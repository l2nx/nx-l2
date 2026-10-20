/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class TimerWheelBenchmark {
    private static final long WINDOW = 10_000;
    private static final TimerHandler NOOP = (loop, entry) -> {};
    private static final TimerSpec PERIODIC = TimerSpec.builder()
            .handler(NOOP)
            .kind(TimerKind.TICK)
            .period(WINDOW)
            .build();
    private static final TimerSpec ONE_SHOT =
            TimerSpec.builder().handler(NOOP).kind(TimerKind.ACTION).build();

    @Param({"1000", "100000"})
    public int live;

    private TimerWheel wheel;
    private long now;

    @Setup(Level.Iteration)
    public void setUp() {
        wheel = new TimerWheel(0, (phase, source, failure) -> {
            throw failure;
        });
        final SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < live; i++) {
            wheel.schedule(random.nextLong(1, WINDOW), PERIODIC);
        }
        now = 0;
    }

    @Benchmark
    public int advanceOneMillisecond() {
        now++;
        return wheel.runDue(null, now, Integer.MAX_VALUE, 10, Long.MIN_VALUE);
    }

    @Benchmark
    public int scheduleAndFire() {
        now++;
        wheel.schedule(now, ONE_SHOT);
        return wheel.runDue(null, now, Integer.MAX_VALUE, 10, Long.MIN_VALUE);
    }
}
