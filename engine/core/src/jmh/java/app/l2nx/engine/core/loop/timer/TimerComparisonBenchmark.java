/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.ClassicThreadPools;
import app.l2nx.engine.core.loop.RespawnDelay;
import java.util.SplittableRandom;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Cost of one timer, scheduled and fired, while {@code live} other timers are pending: the {@link TimerWheel} against
 * the {@link ScheduledThreadPoolExecutor} L2J Mobius runs game timers on (see {@link ClassicThreadPools}), with
 * {@code threads} workers: 1, 4, or the Mobius default of four per CPU.
 *
 * <p>Background timers, the same on both sides: {@code live} respawn-like timers re-armed 1 s to 10 min after each
 * firing, started in steady state, so {@code live / 300 s} fall due per second from the first moment on.
 *
 * <p>The wheel runs on virtual time, advancing 1 ms per operation, far more than the real duration of an executor
 * operation, so it fires many more background timers per measured timer. That tilts the single-operation figures
 * against the wheel; a batch covers a similar span of world time on both sides.
 *
 * <p>The executor caller spins until a worker has run the task, not {@code Future.get}, so its own park and wake-up stay
 * out of the number. {@code *Batch} figures are per timer; the executor's is throughput, and its workers share one
 * lock-guarded queue, so more workers can cost more, not less.
 *
 * <p>Both sides take the cheapest path: the wheel's timer lands in the current turn, the executor's zero delay sits at
 * the head of its heap, and the executor runs a shared task with no Mobius {@code RunnableWrapper}, which spares one
 * allocation per schedule on its side.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TimerComparisonBenchmark {
    static final int BATCH = 1_000;
    static final int PASS_MILLIS = 5;
    private static final RespawnDelay BACKGROUND = RespawnDelay.builder()
            .minMillis(TimeUnit.SECONDS.toMillis(1))
            .maxMillis(TimeUnit.MINUTES.toMillis(10))
            .build();

    @Benchmark
    public int wheelScheduleAndFire(Wheel state) {
        state.now++;
        state.wheel.schedule(state.now, Wheel.SPEC);
        return state.wheel.runDue(null, state.now, Integer.MAX_VALUE, 10, Long.MIN_VALUE);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int wheelBatch(Wheel state) {
        final long from = state.now;
        for (int i = 0; i < BATCH; i++) {
            state.wheel.schedule(from + 1 + i % PASS_MILLIS, Wheel.SPEC);
        }
        state.now = from + PASS_MILLIS;
        return state.wheel.runDue(null, state.now, Integer.MAX_VALUE, 10, Long.MIN_VALUE);
    }

    @Benchmark
    public long executorScheduleAndAwait(Executor state) {
        final long target = ++state.scheduled;
        state.pool.schedule(state.task, 0, TimeUnit.NANOSECONDS);
        return state.awaitRan(target);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public long executorBatch(Executor state) {
        for (int i = 0; i < BATCH; i++) {
            state.pool.schedule(state.task, 0, TimeUnit.NANOSECONDS);
        }
        state.scheduled += BATCH;
        return state.awaitRan(state.scheduled);
    }

    @State(Scope.Thread)
    public static class Wheel {
        static final TimerSpec SPEC = TimerSpec.builder()
                .handler((loop, entry) -> {})
                .kind(TimerKind.ACTION)
                .build();

        @Param({"1000", "100000", "1000000"})
        public int live;

        TimerWheel wheel;
        long now;
        private final SplittableRandom random = new SplittableRandom(1);
        private final TimerSpec background = TimerSpec.builder()
                .handler(
                        (loop, entry) -> wheel.schedule(entry.getDeadline() + BACKGROUND.next(random), this.background))
                .kind(TimerKind.ACTION)
                .build();

        // Per trial: re-arming keeps the population steady, so iterations need no fresh wheel.
        @Setup(Level.Trial)
        public void setUp() {
            wheel = new TimerWheel(0, (phase, source, failure) -> {
                throw failure;
            });
            now = 0;
            for (int i = 0; i < live; i++) {
                wheel.schedule(BACKGROUND.first(random), background);
            }
        }
    }

    @State(Scope.Thread)
    public static class Executor {
        @Param({"1000", "100000", "1000000"})
        public int live;

        /** Worker threads; {@code mobius} is the Mobius default, four per CPU. */
        @Param({"1", "4", "mobius"})
        public String threads;

        private final LongAdder ran = new LongAdder();
        final Runnable task = ran::increment;
        ScheduledThreadPoolExecutor pool;
        long scheduled;

        @Setup(Level.Trial)
        public void setUp() {
            pool = new ClassicThreadPools()
                    .scheduled(
                            "mobius".equals(threads)
                                    ? ClassicThreadPools.SCHEDULED_THREADS
                                    : Integer.parseInt(threads));
            final SplittableRandom random = new SplittableRandom(1);
            for (int i = 0; i < live; i++) {
                pool.schedule(new Background(pool), BACKGROUND.first(random), TimeUnit.MILLISECONDS);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws InterruptedException {
            ClassicThreadPools.shutdown(pool);
        }

        long awaitRan(long target) {
            while (ran.sum() < target) {
                Thread.onSpinWait();
            }
            return target;
        }
    }

    private record Background(ScheduledThreadPoolExecutor pool) implements Runnable {
        @Override
        public void run() {
            pool.schedule(this, BACKGROUND.next(ThreadLocalRandom.current()), TimeUnit.MILLISECONDS);
        }
    }
}
