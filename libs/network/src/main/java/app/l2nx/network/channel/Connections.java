/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import app.l2nx.network.metrics.NetworkMetrics;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.Objects;

public final class Connections {
    private static final AttributeKey<CloseReason> CLOSE_REASON = AttributeKey.valueOf("l2nx.network.closeReason");
    private static final NetworkMetrics NOOP = NetworkMetrics.noop();

    private Connections() {}

    public static void close(Channel channel, CloseReason reason) {
        closing(channel, reason);
        channel.close();
    }

    /**
     * Counts the reason of a close that the caller performs itself, after a last packet; the first reason recorded on
     * the channel wins, as in {@link #close}.
     */
    public static void closing(Channel channel, CloseReason reason) {
        if (channel.attr(CLOSE_REASON).setIfAbsent(reason) == null) {
            metrics(channel).closed(reason);
        }
    }

    public static CloseReason closeReason(Channel channel) {
        return channel.attr(CLOSE_REASON).get();
    }

    public static NetworkMetrics metrics(Channel channel) {
        final NetworkMetrics metrics = channel.attr(NetworkMetrics.KEY).get();
        return metrics == null ? NOOP : metrics;
    }

    /**
     * @return the peer's IP address; a non-IP or unresolved peer as its address text, {@code null} once the
     *     channel has no peer
     */
    public static String remoteIp(Channel channel) {
        return channel.remoteAddress() instanceof InetSocketAddress inet && !inet.isUnresolved()
                ? inet.getAddress().getHostAddress()
                : Objects.toString(channel.remoteAddress(), null);
    }
}
