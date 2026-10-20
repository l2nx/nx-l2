/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import app.l2nx.network.metrics.NetworkMetrics;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public final class Connections {
    private static final AttributeKey<CloseReason> CLOSE_REASON = AttributeKey.valueOf("l2nx.network.closeReason");
    private static final NetworkMetrics NOOP = NetworkMetrics.noop();

    private Connections() {}

    /**
     * Thread-safe: the attribute is set atomically, so concurrent closes count one reason.
     */
    public static void close(Channel channel, CloseReason reason) {
        if (channel.attr(CLOSE_REASON).setIfAbsent(reason) == null) {
            metrics(channel).closed(reason);
        }
        channel.close();
    }

    public static CloseReason closeReason(Channel channel) {
        return channel.attr(CLOSE_REASON).get();
    }

    public static NetworkMetrics metrics(Channel channel) {
        final NetworkMetrics metrics = channel.attr(NetworkMetrics.KEY).get();
        return metrics == null ? NOOP : metrics;
    }
}
