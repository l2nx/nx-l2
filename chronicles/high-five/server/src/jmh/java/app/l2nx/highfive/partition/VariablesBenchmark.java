/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopBinding;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.mechanics.variables.AbstractVariables;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class VariablesBenchmark {
    private static final int KEYS = 20;

    private Loop previous;
    private AbstractVariables variables;
    private String[] keys;
    private int cursor;

    // Setup runs on the benchmark thread, which therefore owns the world loop.
    @Setup
    public void setUp() {
        final ManualGameClock clock = new ManualGameClock(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
        final Loop loop =
                new Loop(PartitionRuntime.WORLD, clock, 1L, EngineProperties.defaults(), new SimpleMeterRegistry());
        LoopBinding.bindToCurrentThread(loop);
        previous = WorldPartition.swap(loop);

        // The concrete variables restore from the database; the base keeps the map, the change tracking and the API.
        variables = new AbstractVariables() {};
        keys = new String[KEYS];
        for (int i = 0; i < KEYS; i++) {
            keys[i] = "VAR_" + i;
            variables.set(keys[i], i);
        }
    }

    @TearDown
    public void tearDown() {
        WorldPartition.swap(previous);
    }

    @Benchmark
    public int read() {
        int sum = 0;
        for (String key : keys) {
            sum += variables.getInt(key, 0);
        }

        return sum;
    }

    @Benchmark
    public void write() {
        variables.set(keys[cursor++ % KEYS], cursor);
    }

    @Benchmark
    public boolean has() {
        return variables.hasVariable(keys[KEYS - 1]);
    }
}
