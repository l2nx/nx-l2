/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.chat.command.SendChat;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.protocol.game.p267.ChatChannel;
import app.l2nx.protocol.game.p267.clientpackets.Say2;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChatDecodersTest {
    @Nested
    class Say2Decoding {
        @Test
        void say2_shouldReadTextAndChannel() {
            final Say2 packet = Say2.builder()
                    .text("hi")
                    .channelCode(ChatChannel.GENERAL.code())
                    .build();

            assertThat(decode(packet::encode))
                    .isEqualTo(
                            SendChat.builder().text("hi").type(ChatType.GENERAL).build());
        }

        @Test
        void say2_shouldReadTheTarget_whenWhispering() {
            final Say2 packet = Say2.builder()
                    .text("psst")
                    .channelCode(ChatChannel.WHISPER.code())
                    .target("bob")
                    .build();

            assertThat(decode(packet::encode))
                    .isEqualTo(SendChat.builder()
                            .text("psst")
                            .type(ChatType.WHISPER)
                            .target("bob")
                            .build());
        }

        @Test
        void say2_shouldLeaveTheTargetNull_whenTheWhisperNamesNobody() {
            final Say2 packet = Say2.builder()
                    .text("psst")
                    .channelCode(ChatChannel.WHISPER.code())
                    .target(" ")
                    .build();

            assertThat(decode(packet::encode))
                    .isEqualTo(SendChat.builder()
                            .text("psst")
                            .type(ChatType.WHISPER)
                            .build());
        }

        @Test
        void say2_shouldLeaveTheTypeNull_whenTheChannelIsUnknown() {
            final Say2 packet = Say2.builder().text("x").channelCode(99).build();

            assertThat(decode(packet::encode, mock(GameConnection.class)))
                    .isEqualTo(SendChat.builder().text("x").build());
        }

        @Test
        void say2_shouldKeepALongTargetForTheHandlerToAnswer() {
            final String target = "b".repeat(65);
            final Say2 packet = Say2.builder()
                    .text("x")
                    .channelCode(ChatChannel.WHISPER.code())
                    .target(target)
                    .build();

            assertThat(decode(packet::encode))
                    .isEqualTo(SendChat.builder()
                            .text("x")
                            .type(ChatType.WHISPER)
                            .target(target)
                            .build());
        }

        @Test
        void say2_shouldKeepALongTextForTheHandlerToCap() {
            final String text = "a".repeat(4096);
            final Say2 packet = Say2.builder()
                    .text(text)
                    .channelCode(ChatChannel.SHOUT.code())
                    .build();

            assertThat(decode(packet::encode))
                    .isEqualTo(
                            SendChat.builder().text(text).type(ChatType.SHOUT).build());
        }
    }

    private static Object decode(Consumer<ByteBuf> packet) {
        return decode(packet, null);
    }

    private static Object decode(Consumer<ByteBuf> packet, GameConnection connection) {
        final ByteBuf frame = Unpooled.buffer();
        try {
            packet.accept(frame);
            frame.skipBytes(1);
            return ChatDecoders.say2(frame, connection);
        } finally {
            frame.release();
        }
    }
}
