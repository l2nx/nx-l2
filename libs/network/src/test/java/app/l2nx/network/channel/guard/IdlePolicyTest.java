/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdlePolicyTest {
    private final EmbeddedChannel channel = TestChannels.embedded();
    private final AtomicInteger pings = new AtomicInteger();
    private final AtomicInteger farewells = new AtomicInteger();

    @Nested
    class Install {
        @Test
        void readIdle_shouldCloseWithIdle_afterReadTimeoutBeforeGame() {
            idle(119);
            assertThat(channel.isOpen()).isTrue();

            idle(1);

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.IDLE);
        }
    }

    @Nested
    class StartPinging {
        @Test
        void ping_shouldPingEachIntervalAndCloseAfterThreeMisses() {
            IdlePolicy.of(channel).startPinging(pings::incrementAndGet);
            channel.runPendingTasks();

            idle(30);
            idle(30);
            idle(30);
            assertThat(pings.get()).isEqualTo(3);
            assertThat(channel.isOpen()).isTrue();

            idle(30);

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.IDLE);
        }

        @Test
        void ping_shouldResetMisses_whenClientReads() {
            IdlePolicy.of(channel).startPinging(pings::incrementAndGet);
            channel.runPendingTasks();
            idle(30);
            idle(30);

            channel.writeInbound(Unpooled.buffer(1).writeByte(1));
            idle(30);
            idle(30);
            idle(30);

            assertThat(channel.isOpen()).isTrue();
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class StopPinging {
        @Test
        void stopPinging_shouldRestoreReadTimeout() {
            IdlePolicy.of(channel).startPinging(pings::incrementAndGet);
            channel.runPendingTasks();
            IdlePolicy.of(channel).stopPinging();
            channel.runPendingTasks();

            idle(119);
            assertThat(pings.get()).isZero();
            assertThat(channel.isOpen()).isTrue();
            idle(1);
            assertThat(channel.isOpen()).isFalse();
        }
    }

    @Nested
    class ExtendReadTimeout {
        @Test
        void extendReadTimeout_shouldRunTheFarewellOnceAndCountIdle_afterTheNewBound() {
            IdlePolicy.of(channel).extendReadTimeout(Duration.ofMinutes(5), this::farewell);
            channel.runPendingTasks();

            idle(299);
            assertThat(farewells.get()).isZero();

            idle(1);

            assertThat(farewells.get()).isEqualTo(1);
            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.IDLE);
            assertThat(closed(CloseReason.IDLE)).isEqualTo(1.0);
            assertThat(closed(CloseReason.TIMEOUT)).isZero();
        }

        @Test
        void extendReadTimeout_shouldLeaveTheConnectionToTheFarewell_untilTheNextReadIdle() {
            IdlePolicy.of(channel).extendReadTimeout(Duration.ofMinutes(5), farewells::incrementAndGet);
            channel.runPendingTasks();

            idle(300);
            assertThat(farewells.get()).isEqualTo(1);
            assertThat(channel.isOpen()).isTrue();

            idle(300);

            assertThat(farewells.get()).isEqualTo(1);
            assertThat(channel.isOpen()).isFalse();
            assertThat(closed(CloseReason.IDLE)).isEqualTo(1.0);
        }

        @Test
        void stopPinging_shouldDropTheFarewell() {
            IdlePolicy.of(channel).extendReadTimeout(Duration.ofMinutes(5), farewells::incrementAndGet);
            channel.runPendingTasks();
            IdlePolicy.of(channel).stopPinging();
            channel.runPendingTasks();

            idle(120);

            assertThat(farewells.get()).isZero();
            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.IDLE);
        }

        private void farewell() {
            farewells.incrementAndGet();
            Connections.close(channel, CloseReason.TIMEOUT);
        }

        private double closed(CloseReason reason) {
            return TestChannels.registry(channel)
                    .get("l2nx.network.closed")
                    .tag("reason", reason.tag())
                    .counter()
                    .count();
        }
    }

    IdlePolicyTest() {
        IdlePolicy.install(channel.pipeline(), NetworkProperties.defaults());
        channel.pipeline().addLast(TailExceptionHandler.INSTANCE);
    }

    private void idle(long seconds) {
        channel.advanceTimeBy(seconds, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
    }
}
