/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot;

import app.l2nx.load.bot.intent.EnterWorld;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.Login;
import app.l2nx.load.bot.intent.Logout;
import app.l2nx.load.bot.intent.SelectCharacter;
import app.l2nx.load.bot.intent.SelectServer;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.Entered;
import app.l2nx.load.bot.observation.ObjectGone;
import app.l2nx.load.bot.observation.ObjectMoved;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.bot.observation.Observation;
import app.l2nx.load.bot.observation.StatusChanged;
import app.l2nx.load.injection.RunTimeline;
import app.l2nx.load.latency.GeneratorLagMonitor;
import app.l2nx.load.latency.LatencyRecorder;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import app.l2nx.load.scenario.model.ActionSchedule;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * One simulated player. Everything but {@link #start()} and {@link #close()} runs on its session's event loop, so it
 * holds no locks. Latency counts from an intent's intended time and the bot enters {@code LOGIN_CONNECT} at its
 * intended start (coordinated omission). Acknowledgements carry no correlation: an intent the server drops silently
 * absorbs the next acknowledgement and overstates latency.
 */
@Slf4j
public final class LoadBot implements LoadBotView {
    private static final Map<LoadBotState, IntentKind> LIFECYCLE_STEP_INTENTS =
            Collections.unmodifiableMap(new EnumMap<>(Map.of(
                    LoadBotState.LOGIN_AUTH, IntentKind.LOGIN,
                    LoadBotState.SERVER_LIST, IntentKind.SELECT_SERVER,
                    LoadBotState.CHARACTER_SELECT, IntentKind.SELECT_CHARACTER,
                    LoadBotState.ENTERING, IntentKind.ENTER_WORLD)));

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final LoadBotIdentity identity;
    private final EventLoop loop;
    private final ClientProtocol protocol;
    private final Set<IntentKind> capabilities;
    private final LoadScenario scenario;
    private final LoadBotState finalState;
    private final RunTimeline timeline;
    private final LatencyRecorder latencies;
    private final GeneratorLagMonitor lag;
    private final long intentTimeoutNanos;
    private final Runnable onStopped;
    private final SplittableRandom random;
    private final Map<IntentKind, ArrayDeque<Pending>> pending = new EnumMap<>(IntentKind.class);
    private final Int2ObjectOpenHashMap<ObjectSeen> visible = new Int2ObjectOpenHashMap<>();
    private final Collection<ObjectSeen> visibleView = Collections.unmodifiableCollection(visible.values());

    private LoadBotState state = LoadBotState.LOGIN_CONNECT;
    private ClientSession session;
    private LoadBehaviour behaviour;
    private ActionSchedule schedule;
    private long nextIntended;
    private ScheduledFuture<?> nextAction;
    private ScheduledFuture<?> leaveTask;
    private ScheduledFuture<?> expiry;
    private long expiryDeadline;
    private long enteredAt;
    private boolean leaving;
    private boolean loggedOut;
    private boolean finished;
    private boolean stoppedOnError;
    private boolean reachedFinalState;
    private boolean arrivalOver;
    private int selfObjectId;
    private Position position;
    private int hp;
    private int mp;

    /** @param onStopped called once, on the event loop, when the load bot reached {@code STOPPED} */
    public LoadBot(
            LoadBotIdentity identity,
            long seed,
            EventLoop loop,
            ClientProtocol protocol,
            LoadScenario scenario,
            RunTimeline timeline,
            LatencyRecorder latencies,
            GeneratorLagMonitor lag,
            Duration intentTimeout,
            Runnable onStopped) {
        this.identity = identity;
        this.loop = loop;
        this.protocol = protocol;
        capabilities = Set.copyOf(protocol.capabilities());
        this.scenario = scenario;
        finalState = requireFinalState(scenario.finalState());
        this.timeline = timeline;
        this.latencies = latencies;
        this.lag = lag;
        intentTimeoutNanos = intentTimeout.toNanos();
        this.onStopped = onStopped;
        random = new SplittableRandom(seedOf(seed, identity.index()));
        enteredAt = timeline.startAt(identity.index());
    }

    /** Intent kinds a load bot sends itself on its way to {@code finalState}; a protocol must express them. */
    public static Set<IntentKind> lifecycleIntents(LoadBotState finalState) {
        requireFinalState(finalState);
        final Set<IntentKind> kinds = EnumSet.noneOf(IntentKind.class);
        for (LoadBotState state : LoadBotState.values()) {
            if (state.compareTo(finalState) >= 0) {
                break;
            }
            final IntentKind kind = LIFECYCLE_STEP_INTENTS.get(state);
            if (kind != null) {
                kinds.add(kind);
            }
        }
        if (finalState == LoadBotState.IN_GAME) {
            kinds.add(IntentKind.LOGOUT);
        }
        return Collections.unmodifiableSet(kinds);
    }

    private static LoadBotState requireFinalState(LoadBotState finalState) {
        if (finalState == null || !finalState.canBeFinal()) {
            throw new IllegalArgumentException("A load scenario's final state must lie after "
                    + LoadBotState.LOGIN_CONNECT + " and up to " + LoadBotState.IN_GAME + ": " + finalState);
        }
        return finalState;
    }

    /** SplitMix64 mix of the run seed and the load bot index. */
    static long seedOf(long seed, int index) {
        long z = seed ^ index * GOLDEN_GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Call once; safe from any thread. */
    public void start() {
        loop.execute(() -> {
            schedule(timeline.startAt(identity.index()), this::connect);
            leaveTask = schedule(timeline.leaveAt(identity.index()), this::leave);
        });
    }

    /** Stops the load bot without a logout, from any thread; pending intents of the measured phase count as timeouts. */
    public void close() {
        loop.execute(() -> finish(false));
    }

    /** Whether the load bot stopped on a protocol error or dropped connection; read once its event loop is done. */
    public boolean stoppedOnError() {
        return stoppedOnError;
    }

    /** Whether the load bot ever reached its final state; read once its event loop is done. */
    public boolean reachedFinalState() {
        return reachedFinalState;
    }

    @Override
    public LoadBotIdentity identity() {
        return identity;
    }

    @Override
    public LoadBotState state() {
        return state;
    }

    @Override
    public int selfObjectId() {
        return selfObjectId;
    }

    @Override
    public Position position() {
        return position;
    }

    @Override
    public int hp() {
        return hp;
    }

    @Override
    public int mp() {
        return mp;
    }

    @Override
    public Collection<ObjectSeen> visibleObjects() {
        return visibleView;
    }

    private ScheduledFuture<?> schedule(long intended, Runnable task) {
        return loop.schedule(
                () -> {
                    recordLag(intended);
                    task.run();
                },
                Math.max(0, intended - timeline.now()),
                TimeUnit.NANOSECONDS);
    }

    private void recordLag(long intended) {
        if (timeline.countsLag(intended)) {
            lag.record(timeline.now() - intended);
        }
    }

    private void connect() {
        if (finished) {
            return;
        }
        try {
            session = protocol.open(identity, loop, new Listener());
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void leave() {
        leaving = true;
        cancelActions();
        departWhenDrained();
    }

    private void stateChanged(LoadBotState next) {
        // A repeated report of the current state must not resend a lifecycle intent or start a second action chain.
        if (finished || next == state) {
            return;
        }
        left(state, next);
        state = next;
        if (next != LoadBotState.IN_GAME) {
            cancelActions();
        }
        if (next == LoadBotState.STOPPED) {
            finish(!loggedOut);
            return;
        }
        if (next.inLifecycle() && arrived(next)) {
            return;
        }
        if (departing()) {
            departWhenDrained();
            return;
        }
        switch (next) {
            case LOGIN_AUTH ->
                sendArrival(Login.builder()
                        .account(identity.account())
                        .password(identity.password())
                        .build());
            case SERVER_LIST -> sendArrival(new SelectServer());
            case CHARACTER_SELECT -> sendArrival(new SelectCharacter(identity.characterSlot()));
            case ENTERING -> sendArrival(new EnterWorld());
            case IN_GAME -> enterGame();
            default -> {}
        }
    }

    private void left(LoadBotState left, LoadBotState next) {
        final long now = timeline.now();
        if (left.inLifecycle()
                && next.inLifecycle()
                && left != finalState
                && timeline.countsArrival(identity.index(), enteredAt)) {
            latencies.recordStep(left, now - enteredAt);
        }
        enteredAt = now;
    }

    /** A state past the final one counts as reaching it: an adapter may report several steps at once. */
    private boolean arrived(LoadBotState reached) {
        if (reached.compareTo(finalState) < 0) {
            return false;
        }
        reachedFinalState = true;
        endArrival();
        if (finalState == LoadBotState.IN_GAME) {
            return false;
        }
        finish(false);
        return true;
    }

    private void endArrival() {
        if (!arrivalOver) {
            arrivalOver = true;
            timeline.arrivalOver();
        }
    }

    private void enterGame() {
        try {
            if (behaviour == null) {
                behaviour = scenario.behaviour(this);
                schedule = behaviour.schedule().orElse(null);
            }
        } catch (RuntimeException e) {
            fail(e);
            return;
        }
        // Without a schedule the load bot only waits for its leave task.
        if (schedule == null) {
            return;
        }
        // A fresh phase after every entry: the time spent outside the game is not owed as a burst of actions.
        nextIntended = timeline.now() + schedule.firstDelayNanos(random);
        nextAction = schedule(nextIntended, this::act);
    }

    private void act() {
        nextAction = null;
        final long intended = nextIntended;
        final Intent intent;
        try {
            intent = behaviour.next(this, random);
        } catch (RuntimeException e) {
            fail(e);
            return;
        }
        if (intent != null) {
            send(intent, intended, timeline.countsAction(intended));
        }
        if (!finished && !leaving && state == LoadBotState.IN_GAME) {
            nextIntended = intended + schedule.nextDelayNanos(random);
            nextAction = schedule(nextIntended, this::act);
        }
    }

    private void cancelActions() {
        if (nextAction != null) {
            nextAction.cancel(false);
            nextAction = null;
        }
    }

    private void sendArrival(Intent intent) {
        final long now = timeline.now();
        send(intent, now, timeline.countsArrival(identity.index(), now));
    }

    /** @param counted whether the measured phase counts the intent, judged once for its whole life */
    private void send(Intent intent, long intended, boolean counted) {
        final IntentKind kind = intent.kind();
        if (!capabilities.contains(kind)) {
            fail(new IllegalStateException("Protocol " + protocol.id() + " cannot express " + kind));
            return;
        }
        if (session == null) {
            fail(new IllegalStateException("Protocol " + protocol.id() + " reported a state before open returned"));
            return;
        }
        if (counted) {
            latencies.recordSent(kind);
        }
        // Pending before the send: an adapter may acknowledge synchronously inside it.
        pending.computeIfAbsent(kind, k -> new ArrayDeque<>()).addLast(new Pending(intended, counted));
        armExpiry(intended + intentTimeoutNanos);
        session.send(intent);
    }

    private void acknowledged(IntentKind kind) {
        final Pending head = takeOldest(kind);
        if (head == null) {
            return;
        }
        if (!head.expired && head.counted) {
            final long latency = timeline.now() - head.intended;
            if (latency >= intentTimeoutNanos) {
                // The expiry task has not run yet: the intent is late all the same.
                latencies.recordTimeout(kind);
            } else {
                latencies.recordAcknowledged(kind, latency);
            }
        }
        departWhenDrained();
    }

    private void rejected(IntentKind kind) {
        final Pending head = takeOldest(kind);
        if (head == null) {
            return;
        }
        // An expired intent has already counted as a timeout.
        if (!head.expired && head.counted) {
            latencies.recordError(kind);
        }
        departWhenDrained();
    }

    /** The oldest pending intent of {@code kind}, expired or not; {@code null} when none is pending. */
    private Pending takeOldest(IntentKind kind) {
        if (finished) {
            return null;
        }
        final ArrayDeque<Pending> queue = pending.get(kind);
        return queue == null ? null : queue.pollFirst();
    }

    private void armExpiry(long deadline) {
        if (expiry != null) {
            if (deadline - expiryDeadline >= 0) {
                return;
            }
            expiry.cancel(false);
        }
        expiryDeadline = deadline;
        expiry = loop.schedule(this::expire, Math.max(0, deadline - timeline.now()), TimeUnit.NANOSECONDS);
    }

    private void expire() {
        expiry = null;
        if (finished) {
            return;
        }
        final long now = timeline.now();
        final long purgeAge = 2 * intentTimeoutNanos;
        long next = 0;
        boolean any = false;
        for (Map.Entry<IntentKind, ArrayDeque<Pending>> entry : pending.entrySet()) {
            final ArrayDeque<Pending> queue = entry.getValue();
            while (!queue.isEmpty() && queue.peekFirst().expired && now - queue.peekFirst().intended >= purgeAge) {
                queue.pollFirst();
            }
            // Intended times grow along the FIFO, so the expired entries form its head.
            for (Pending waiting : queue) {
                if (waiting.expired) {
                    continue;
                }
                if (now - waiting.intended < intentTimeoutNanos) {
                    final long deadline = waiting.intended + intentTimeoutNanos;
                    if (!any || deadline - next < 0) {
                        next = deadline;
                        any = true;
                    }
                    break;
                }
                waiting.expired = true;
                if (waiting.counted) {
                    latencies.recordTimeout(entry.getKey());
                }
            }
            if (!queue.isEmpty() && queue.peekFirst().expired) {
                final long purgeAt = queue.peekFirst().intended + purgeAge;
                if (!any || purgeAt - next < 0) {
                    next = purgeAt;
                    any = true;
                }
            }
        }
        if (any) {
            armExpiry(next);
        }
        departWhenDrained();
    }

    int pendingCount() {
        int count = 0;
        for (ArrayDeque<Pending> queue : pending.values()) {
            count += queue.size();
        }
        return count;
    }

    /**
     * A bot with a final state before the game keeps walking to it past its leave time: leaving would cut short the
     * lifecycle measured.
     */
    private boolean departing() {
        return leaving && finalState == LoadBotState.IN_GAME;
    }

    private void departWhenDrained() {
        if (!departing() || loggedOut || finished) {
            return;
        }
        for (ArrayDeque<Pending> queue : pending.values()) {
            if (!queue.isEmpty() && !queue.peekLast().expired) {
                return;
            }
        }
        if (state == LoadBotState.IN_GAME) {
            loggedOut = true;
            final long now = timeline.now();
            send(new Logout(), now, timeline.countsAction(now));
        } else {
            finish(false);
        }
    }

    private void observed(Observation observation) {
        switch (observation) {
            case Entered entered -> {
                selfObjectId = entered.selfObjectId();
                position = entered.position();
            }
            case ObjectSeen seen -> visible.put(seen.objectId(), seen);
            case ObjectGone gone -> visible.remove(gone.objectId());
            case ObjectMoved moved -> {
                if (moved.objectId() == selfObjectId) {
                    position = moved.position();
                } else {
                    visible.computeIfPresent(
                            moved.objectId(),
                            (id, seen) ->
                                    seen.toBuilder().position(moved.position()).build());
                }
            }
            case StatusChanged status -> {
                hp = status.hp();
                mp = status.mp();
            }
        }
    }

    private void fail(Throwable cause) {
        if (finished) {
            return;
        }
        log.warn("Load bot {} stopped on an error in state {}", identity.index(), state, cause);
        finish(true);
    }

    private void finish(boolean onError) {
        if (finished) {
            return;
        }
        finished = true;
        state = LoadBotState.STOPPED;
        stoppedOnError = onError;
        endArrival();
        cancelActions();
        if (leaveTask != null) {
            leaveTask.cancel(false);
        }
        if (expiry != null) {
            expiry.cancel(false);
            expiry = null;
        }
        for (Map.Entry<IntentKind, ArrayDeque<Pending>> entry : pending.entrySet()) {
            for (Pending waiting : entry.getValue()) {
                if (!waiting.expired && waiting.counted) {
                    if (onError) {
                        latencies.recordError(entry.getKey());
                    } else {
                        latencies.recordTimeout(entry.getKey());
                    }
                }
            }
            entry.getValue().clear();
        }
        if (session != null) {
            session.close();
        }
        onStopped.run();
    }

    private final class Listener implements SessionListener {
        @Override
        public void stateChanged(LoadBotState state) {
            LoadBot.this.stateChanged(state);
        }

        @Override
        public void observed(Observation observation) {
            if (!finished) {
                LoadBot.this.observed(observation);
            }
        }

        @Override
        public void acknowledged(IntentKind kind) {
            LoadBot.this.acknowledged(kind);
        }

        @Override
        public void rejected(IntentKind kind) {
            LoadBot.this.rejected(kind);
        }

        @Override
        public void failed(Throwable cause) {
            fail(cause);
        }
    }

    /** An intent waiting for its acknowledgement; an expired one only stays to absorb a late acknowledgement. */
    private static final class Pending {
        private final long intended;
        private final boolean counted;
        private boolean expired;

        private Pending(long intended, boolean counted) {
            this.intended = intended;
            this.counted = counted;
        }
    }
}
