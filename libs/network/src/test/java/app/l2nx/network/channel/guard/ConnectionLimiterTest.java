/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConnectionLimiterTest {
    private final AtomicLong nanos = new AtomicLong();
    private final TimeMeter time = new TimeMeter() {
        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    };

    @Nested
    class Admit {
        @Test
        void admit_shouldRejectBannedAddress() throws Exception {
            final ConnectionLimiter limiter =
                    new ConnectionLimiter(NetworkProperties.defaults(), address -> true, time);

            assertThat(limiter.admit(ip("10.0.0.1"))).isEqualTo(DropReason.BANNED);
        }

        @Test
        void admit_shouldLimitConnectionsPerIp() throws Exception {
            final ConnectionLimiter limiter = new ConnectionLimiter(
                    NetworkProperties.defaults().toBuilder()
                            .connectionsPerIp(2)
                            .acceptPerIpPerSecond(100)
                            .build(),
                    address -> false,
                    time);
            final InetAddress address = ip("10.0.0.2");

            assertThat(limiter.admit(address)).isNull();
            assertThat(limiter.admit(address)).isNull();
            assertThat(limiter.admit(address)).isEqualTo(DropReason.CONNECTIONS_PER_IP);
            limiter.release(address);
            assertThat(limiter.admit(address)).isNull();
        }

        @Test
        void admit_shouldLimitAcceptRatePerIp() throws Exception {
            final ConnectionLimiter limiter =
                    new ConnectionLimiter(NetworkProperties.defaults(), address -> false, time);
            final InetAddress address = ip("10.0.0.3");
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.admit(address)).isNull();
                limiter.release(address);
            }

            assertThat(limiter.admit(address)).isEqualTo(DropReason.ACCEPT_RATE);
            nanos.addAndGet(Duration.ofSeconds(1).toNanos());
            assertThat(limiter.admit(address)).isNull();
        }

        @Test
        void admit_shouldLimitTotalConnections() throws Exception {
            final ConnectionLimiter limiter = new ConnectionLimiter(
                    NetworkProperties.defaults().toBuilder().maxConnections(1).build(), address -> false, time);

            assertThat(limiter.admit(ip("10.0.0.4"))).isNull();
            assertThat(limiter.admit(ip("10.0.0.5"))).isEqualTo(DropReason.MAX_CONNECTIONS);
        }
    }

    @Nested
    class TryAccept {
        @Test
        void tryAccept_shouldReleaseSlotWhenChannelCloses() {
            final ConnectionLimiter limiter = new ConnectionLimiter(
                    NetworkProperties.defaults().toBuilder().maxConnections(1).build(), address -> false, time);
            final EmbeddedChannel first = remote("10.0.0.6");

            assertThat(limiter.tryAccept(first)).isTrue();
            assertThat(limiter.tryAccept(remote("10.0.0.7"))).isFalse();
            first.close();
            assertThat(limiter.tryAccept(remote("10.0.0.7"))).isTrue();
        }

        @Test
        void tryAccept_shouldCountDrop_whenRejected() {
            final ConnectionLimiter limiter =
                    new ConnectionLimiter(NetworkProperties.defaults(), address -> true, time);
            final EmbeddedChannel channel = remote("10.0.0.8");
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            channel.attr(NetworkMetrics.KEY).set(new NetworkMetrics(registry));

            assertThat(limiter.tryAccept(channel)).isFalse();
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "banned")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }

        @Test
        void tryAccept_shouldApplyOnlyTheTotalLimit_whenPeerHasNoIpAddress() {
            final ConnectionLimiter limiter = new ConnectionLimiter(
                    NetworkProperties.defaults().toBuilder().maxConnections(1).build(), address -> true, time);
            // EmbeddedChannel's remote address is not an InetSocketAddress, like a local channel's.
            final EmbeddedChannel first = new EmbeddedChannel();

            assertThat(limiter.tryAccept(first)).isTrue();
            assertThat(limiter.tryAccept(new EmbeddedChannel())).isFalse();
            first.close();
            assertThat(limiter.tryAccept(new EmbeddedChannel())).isTrue();
            assertThat(limiter.trackedAddresses()).isZero();
        }
    }

    @Nested
    class Sweep {
        @Test
        void sweep_shouldForgetIdleAddresses() throws Exception {
            final ConnectionLimiter limiter =
                    new ConnectionLimiter(NetworkProperties.defaults(), address -> false, time);
            final InetAddress address = ip("10.0.0.9");
            limiter.admit(address);
            limiter.release(address);

            nanos.addAndGet(Duration.ofSeconds(2).toNanos());
            limiter.sweep();

            assertThat(limiter.trackedAddresses()).isZero();
        }
    }

    private static InetAddress ip(String text) throws UnknownHostException {
        return InetAddress.getByName(text);
    }

    private static EmbeddedChannel remote(String ip) {
        return new EmbeddedChannel() {
            @Override
            public SocketAddress remoteAddress() {
                return new InetSocketAddress(ip, 40000);
            }
        };
    }
}
