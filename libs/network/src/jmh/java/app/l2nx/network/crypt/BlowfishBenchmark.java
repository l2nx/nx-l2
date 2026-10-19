/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import java.util.SplittableRandom;
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
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class BlowfishBenchmark {
    private static final int BLOCK_SIZE = 8;

    @Param({"64", "256", "1024", "4096"})
    public int size;

    private byte[] data;
    private BlowfishEngine engine;
    private LittleEndianBlowfishCipher cipher;

    @Setup
    public void setUp() {
        final SplittableRandom random = new SplittableRandom(1);
        final byte[] key = new byte[16];
        random.nextBytes(key);
        data = new byte[size];
        random.nextBytes(data);
        engine = new BlowfishEngine();
        engine.init(key);
        cipher = new LittleEndianBlowfishCipher(key);
    }

    @Benchmark
    public byte[] engineEncrypt() {
        for (int i = 0; i < size; i += BLOCK_SIZE) {
            engine.encryptBlock(data, i);
        }
        return data;
    }

    @Benchmark
    public byte[] engineDecrypt() {
        for (int i = 0; i < size; i += BLOCK_SIZE) {
            engine.decryptBlock(data, i);
        }
        return data;
    }

    @Benchmark
    public byte[] jceEncrypt() {
        cipher.encrypt(data, 0, size);
        return data;
    }

    @Benchmark
    public byte[] jceDecrypt() {
        cipher.decrypt(data, 0, size);
        return data;
    }
}
