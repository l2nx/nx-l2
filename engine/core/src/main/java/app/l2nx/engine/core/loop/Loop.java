/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.loop.inbox.Inbox;
import app.l2nx.engine.core.loop.inbox.InputSource;
import app.l2nx.engine.core.loop.inbox.Wakeup;
import app.l2nx.engine.core.loop.periodic.PeriodicSchedule;
import app.l2nx.engine.core.loop.periodic.PeriodicSystem;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerWheel;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Single-writer event loop of a partition or a global service. {@link #offer} may be called from any thread;
 * everything else runs on the owner thread, the loop thread the loop is pinned to.
 */
@Slf4j
public class Loop {
    static final String RANDOM_ALGORITHM = "L64X128MixRandom";

    private static final ThreadLocal<Loop> CURRENT = new ThreadLocal<>();

    static final int SHUTDOWN_DRAIN_PASSES = 1_000;

    // The timer wheels' resolution: a deadline pass held back this long fires nothing later than its tick.
    private static final long DEADLINE_PASS_SPACING_MILLIS = 1;

    @Getter
    private final String name;

    @Getter
    private final GameClock clock;

    @Getter
    private final long seed;

    private final RandomGenerator random;
    private final EngineProperties settings;
    private final long passMinMillis;
    private final long passBudgetNanos;
    private final Inbox<Message> inbox = new Inbox<>();
    private final EnumMap<PassPhase, List<PassHook>> hooks = new EnumMap<>(PassPhase.class);
    private final long[] phaseNanos = new long[PassPhase.values().length];
    private final LoopMetrics metrics;
    private final PassRecorder recorder;
    private final TimerWheel gameTimers;
    private final TimerWheel wallTimers;
    private final PeriodicSchedule systems = new PeriodicSchedule();
    private final ArrayDeque<InputSource> ready = new ArrayDeque<>();
    private ArrayDeque<Runnable> deferred = new ArrayDeque<>();
    private ArrayDeque<Runnable> spare = new ArrayDeque<>();
    private final List<ShutdownHook> shutdownHooks = new ArrayList<>();
    private boolean inputStopped;
    // Volatile only for the ownership assertion, which runs on whichever thread calls in.
    private volatile Thread owner;

    @Getter
    private long pass;

    /** Game time of the current or last pass; before the first pass, the loop's start, a recorded reading too. */
    @Getter
    private long passTime;

    // Far enough in the past that pass-min never holds back the first pass.
    private long lastPassStart = Long.MIN_VALUE / 2;
    private int passMessages;
    private int passTimers;
    private int passInput;
    // Position of the next recorded input in the pass: messages first, then source items in the order they ran.
    private int passOrder;

    public Loop(
            String name,
            GameClock clock,
            long seed,
            EngineProperties settings,
            MeterRegistry registry,
            PassRecorder recorder) {
        this.name = Objects.requireNonNull(name, "name");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.seed = seed;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.random = RandomGeneratorFactory.of(RANDOM_ALGORITHM).create(seed);
        this.passMinMillis = settings.passMin().toMillis();
        this.passBudgetNanos = settings.passBudget().toNanos();
        this.metrics = new LoopMetrics(registry, name, inbox);
        final long gameStart = clock.gameMillis();
        final long wallStart = clock.wallMillis();
        this.passTime = gameStart;
        this.gameTimers = new TimerWheel(gameStart, this::onFailure);
        this.wallTimers = new TimerWheel(wallStart, this::onFailure);
        recorder.loopStarted(name, seed, gameStart, wallStart);
        log.atInfo()
                .addKeyValue("loop", name)
                .addKeyValue("seed", seed)
                .log("Loop {} started with seed {}", name, seed);
    }

    /** The loop whose pass runs on the calling thread, or {@code null}. */
    public static Loop current() {
        return CURRENT.get();
    }

    /** Any thread. {@code false} once the loop has stopped. */
    public boolean offer(Message message) {
        return inbox.offer(message);
    }

    /** Any thread: the source has input. {@code false} once the loop has stopped. */
    public boolean signal(InputSource source) {
        return inbox.offer(new ReadySignal(Objects.requireNonNull(source, "source")));
    }

    public RandomGenerator random() {
        assertOwner();
        return random;
    }

    public TimerWheel timers(TimeBase base) {
        assertOwner();
        return base == TimeBase.GAME ? gameTimers : wallTimers;
    }

    /**
     * The first run is one cadence after the pass time: a live clock reading would differ in a replay, which sets the
     * clock to the recorded pass time.
     */
    public void addSystem(PeriodicSystem system) {
        assertOwner();
        systems.add(system, passTime, metrics);
    }

    /** Work of the same pass, run in the side-effect phase. */
    public void defer(Runnable work) {
        assertOwner();
        deferred.add(Objects.requireNonNull(work, "work"));
    }

    public void addShutdownHook(ShutdownHook hook) {
        assertOwner();
        shutdownHooks.add(Objects.requireNonNull(hook, "hook"));
    }

    public void addHook(PassPhase phase, PassHook hook) {
        assertOwner();
        if (phase != PassPhase.SIDE_EFFECTS && phase != PassPhase.REPLICATION && phase != PassPhase.FLUSH) {
            throw new IllegalArgumentException(
                    "Hooks run only in the side-effect, replication and flush phases: " + phase);
        }
        hooks.computeIfAbsent(phase, key -> new ArrayList<>()).add(Objects.requireNonNull(hook, "hook"));
    }

    public final void runPass(long now) {
        assertOwner();
        if (now < passTime) {
            throw new IllegalArgumentException(
                    "Pass time of loop " + name + " went back from " + passTime + " to " + now);
        }
        runCurrent(now, true);
    }

    protected final void assertOwner() {
        assert owner == null || owner == Thread.currentThread()
                : "Loop " + name + " is owned by " + owner.getName() + ", called from "
                        + Thread.currentThread().getName();
    }

    /** Game time of the earliest pending work; {@link Long#MIN_VALUE} when work is pending now. */
    long nextWorkAt() {
        return hasQueuedWork() ? Long.MIN_VALUE : nextDeadlineAt();
    }

    long passGate() {
        return lastPassStart + passMinMillis;
    }

    /**
     * Queued work waits for the pass-min gate; a deadline runs when due and takes it along, but no sooner than one wheel
     * tick after the last pass started, or a re-armed timer or overdue backlog would run passes back to back within a
     * millisecond. A time at or before {@code now} only means a pass is due.
     */
    long nextPassAt(long now) {
        assertOwner();
        final boolean queued = hasQueuedWork();
        final long gate = passGate();
        if (queued && gate <= now) {
            return gate;
        }
        final long deadline = Math.max(nextDeadlineAt(), lastPassStart + DEADLINE_PASS_SPACING_MILLIS);
        return queued ? Math.min(deadline, gate) : deadline;
    }

    /** An {@link Error} in a stage skips none of the later ones: the hooks save players. */
    final void shutdown() {
        assertOwner();
        inputStopped = true;
        Throwable failure = runStage(null, () -> drain("before the shutdown hooks"));
        for (ShutdownHook hook : shutdownHooks) {
            failure = runStage(failure, () -> runShutdownHook(hook));
        }
        failure = runStage(failure, () -> drain("after the shutdown hooks"));
        inbox.terminate();
        failure = runStage(failure, () -> drain("of offers accepted while the inbox terminated"));
        InputSource source;
        while ((source = ready.poll()) != null) {
            clearSignal(source);
        }
        switch (failure) {
            case null -> {}
            case Error error -> throw error;
            case RuntimeException exception -> throw exception;
            default -> throw new IllegalStateException("Shutdown of loop " + name + " failed", failure);
        }
    }

    final void close() {
        metrics.close();
    }

    final void bind(Thread thread, Wakeup wakeup) {
        if (owner != null) {
            throw new IllegalStateException("Loop " + name + " is already pinned to " + owner.getName());
        }
        owner = Objects.requireNonNull(thread, "thread");
        inbox.wakeup(wakeup);
    }

    /**
     * A stop is the stopping flag, never an interrupt: one left set ends every park at once, and blocking IO in a
     * shutdown hook fails on it, NIO closing its channel.
     */
    static void clearInterrupt(String when) {
        if (Thread.interrupted()) {
            log.warn(
                    "Loop thread {} was interrupted {}; the interrupt is cleared",
                    Thread.currentThread().getName(),
                    when);
        }
    }

    static String breakdown(long[] phaseNanos) {
        final StringJoiner joiner = new StringJoiner(", ");
        for (PassPhase phase : PassPhase.values()) {
            joiner.add(phase.tag() + " " + LoopMetrics.millis(phaseNanos[phase.ordinal()]) + " ms");
        }
        return joiner.toString();
    }

    private boolean hasQueuedWork() {
        return !inbox.isEmpty() || !ready.isEmpty() || !deferred.isEmpty();
    }

    private long nextDeadlineAt() {
        final long at = Math.min(gameTimers.nextDeadline(), systems.nextDue());
        final long wallDeadline = wallTimers.nextDeadline();
        return wallDeadline == Long.MAX_VALUE ? at : Math.min(at, clock.gameMillisAtWall(wallDeadline));
    }

    private void runCurrent(long now, boolean simulate) {
        final Loop previous = bindCurrent();
        try {
            runPhases(now, simulate);
        } finally {
            restoreCurrent(previous);
        }
    }

    private Loop bindCurrent() {
        final Loop previous = CURRENT.get();
        CURRENT.set(this);
        return previous;
    }

    private static void restoreCurrent(Loop previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    private void runPhases(long now, boolean simulate) {
        final long wallNow = clock.wallMillis();
        pass++;
        passTime = now;
        lastPassStart = now;
        passMessages = 0;
        passTimers = 0;
        passInput = 0;
        Arrays.fill(phaseNanos, 0);
        recorder.passStarted(pass, now, wallNow, !simulate);
        final long start = metrics.monotonicTime();
        long mark = start;
        runInbox();
        mark = endPhase(PassPhase.INBOX, mark);
        if (simulate) {
            passTimers = runDeadlines(now, wallNow);
            mark = endPhase(PassPhase.DEADLINES, mark);
            systems.runDue(this, now, metrics, this::onFailure);
            mark = endPhase(PassPhase.SYSTEMS, mark);
        }
        runSideEffects(now);
        mark = endPhase(PassPhase.SIDE_EFFECTS, mark);
        runHooks(PassPhase.REPLICATION, now);
        mark = endPhase(PassPhase.REPLICATION, mark);
        runHooks(PassPhase.FLUSH, now);
        endPhase(PassPhase.FLUSH, mark);
        completePass(metrics.monotonicTime() - start);
    }

    private static Throwable runStage(Throwable failure, Runnable stage) {
        try {
            stage.run();
        } catch (Throwable e) {
            if (failure == null) {
                return e;
            }
            failure.addSuppressed(e);
        }
        return failure;
    }

    private void runShutdownHook(ShutdownHook hook) {
        clearInterrupt("before a shutdown hook");
        final Loop previous = bindCurrent();
        try {
            hook.onShutdown(this);
        } catch (RuntimeException e) {
            log.error("Shutdown hook {} of loop {} failed", hook, name, e);
        } finally {
            restoreCurrent(previous);
        }
    }

    private void drain(String stage) {
        int passes = 0;
        while (!inbox.isEmpty() || !deferred.isEmpty()) {
            if (passes++ == SHUTDOWN_DRAIN_PASSES) {
                log.warn(
                        "Loop {} stopped the drain {} after {} passes: {} messages and {} deferred items left",
                        name,
                        stage,
                        SHUTDOWN_DRAIN_PASSES,
                        inbox.size(),
                        deferred.size());
                return;
            }
            clearInterrupt("before a shutdown drain pass");
            runCurrent(Math.max(passTime, clock.gameMillis()), false);
        }
    }

    private int runDeadlines(long now, long wallNow) {
        final int cap = settings.overduePerPass();
        final int owed = settings.owedTicksPerPass();
        final int fired = gameTimers.runDue(this, now, cap, owed, now - passMinMillis)
                + wallTimers.runDue(this, wallNow, cap, owed, wallNow - passMinMillis);
        metrics.overdue(gameTimers.getLastOverdue() + wallTimers.getLastOverdue());
        return fired;
    }

    private void runInbox() {
        // Only what is queued now, so producers cannot keep the pass in this phase.
        final int limit = inbox.size();
        passOrder = 0;
        for (int i = 0; i < limit; i++) {
            final Message message = inbox.poll();
            if (message == null) {
                break;
            }
            if (message instanceof ReadySignal(InputSource source)) {
                ready.add(source);
                continue;
            }
            record(message, () -> recorder.message(pass, passOrder, message));
            passOrder++;
            passMessages++;
            try {
                message.execute(this);
            } catch (RuntimeException e) {
                onFailure(PassPhase.INBOX, message, e);
            }
        }
        // Round-robin with packets-per-pass per source: a flooding client delays only itself, its order is kept.
        final int sources = ready.size();
        for (int i = 0; i < sources; i++) {
            final InputSource source = ready.poll();
            try {
                if (runSource(source)) {
                    ready.add(source);
                }
            } catch (RuntimeException e) {
                // A broken source leaves the ready ring until its producer signals again; the pass goes on.
                onFailure(PassPhase.INBOX, source, e);
                clearSignal(source);
            }
        }
    }

    /** One turn of a ready source; {@code true}: it stays in the ready ring. */
    private boolean runSource(InputSource source) {
        if (inputStopped) {
            source.clearSignal();
            return false;
        }
        runInput(source);
        if (source.hasInput()) {
            return true;
        }
        source.clearSignal();
        // Pairs with the producer's fence before trySignal: either this check sees the item or trySignal sees the
        // cleared flag.
        VarHandle.fullFence();
        return source.hasInput() && source.trySignal();
    }

    private void clearSignal(InputSource source) {
        try {
            source.clearSignal();
        } catch (RuntimeException e) {
            onFailure(PassPhase.INBOX, source, e);
        }
    }

    /**
     * Each item is recorded and leaves the source before it runs, so a failing one is never retried. A failure of the
     * source itself propagates.
     */
    private void runInput(InputSource source) {
        final long sourceId = source.id();
        final int max = settings.packetsPerPass();
        for (int ran = 0; ran < max; ran++) {
            final Message item = source.poll();
            if (item == null) {
                break;
            }
            record(item, () -> recorder.inputItem(pass, passOrder, sourceId, item));
            passOrder++;
            passInput++;
            try {
                item.execute(this);
            } catch (RuntimeException e) {
                onFailure(PassPhase.INBOX, item, e);
            }
        }
    }

    // Recording never blocks or drops game work: a failure is reported and the work still runs.
    private void record(Message input, Runnable recording) {
        try {
            recording.run();
        } catch (RuntimeException e) {
            onFailure(PassPhase.INBOX, input, e);
        }
    }

    private void runSideEffects(long now) {
        final int limit = settings.triggerDepth();
        int depth = 0;
        while (true) {
            while (!deferred.isEmpty() && depth < limit) {
                // One generation: work it defers goes to the next generation.
                final ArrayDeque<Runnable> generation = deferred;
                deferred = spare;
                spare = generation;
                Runnable work;
                while ((work = generation.poll()) != null) {
                    try {
                        work.run();
                    } catch (RuntimeException e) {
                        onFailure(PassPhase.SIDE_EFFECTS, work, e);
                    }
                }
                depth++;
            }
            // Again after work the hooks deferred, so they see what it changed; never twice without work between.
            runHooks(PassPhase.SIDE_EFFECTS, now);
            if (deferred.isEmpty()) {
                return;
            }
            if (depth == limit) {
                metrics.triggerDepthExceeded();
                log.warn(
                        "Loop {} pass {} reached trigger depth {}; {} queued items continue in the next pass",
                        name,
                        pass,
                        limit,
                        deferred.size());
                return;
            }
        }
    }

    private void runHooks(PassPhase phase, long now) {
        final List<PassHook> phaseHooks = hooks.get(phase);
        if (phaseHooks == null) {
            return;
        }
        for (int i = 0; i < phaseHooks.size(); i++) {
            final PassHook hook = phaseHooks.get(i);
            try {
                hook.run(this, now);
            } catch (RuntimeException e) {
                onFailure(phase, hook, e);
            }
        }
    }

    private long endPhase(PassPhase phase, long mark) {
        final long now = metrics.monotonicTime();
        final long nanos = now - mark;
        phaseNanos[phase.ordinal()] = nanos;
        metrics.phase(phase, nanos);
        return now;
    }

    private void completePass(long durationNanos) {
        metrics.pass(durationNanos, passMessages, passInput, passTimers, gameTimers.size() + wallTimers.size());
        if (durationNanos > passBudgetNanos) {
            metrics.overBudget();
            log.warn(
                    "Loop {} pass {} took {} ms, over the {} ms budget: {}",
                    name,
                    pass,
                    LoopMetrics.millis(durationNanos),
                    settings.passBudget().toMillis(),
                    breakdown(phaseNanos));
        }
    }

    private void onFailure(PassPhase phase, Object source, RuntimeException failure) {
        metrics.failure(phase);
        log.error("{} failed in the {} phase of loop {} pass {}", source, phase.tag(), name, pass, failure);
    }

    private record ReadySignal(InputSource source) implements Message {
        @Override
        public void execute(Loop loop) {
            throw new IllegalStateException("A ready signal is consumed by the inbox phase, never executed");
        }
    }
}
