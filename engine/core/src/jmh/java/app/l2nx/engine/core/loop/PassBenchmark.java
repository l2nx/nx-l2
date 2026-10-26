/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class PassBenchmark {
    private static final Duration STEP = Duration.ofMillis(5);

    @Param({"1000", "10000", "100000"})
    public int timers;

    private ManualGameClock clock;
    private Loop loop;

    @Setup
    public void setUp() {
        clock = new ManualGameClock(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
        loop = new Loop("bench", clock, 1L, EngineProperties.defaults(), new SimpleMeterRegistry());
        final TimerSpec spec =
                TimerSpec.builder().handler((l, entry) -> {}).period(1_000).build();
        final long start = clock.gameMillis();
        for (int i = 0; i < timers; i++) {
            loop.timers(TimeBase.GAME).schedule(start + 1 + i % 1_000, spec);
        }
    }

    @Benchmark
    public long pass() {
        clock.advance(STEP);
        loop.runPass(clock.gameMillis());
        return loop.getPass();
    }
}
