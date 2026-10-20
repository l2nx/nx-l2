/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerEntry;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Wake-ups per second of an idle world (no player) with {@code npcs} NPCs, by scheduler. The benchmark thread only
 * samples once a second, so the primary score means nothing; the result is the {@link Rates} counters. Windows
 * accounts thread time in 15.6 ms quanta, so {@code cpuMicros} is coarse there.
 *
 * <p>Every NPC holds a respawn timer re-armed {@value #RESPAWN_MIN_SECONDS} to {@value #RESPAWN_MAX_SECONDS} s after
 * it fires, started in steady state (see {@link RespawnDelay}). {@code MOBIUS_IDLE} models Mobius's always-on think and
 * movement tasks running on empty pools; {@code MOBIUS_ACTIVE} fills them, an upper bound and not a zero-player state;
 * {@code TICK_100MS} is the L2J Server {@code GameTimeController} shape. Other always-on manager tasks of the
 * competitors are left out, which favours them.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 10)
@Fork(1)
@State(Scope.Thread)
public class IdleWakeupBenchmark {
    static final long RESPAWN_MIN_SECONDS = 30;
    static final long RESPAWN_MAX_SECONDS = 90;
    private static final long SAMPLE_MILLIS = 1_000;
    private static final RespawnDelay RESPAWN = RespawnDelay.builder()
            .minMillis(TimeUnit.SECONDS.toMillis(RESPAWN_MIN_SECONDS))
            .maxMillis(TimeUnit.SECONDS.toMillis(RESPAWN_MAX_SECONDS))
            .build();

    public enum Scheduler {
        ZERO_TICK,
        MOBIUS_IDLE,
        MOBIUS_ACTIVE,
        TICK_100MS
    }

    @Param({"1000", "10000"})
    public int npcs;

    @Param
    public Scheduler scheduler;

    private World world;
    private long lastWakeups;
    private long lastVisits;
    private long lastCpuNanos;

    @Setup(Level.Trial)
    public void start() {
        world = switch (scheduler) {
            case ZERO_TICK -> new ZeroTickWorld(npcs);
            case MOBIUS_IDLE -> new MobiusWorld(npcs, false);
            case MOBIUS_ACTIVE -> new MobiusWorld(npcs, true);
            case TICK_100MS -> new TickWorld(npcs);
        };
    }

    // The scheduler runs between iterations too; counting starts afresh with each one.
    @Setup(Level.Iteration)
    public void mark() {
        lastWakeups = world.wakeups();
        lastVisits = world.visits();
        lastCpuNanos = world.cpuNanos();
    }

    @TearDown(Level.Trial)
    public void stop() throws InterruptedException {
        world.close();
    }

    @Benchmark
    public void sample(Rates rates) throws InterruptedException {
        Thread.sleep(SAMPLE_MILLIS);
        final long wakeups = world.wakeups();
        final long visits = world.visits();
        final long cpuNanos = world.cpuNanos();
        rates.wakeups += wakeups - lastWakeups;
        rates.npcVisits += visits - lastVisits;
        rates.cpuMicros += (cpuNanos - lastCpuNanos) / 1_000;
        lastWakeups = wakeups;
        lastVisits = visits;
        lastCpuNanos = cpuNanos;
    }

    @AuxCounters(AuxCounters.Type.OPERATIONS)
    @State(Scope.Thread)
    public static class Rates {
        public long wakeups;
        public long npcVisits;
        public long cpuMicros;
    }

    private interface World {
        long wakeups();

        long visits();

        long cpuNanos();

        void close() throws InterruptedException;
    }

    private static final class ZeroTickWorld implements World {
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final LongAdder fired = new LongAdder();
        // Used by the loop thread only, once the setup has handed the loop over.
        private final SplittableRandom random = new SplittableRandom(1);
        private final LoopThread thread;
        private final TimerSpec respawn;

        ZeroTickWorld(int npcs) {
            final SystemGameClock clock = new SystemGameClock(Clock.systemUTC());
            final Loop loop = new Loop("idle", clock, 1L, EngineProperties.defaults(), registry, PassRecorder.NOOP);
            respawn = TimerSpec.builder()
                    .handler(this::respawned)
                    .kind(TimerKind.ACTION)
                    .build();
            final long now = clock.gameMillis();
            for (int i = 0; i < npcs; i++) {
                loop.timers(TimeBase.GAME).schedule(now + RESPAWN.first(random), respawn);
            }
            thread = new LoopThread("idle", registry, (failed, failure) -> {});
            thread.add(loop);
            thread.start();
        }

        private void respawned(Loop owner, TimerEntry entry) {
            fired.increment();
            owner.timers(TimeBase.GAME).schedule(entry.getDeadline() + RESPAWN.next(random), respawn);
        }

        @Override
        public long wakeups() {
            if (!thread.getThread().isAlive()) {
                throw new IllegalStateException("Loop thread died; see its log");
            }
            return registry.get("l2nx.engine.pass").timer().count();
        }

        @Override
        public long visits() {
            return fired.sum();
        }

        @Override
        public long cpuNanos() {
            return Math.max(
                    0,
                    ManagementFactory.getThreadMXBean()
                            .getThreadCpuTime(thread.getThread().threadId()));
        }

        @Override
        public void close() throws InterruptedException {
            thread.requestStop();
            thread.join();
            thread.close();
        }
    }

    private static final class MobiusWorld implements World {
        private static final int POOL_SIZE = 1_000;
        private static final long THINK_MILLIS = 1_000;
        private static final long MOVE_MILLIS = 100;

        private final ClassicThreadPools pools = new ClassicThreadPools();
        private final ScheduledThreadPoolExecutor highPriority = pools.highPriority();
        private final ScheduledThreadPoolExecutor scheduled = pools.scheduled();
        private final LongAdder runs = new LongAdder();
        private final LongAdder visits = new LongAdder();

        MobiusWorld(int npcs, boolean active) {
            final List<Npc> all = new ArrayList<>(npcs);
            final SplittableRandom random = new SplittableRandom(1);
            for (int i = 0; i < npcs; i++) {
                final Npc npc = new Npc();
                all.add(npc);
                scheduled.schedule(() -> respawn(npc), RESPAWN.first(random), TimeUnit.MILLISECONDS);
            }
            for (int from = 0; from < npcs; from += POOL_SIZE) {
                final Set<Npc> thinking = ConcurrentHashMap.newKeySet(POOL_SIZE);
                final Set<Npc> moving = ConcurrentHashMap.newKeySet(POOL_SIZE);
                if (active) {
                    final List<Npc> members = all.subList(from, Math.min(npcs, from + POOL_SIZE));
                    thinking.addAll(members);
                    moving.addAll(members);
                }
                highPriority.scheduleAtFixedRate(
                        () -> think(thinking), THINK_MILLIS, THINK_MILLIS, TimeUnit.MILLISECONDS);
                scheduled.scheduleAtFixedRate(() -> move(moving), MOVE_MILLIS, MOVE_MILLIS, TimeUnit.MILLISECONDS);
            }
        }

        private void respawn(Npc npc) {
            runs.increment();
            npc.respawn();
            visits.increment();
            scheduled.schedule(() -> respawn(npc), RESPAWN.next(ThreadLocalRandom.current()), TimeUnit.MILLISECONDS);
        }

        private void think(Set<Npc> pool) {
            runs.increment();
            if (pool.isEmpty()) {
                return;
            }
            int visited = 0;
            for (Npc npc : pool) {
                npc.think();
                visited++;
            }
            visits.add(visited);
        }

        private void move(Set<Npc> pool) {
            runs.increment();
            if (pool.isEmpty()) {
                return;
            }
            int visited = 0;
            for (Npc npc : pool) {
                npc.updatePosition();
                visited++;
            }
            visits.add(visited);
        }

        @Override
        public long wakeups() {
            return runs.sum();
        }

        @Override
        public long visits() {
            return visits.sum();
        }

        @Override
        public long cpuNanos() {
            return pools.cpuNanos();
        }

        @Override
        public void close() throws InterruptedException {
            ClassicThreadPools.shutdown(highPriority);
            ClassicThreadPools.shutdown(scheduled);
        }
    }

    private static final class TickWorld implements World {
        private static final long TICK_MILLIS = 100;

        private final Set<Npc> moving = ConcurrentHashMap.newKeySet();
        // Deadlines in System.currentTimeMillis; the tick thread owns them once it runs.
        private final LongHeapPriorityQueue respawns = new LongHeapPriorityQueue();
        private final SplittableRandom random = new SplittableRandom(1);
        private final LongAdder ticks = new LongAdder();
        private final LongAdder visits = new LongAdder();
        private final Thread thread;
        private volatile boolean running = true;

        TickWorld(int npcs) {
            final long now = System.currentTimeMillis();
            for (int i = 0; i < npcs; i++) {
                respawns.enqueue(now + RESPAWN.first(random));
            }
            thread = Thread.ofPlatform().name("tick").daemon(true).start(this::run);
        }

        // The GameTimeController shape: the tick's end is fixed before its work, the sleep takes what is left.
        private void run() {
            while (running) {
                final long now = System.currentTimeMillis();
                final long next = now + TICK_MILLIS;
                ticks.increment();
                int visited = 0;
                for (Npc npc : moving) {
                    npc.updatePosition();
                    visited++;
                }
                while (!respawns.isEmpty() && respawns.firstLong() <= now) {
                    respawns.enqueue(respawns.dequeueLong() + RESPAWN.next(random));
                    visited++;
                }
                visits.add(visited);
                final long sleep = next - System.currentTimeMillis();
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }

        @Override
        public long wakeups() {
            return ticks.sum();
        }

        @Override
        public long visits() {
            return visits.sum();
        }

        @Override
        public long cpuNanos() {
            return Math.max(0, ManagementFactory.getThreadMXBean().getThreadCpuTime(thread.threadId()));
        }

        @Override
        public void close() throws InterruptedException {
            running = false;
            thread.interrupt();
            thread.join();
        }
    }

    // Stands for a Mobius creature; a visit is one call on it, the work behind the call is not modelled.
    private static final class Npc {
        private long calls;

        void think() {
            calls++;
        }

        void respawn() {
            calls++;
        }

        boolean updatePosition() {
            calls++;
            return false;
        }
    }
}
