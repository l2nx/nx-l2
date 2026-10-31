/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.test.TestChannels;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConnectionDeadlinesTest {
    private final EmbeddedChannel channel =
            TestChannels.embedded(new ConnectionDeadlines(Duration.ofSeconds(10), Duration.ofSeconds(60)));

    @Nested
    class HandlerAdded {
        @Test
        void handlerAdded_shouldCloseWithTimeout_whenTheHandshakeIsNotReachedInTime() {
            channel.advanceTimeBy(10, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.TIMEOUT);
        }
    }

    @Nested
    class HandshakeReached {
        @Test
        void handshakeReached_shouldLeaveTheAuthDeadlineRunning() {
            ConnectionDeadlines.handshakeReached(channel);
            channel.runPendingTasks();
            channel.advanceTimeBy(59, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            assertThat(channel.isOpen()).isTrue();

            channel.advanceTimeBy(1, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void handshakeReached_shouldDoNothing_whenTheChannelHasClosed() {
            channel.close();

            assertThatCode(() -> ConnectionDeadlines.handshakeReached(channel)).doesNotThrowAnyException();
        }
    }

    @Nested
    class AuthReached {
        @Test
        void authReached_shouldCancelBothTimers() {
            ConnectionDeadlines.authReached(channel);
            channel.runPendingTasks();
            channel.advanceTimeBy(5, TimeUnit.MINUTES);
            channel.runScheduledPendingTasks();

            assertThat(channel.isOpen()).isTrue();
            channel.finishAndReleaseAll();
        }

        @Test
        void authReached_shouldDoNothing_whenTheChannelHasClosed() {
            channel.close();

            assertThatCode(() -> ConnectionDeadlines.authReached(channel)).doesNotThrowAnyException();
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void handlerRemoved_shouldCancelBothTimers() {
            channel.pipeline().remove(ConnectionDeadlines.class);
            channel.advanceTimeBy(5, TimeUnit.MINUTES);
            channel.runScheduledPendingTasks();

            assertThat(channel.isOpen()).isTrue();
            channel.finishAndReleaseAll();
        }
    }
}
