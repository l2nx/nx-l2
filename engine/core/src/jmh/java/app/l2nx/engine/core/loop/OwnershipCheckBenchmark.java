/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.inbox.Wakeup;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
public class OwnershipCheckBenchmark {
    private Loop loop;

    // JMH runs a thread-scoped setup on the benchmark thread, which so owns the loop.
    @Setup
    public void setUp() {
        loop = new Loop(
                "bench",
                new ManualGameClock(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC),
                1L,
                EngineProperties.defaults(),
                new SimpleMeterRegistry());
        loop.bind(Thread.currentThread(), Wakeup.NONE);
    }

    @Benchmark
    public void checkOwner() {
        loop.checkOwner();
    }
}
