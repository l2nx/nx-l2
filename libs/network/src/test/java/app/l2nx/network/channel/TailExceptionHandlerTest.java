/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TailExceptionHandlerTest {
    @Nested
    class ExceptionCaught {
        @Test
        void exceptionCaught_shouldCloseWithErrorAndCountIt() {
            final EmbeddedChannel channel = TestChannels.embedded(TailExceptionHandler.INSTANCE);

            channel.pipeline().fireExceptionCaught(new IOException("Connection reset by peer"));

            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.closed")
                            .tag("reason", "error")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    class ConnectionsClose {
        @Test
        void close_shouldCountOnlyTheFirstReason() {
            final EmbeddedChannel channel = TestChannels.embedded(TailExceptionHandler.INSTANCE);

            Connections.close(channel, CloseReason.FLOOD);
            Connections.close(channel, CloseReason.ERROR);

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.FLOOD);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.closed")
                            .tag("reason", "error")
                            .counter()
                            .count())
                    .isZero();
        }
    }

    @Nested
    class ChannelRead {
        @Test
        void channelRead_shouldReleaseAndCountUnhandledMessage() {
            final EmbeddedChannel channel = TestChannels.embedded(TailExceptionHandler.INSTANCE);
            final ByteBuf message = Unpooled.buffer(4).writeInt(1);

            channel.writeInbound(message);

            assertThat(message.refCnt()).isZero();
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.dropped")
                            .tag("reason", "unhandled")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class IsExpected {
        @ParameterizedTest(name = "{0}")
        @MethodSource("failures")
        void isExpected_shouldLeaveStackTracesOnlyToUnexpectedFailures(Throwable cause, boolean expected) {
            assertThat(TailExceptionHandler.isExpected(cause)).isEqualTo(expected);
        }

        static Stream<Arguments> failures() {
            return Stream.of(
                    Arguments.of(new IOException("Connection reset by peer"), true),
                    Arguments.of(new PacketDecodeException("String without terminator"), true),
                    Arguments.of(new TooLongFrameException("frame too long"), true),
                    Arguments.of(new IllegalStateException("bug"), false),
                    Arguments.of(new NullPointerException(), false));
        }
    }
}
