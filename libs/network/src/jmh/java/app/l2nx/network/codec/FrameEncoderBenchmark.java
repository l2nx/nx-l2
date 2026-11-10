/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import app.l2nx.network.crypt.GameXorCipher;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.XorChecksum;
import app.l2nx.network.metrics.NetworkMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Encode cost per frame: in place for a sole owner, copy-first for a broadcast-shared frame. Frames are made outside
 * the measured time; {@code allocateOnly} is the residual baseline.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FrameEncoderBenchmark {
    private static final ByteBufAllocator ALLOC = ByteBufAllocator.DEFAULT;

    @Param({"64", "512", "4096"})
    private int payload;

    @Param({"none", "blowfish", "game"})
    private String cipher;

    private FrameEncoder encoder;
    private ByteBuf shared;
    private ByteBuf sole;
    private ByteBuf sharedHolder;

    @Setup(Level.Trial)
    public void setUp() {
        encoder = new FrameEncoder(cipher(cipher), NetworkMetrics.noop());
        shared = frame();
        final ByteBuf probe = Frames.share(shared);
        final ByteBuf finished = encoder.finish(ALLOC, probe);
        if (finished == probe) {
            finished.release();
            throw new IllegalStateException("A shared frame was encrypted in place");
        }
        finished.release();
    }

    @Setup(Level.Invocation)
    public void nextFrames() {
        sole = frame();
        sharedHolder = Frames.share(shared);
    }

    @TearDown(Level.Invocation)
    public void releaseUnused() {
        // Each benchmark consumes one of the two; the other is released here.
        if ((sole != null) && (sole.refCnt() > 0)) {
            sole.release();
        }
        if ((sharedHolder != null) && (sharedHolder.refCnt() > 1)) {
            sharedHolder.release();
        }
        sole = null;
        sharedHolder = null;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        shared.release();
    }

    @Benchmark
    public int encodeInPlace() {
        final ByteBuf finished = encoder.finish(ALLOC, sole);
        sole = null;
        final int length = finished.readableBytes();
        finished.release();
        return length;
    }

    @Benchmark
    public int encodeShared() {
        final ByteBuf finished = encoder.finish(ALLOC, sharedHolder);
        sharedHolder = null;
        final int length = finished.readableBytes();
        finished.release();
        return length;
    }

    @Benchmark
    public int allocateOnly() {
        return sole.readableBytes();
    }

    static FrameCipher cipher(String name) {
        return switch (name) {
            case "none" -> FrameCipher.NONE;
            case "blowfish" -> new BlowfishChecksumCipher();
            case "game" -> new GameXorShape();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private ByteBuf frame() {
        return Frames.allocate(ALLOC, payload + 16).writeZero(payload);
    }

    static final class GameXorShape implements FrameCipher {
        private final GameXorCipher xor = new GameXorCipher();

        GameXorShape() {
            xor.setKey(new byte[16]);
        }

        @Override
        public int encryptedLength(int payloadLength) {
            return payloadLength;
        }

        @Override
        public void encrypt(ByteBuf buf, int index, int payloadLength) {
            xor.encrypt(buf, index, payloadLength);
        }

        @Override
        public boolean decrypt(ByteBuf buf, int index, int length) {
            xor.decrypt(buf, index, length);
            return true;
        }
    }

    /** The login session cipher's shape, without depending on the login module. */
    private static final class BlowfishChecksumCipher implements FrameCipher {
        private final LittleEndianBlowfishCipher blowfish = new LittleEndianBlowfishCipher(new byte[16]);

        @Override
        public int encryptedLength(int payloadLength) {
            final int withChecksum = payloadLength + 4;
            return withChecksum + (8 - (withChecksum % 8)) + 8;
        }

        @Override
        public void encrypt(ByteBuf buf, int index, int payloadLength) {
            final int length = encryptedLength(payloadLength);
            buf.setZero(index + payloadLength, length - payloadLength);
            XorChecksum.append(buf, index, length);
            blowfish.encrypt(buf, index, length);
        }

        @Override
        public boolean decrypt(ByteBuf buf, int index, int length) {
            return true;
        }
    }
}
