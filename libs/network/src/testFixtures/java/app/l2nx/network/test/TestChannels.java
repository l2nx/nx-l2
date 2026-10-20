/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import app.l2nx.network.metrics.NetworkMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import java.util.stream.Stream;

public final class TestChannels {
    private static final AttributeKey<MeterRegistry> REGISTRY = AttributeKey.valueOf("test.registry");

    private TestChannels() {}

    public static EmbeddedChannel embedded(ChannelHandler... handlers) {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        channel.attr(REGISTRY).set(registry);
        channel.attr(NetworkMetrics.KEY).set(new NetworkMetrics(registry));
        channel.pipeline().addLast(handlers);
        return channel;
    }

    public static Stream<ByteBufAllocator> allocators() {
        return Stream.of(
                new AdaptiveByteBufAllocator(), PooledByteBufAllocator.DEFAULT, UnpooledByteBufAllocator.DEFAULT);
    }

    public static MeterRegistry registry(Channel channel) {
        return channel.attr(REGISTRY).get();
    }

    public static ByteBuf frame(int... bytes) {
        final ByteBuf frame = Unpooled.buffer(2 + bytes.length);
        frame.writeShortLE(2 + bytes.length);
        for (int b : bytes) {
            frame.writeByte(b);
        }
        return frame;
    }
}
