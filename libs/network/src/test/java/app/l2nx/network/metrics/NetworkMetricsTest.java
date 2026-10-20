/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.config.Transport;
import app.l2nx.network.metrics.model.Direction;
import app.l2nx.network.metrics.model.DropReason;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.network.transport.TransportResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBufAllocator;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NetworkMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NetworkMetrics metrics = new NetworkMetrics(registry);

    @Nested
    class Meters {
        @Test
        void meters_shouldUseSpecNamesAndTags() {
            metrics.connectionOpened();
            metrics.connectionOpened();
            metrics.connectionClosed();
            metrics.bytes(Direction.IN, 10);
            metrics.bytes(Direction.OUT, 7);
            metrics.command(String.class);
            metrics.legacyPacket(Integer.class, Direction.OUT);
            metrics.decodeError("IN_GAME");
            metrics.dropped(DropReason.UNKNOWN_OPCODE);
            metrics.closed(CloseReason.OUTBOUND_LIMIT);
            metrics.unwritable();

            assertThat(registry.get("l2nx.network.connections").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("l2nx.network.bytes")
                            .tag("direction", "in")
                            .counter()
                            .count())
                    .isEqualTo(10.0);
            assertThat(registry.get("l2nx.network.bytes")
                            .tag("direction", "out")
                            .counter()
                            .count())
                    .isEqualTo(7.0);
            assertThat(registry.get("l2nx.network.commands")
                            .tag("type", "java.lang.String")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.network.legacy.packets")
                            .tag("type", "java.lang.Integer")
                            .tag("direction", "out")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.network.decode.errors")
                            .tag("state", "IN_GAME")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "unknown-opcode")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.network.closed")
                            .tag("reason", "outbound-limit")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.network.unwritable").counter().count())
                    .isEqualTo(1.0);
        }

        @Test
        void closeReasonTags_shouldMatchSpec() {
            assertThat(Arrays.stream(CloseReason.values()).map(CloseReason::tag))
                    .containsExactly("idle", "timeout", "flood", "slow", "outbound-limit", "error");
        }
    }

    @Nested
    class BindEventLoops {
        @Test
        void bindEventLoops_shouldRegisterMicrometerNettyMetersPerGroup() {
            try (NetworkEventLoops loops = new NetworkEventLoops(
                    NetworkProperties.defaults().toBuilder()
                            .transport(Transport.NIO)
                            .workerThreads(2)
                            .shutdownQuietPeriod(Duration.ZERO)
                            .build(),
                    TransportResolver.nativeAvailability())) {
                metrics.bindEventLoops(loops);

                assertThat(registry.find("netty.eventexecutor.tasks.pending").gauges())
                        .hasSize(3);
                assertThat(registry.get("netty.eventexecutor.workers")
                                .tag("group", "io")
                                .gauge()
                                .value())
                        .isEqualTo(2.0);
                assertThat(registry.get("netty.eventexecutor.workers")
                                .tag("group", "boss")
                                .gauge()
                                .value())
                        .isEqualTo(1.0);
            }
        }
    }

    @Nested
    class BindAllocator {
        @Test
        void bindAllocator_shouldRegisterMicrometerAllocatorMeters() {
            metrics.bindAllocator(ByteBufAllocator.DEFAULT);

            assertThat(registry.find("netty.allocator.memory.used").gauges()).isNotEmpty();
        }
    }

    @Nested
    class Command {
        @Test
        void command_shouldTagTheFullClassName_whenClassIsAnonymous() {
            final Runnable anonymous = new Runnable() {
                @Override
                public void run() {}
            };

            metrics.command(anonymous.getClass());

            assertThat(registry.get("l2nx.network.commands")
                            .tag("type", anonymous.getClass().getName())
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(anonymous.getClass().getName()).isNotBlank();
        }
    }
}
