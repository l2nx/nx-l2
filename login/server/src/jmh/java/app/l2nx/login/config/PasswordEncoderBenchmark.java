/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.security.crypto.password.PasswordEncoder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class PasswordEncoderBenchmark {
    private static final String PASSWORD = "correct-horse";

    private PasswordEncoder encoder;
    private String stored;

    @Setup
    public void setUp() {
        encoder = PasswordEncoderConfiguration.argon2();
        stored = encoder.encode(PASSWORD);
    }

    @Benchmark
    public String encode() {
        return encoder.encode(PASSWORD);
    }

    @Benchmark
    public boolean matches() {
        return encoder.matches(PASSWORD, stored);
    }

    @Benchmark
    @Threads(12)
    public String encodeOnTwelveThreads() {
        return encoder.encode(PASSWORD);
    }

    @Benchmark
    @Threads(12)
    public boolean matchesOnTwelveThreads() {
        return encoder.matches(PASSWORD, stored);
    }
}
