/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.RespawnDelay;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Cost of one timer of the {@link TimerQueue} while {@code live} other timers are pending: scheduled and fired one by
 * one, scheduled and fired in a batch of one pass, and the same batch with every second timer cancelled before it
 * falls due.
 *
 * <p>Background timers: {@code live} respawn-like timers re-armed 1 s to 10 min after each firing, started in steady
 * state, so {@code live / 300 s} fall due per second from the start. Virtual time: 1 ms per single operation, one pass
 * of {@link #PASS_MILLIS} per batch.
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
    public int scheduleAndFire(Timers state) {
        state.now++;
        state.queue.schedule(state.now, Timers.SPEC);
        return state.queue.runDue(null, state.now, Integer.MAX_VALUE, Long.MIN_VALUE);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int batch(Timers state) {
        final long from = state.now;
        for (int i = 0; i < BATCH; i++) {
            state.queue.schedule(from + 1 + i % PASS_MILLIS, Timers.SPEC);
        }
        state.now = from + PASS_MILLIS;
        return state.queue.runDue(null, state.now, Integer.MAX_VALUE, Long.MIN_VALUE);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int batchWithCancels(Timers state) {
        final long from = state.now;
        final TimerEntry[] entries = state.entries;
        for (int i = 0; i < BATCH; i++) {
            entries[i] = state.queue.schedule(from + 1 + i % PASS_MILLIS, Timers.SPEC);
        }
        for (int i = 0; i < BATCH; i += 2) {
            state.queue.cancel(entries[i]);
        }
        state.now = from + PASS_MILLIS;
        return state.queue.runDue(null, state.now, Integer.MAX_VALUE, Long.MIN_VALUE);
    }

    @State(Scope.Thread)
    public static class Timers {
        static final TimerSpec SPEC =
                TimerSpec.builder().handler((loop, entry) -> {}).build();

        @Param({"1000", "100000", "1000000"})
        public int live;

        TimerQueue queue;
        long now;
        final TimerEntry[] entries = new TimerEntry[BATCH];
        private final SplittableRandom random = new SplittableRandom(1);
        private final TimerSpec background = TimerSpec.builder()
                .handler(
                        (loop, entry) -> queue.schedule(entry.getDeadline() + BACKGROUND.next(random), this.background))
                .build();

        // Per trial: re-arming keeps the population steady, so iterations need no fresh queue.
        @Setup(Level.Trial)
        public void setUp() {
            queue = new TimerQueue((phase, source, failure) -> {
                throw failure;
            });
            now = 0;
            for (int i = 0; i < live; i++) {
                queue.schedule(BACKGROUND.first(random), background);
            }
        }
    }
}
