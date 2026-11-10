/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import app.l2nx.engine.core.session.outbound.Audience;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.network.protocol.p267.P267Adapter;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.test.TestChannels;
import app.l2nx.protocol.game.p267.crypt.GameFrameCipher;
import app.l2nx.protocol.game.p267.crypt.GameXorKeys;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

/**
 * One chat line to N p267 game connections, from {@link Audience#emit} to the bytes the encoder writes, each connection
 * mapping and serializing its own frame: the baseline a replicated fan-out is measured against.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class ChatBroadcastBenchmark {
    private static final String SENDER_NAME = "ShouterOfGiran16";

    @Param({"1", "10", "50", "200"})
    private int viewers;

    // 105 is the longest line the client lets a player type.
    @Param({"16", "105"})
    private int textLength;

    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private final List<ClientPort> ports = new ArrayList<>();
    private ChatMessage message;

    @Setup
    public void setUp() {
        final P267Adapter adapter = new P267Adapter();
        final List<byte[]> keys = new ArrayList<>(viewers);
        for (int i = 0; i < viewers; i++) {
            final byte[] key = GameXorKeys.getRandomKey();
            final GameFrameCipher cipher = new GameFrameCipher();
            cipher.setKey(key);
            final NetworkMetrics metrics = NetworkMetrics.noop();
            final OutboundQueue outbound = new OutboundQueue(NetworkProperties.defaults(), metrics);
            final EmbeddedChannel channel = TestChannels.embedded(new FrameEncoder(cipher, metrics), outbound);
            final GameConnection connection =
                    new GameConnection(channel, cipher, outbound, metrics, NetworkProperties.defaults());
            channel.attr(GameConnection.KEY).set(connection);
            connection.bind(null, new GameClient(connection));
            connection.accept(adapter, 267);
            // The clear key packet is the frame that switches the rolling XOR on, as in the version handshake.
            connection.outbound().send(adapter.keyFrame(channel.alloc(), key));
            channel.runPendingTasks();
            channel.releaseOutbound();
            if (!cipher.isEnabled()) {
                throw new IllegalStateException("The key frame did not switch the cipher on");
            }
            channels.add(channel);
            keys.add(key);
            ports.add(new GameClientAdapter(connection));
        }
        message = ChatMessage.builder()
                .senderHandle(0x10000001)
                .type(ChatType.SHOUT)
                .senderName(SENDER_NAME)
                .text("a".repeat(textLength))
                .build();
        verifyOneFramePerChannel(keys);
    }

    @TearDown
    public void tearDown() {
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    @Benchmark
    public void broadcast() {
        Audience.emit(message, ports);
        for (EmbeddedChannel channel : channels) {
            channel.runPendingTasks();
            channel.releaseOutbound();
        }
    }

    // Each client must decrypt exactly the frame a sole viewer would get, so the score covers a working broadcast.
    private void verifyOneFramePerChannel(List<byte[]> keys) {
        final byte[] expected = plainPayload();
        Audience.emit(message, ports);
        for (int i = 0; i < channels.size(); i++) {
            final EmbeddedChannel channel = channels.get(i);
            channel.runPendingTasks();
            if (channel.outboundMessages().size() != 1) {
                channel.releaseOutbound();
                throw new IllegalStateException(
                        "Viewer " + i + " got " + channel.outboundMessages().size() + " frames instead of one");
            }
            final ByteBuf frame = channel.readOutbound();
            try {
                final GameFrameCipher client = GameFrameCipher.client();
                client.setKey(keys.get(i));
                final int start = frame.readerIndex() + Frames.HEADER;
                final int length = frame.readableBytes() - Frames.HEADER;
                client.decrypt(frame, start, length);
                if (!Arrays.equals(expected, ByteBufUtil.getBytes(frame, start, length))) {
                    throw new IllegalStateException("Viewer " + i + " cannot read the chat line");
                }
            } finally {
                frame.release();
            }
        }
    }

    private byte[] plainPayload() {
        final GameConnection connection = Objects.requireNonNull(GameConnection.of(channels.getFirst()));
        final ByteBuf frame = connection.adapter().serialize(message, connection.client(), connection.alloc());
        try {
            return ByteBufUtil.getBytes(
                    frame, frame.readerIndex() + Frames.HEADER, frame.readableBytes() - Frames.HEADER);
        } finally {
            frame.release();
        }
    }
}
