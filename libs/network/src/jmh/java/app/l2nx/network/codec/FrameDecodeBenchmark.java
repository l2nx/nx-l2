/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import app.l2nx.network.crypt.GameFrameCipher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Inbound cost per read of {@code framesPerRead} chat-shaped frames: framing, in-place XOR, a bounded string read. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FrameDecodeBenchmark {
    @Param({"1", "16"})
    private int framesPerRead;

    private EmbeddedChannel channel;
    private ByteBuf batch;
    private Blackhole sink;

    @Setup
    public void setUp(Blackhole blackhole) {
        sink = blackhole;
        final GameFrameCipher cipher = new GameFrameCipher();
        cipher.setKey(new byte[16]);
        // The first outbound frame is the key packet, sent in clear; encrypting one turns the XOR on.
        final ByteBuf keyPacket = PooledByteBufAllocator.DEFAULT.buffer(1).writeByte(0);
        cipher.encrypt(keyPacket, 0, 1);
        keyPacket.release();
        channel = new EmbeddedChannel(
                Frames.decoder(16 * 1024), new CipherDecoder(cipher), new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        final ByteBuf frame = (ByteBuf) msg;
                        frame.readUnsignedByte();
                        sink.consume(Wire.readString(frame, 2_048));
                        sink.consume(frame.readIntLE());
                        frame.release();
                    }
                });
        batch = PooledByteBufAllocator.DEFAULT.buffer();
        for (int i = 0; i < framesPerRead; i++) {
            final int start = batch.writerIndex();
            batch.writeShortLE(0).writeByte(0x49);
            Wire.writeString(batch, "hello world, this is a typical chat line");
            batch.writeIntLE(0);
            batch.setShortLE(start, batch.writerIndex() - start);
        }
    }

    @TearDown
    public void tearDown() {
        batch.release();
        channel.finishAndReleaseAll();
    }

    @Benchmark
    public void decode() {
        channel.writeInbound(batch.retainedDuplicate());
    }
}
