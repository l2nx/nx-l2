/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.network.LoginClient;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.metrics.model.DropReason;
import app.l2nx.network.test.TestChannels;
import io.micrometer.core.instrument.Counter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginPacketDecoderTest {
    private static final int AUTH_GAME_GUARD = 0x07;
    private static final int REQUEST_SERVER_LIST = 0x05;

    private final LoginClient client = mock(LoginClient.class);
    private final EmbeddedChannel channel = TestChannels.embedded(new LoginPacketDecoder());

    LoginPacketDecoderTest() {
        channel.attr(LoginClient.KEY).set(client);
        when(client.getConnectionState()).thenReturn(ConnectionState.CONNECTED);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class ChannelRead {
        @Test
        void channelRead_shouldReleaseTheFrameBeforeHandingThePacketToTheLane() {
            final ByteBuf frame = payload(AUTH_GAME_GUARD, 20);
            final AtomicInteger refCntOnLane = new AtomicInteger(-1);
            doAnswer(invocation -> {
                        refCntOnLane.set(frame.refCnt());
                        return null;
                    })
                    .when(client)
                    .execute(any(Runnable.class));

            channel.writeInbound(frame);

            verify(client).execute(any(Runnable.class));
            assertThat(refCntOnLane).hasValue(0);
        }

        @Test
        void channelRead_shouldCountAndCloseWithError_whenTheHandOffToTheLaneThrows() {
            final ByteBuf frame = payload(AUTH_GAME_GUARD, 20);
            doThrow(new IllegalStateException("lane")).when(client).execute(any(Runnable.class));

            channel.writeInbound(frame);

            assertThat(frame.refCnt()).isZero();
            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.decode.errors")
                            .tag("state", "CONNECTED")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }

        @Test
        void channelRead_shouldReleaseTheFrameOnce_whenTheOpcodeIsUnknown() {
            final ByteBuf frame = payload(0x55, 0);

            channel.writeInbound(frame);

            assertThat(frame.refCnt()).isZero();
            verify(client, never()).execute(any(Runnable.class));
        }

        @Test
        void channelRead_shouldHandThePacketToTheLane_whateverTheState() {
            final ByteBuf frame = payload(REQUEST_SERVER_LIST, 8);

            channel.writeInbound(frame);

            assertThat(frame.refCnt()).isZero();
            assertThat(dropped()).isZero();
            verify(client).execute(any(Runnable.class));
        }

        @Test
        void channelRead_shouldReleaseTheFrameOnce_whenTheDecodeFails() {
            final ByteBuf frame = payload(AUTH_GAME_GUARD, 1);

            channel.writeInbound(frame);

            assertThat(frame.refCnt()).isZero();
            assertThat(channel.isOpen()).isFalse();
            verify(client, never()).execute(any(Runnable.class));
        }
    }

    @Nested
    class IgnoredOpcodes {
        @ParameterizedTest(name = "opcode {0}")
        @ValueSource(ints = {0x0E, 0x0F})
        void channelRead_shouldCountAsIgnored_whenThePiAgreementOpcodeArrivesAuthed(int opcode) {
            when(client.getConnectionState()).thenReturn(ConnectionState.AUTHED_LOGIN);
            final ByteBuf frame = payload(opcode, 4);

            channel.writeInbound(frame);

            assertThat(frame.refCnt()).isZero();
            assertThat(channel.isOpen()).isTrue();
            assertThat(dropped()).isEqualTo(1.0);
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.dropped")
                            .tag("reason", DropReason.IGNORED.tag())
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            verify(client, never()).execute(any(Runnable.class));
        }

        @ParameterizedTest
        @EnumSource(
                value = ConnectionState.class,
                names = {"CONNECTED", "AUTHED_GG"})
        void channelRead_shouldCountAsIgnored_whenThePiAgreementOpcodeArrivesBeforeLogin(ConnectionState state) {
            when(client.getConnectionState()).thenReturn(state);

            channel.writeInbound(payload(0x0E, 0));

            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.dropped")
                            .tag("reason", DropReason.IGNORED.tag())
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            verify(client, never()).execute(any(Runnable.class));
        }
    }

    private double dropped() {
        return TestChannels.registry(channel).find("l2nx.network.dropped").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private static ByteBuf payload(int opcode, int body) {
        return Unpooled.buffer().writeByte(opcode).writeZero(body);
    }
}
