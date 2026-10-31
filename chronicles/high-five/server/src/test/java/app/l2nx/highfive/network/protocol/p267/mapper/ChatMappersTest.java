/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.test.GoldenBytes;
import app.l2nx.protocol.game.p267.ChatChannel;
import app.l2nx.protocol.game.p267.serverpackets.CreatureSay;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChatMappersTest {
    private static final int HANDLE = 0x10000001;
    private static final ChatMessage SHOUT = ChatMessage.builder()
            .senderHandle(HANDLE)
            .type(ChatType.SHOUT)
            .senderName("A")
            .text("b")
            .build();
    private static final CreatureSay SHOUT_PACKET = CreatureSay.builder()
            .senderId(HANDLE)
            .channel(ChatChannel.SHOUT)
            .senderName("A")
            .messageId(CreatureSay.NO_MESSAGE)
            .text("b")
            .parameters(List.of())
            .build();

    @Nested
    class ChatMessageMapping {
        @Test
        void map_shouldBeTheCreatureSayOfAPlayerLine() {
            assertThat(new ChatMessageMapper().map(SHOUT, null)).isEqualTo(SHOUT_PACKET);
        }

        @Test
        void map_shouldNameTheRecipientAfterAnArrow_whenTheLineIsAWhisperEcho() {
            final ChatMessage echo = SHOUT.toBuilder()
                    .type(ChatType.WHISPER)
                    .recipientName("Bob")
                    .build();

            assertThat(new ChatMessageMapper().map(echo, null))
                    .isEqualTo(SHOUT_PACKET.toBuilder()
                            .channel(ChatChannel.WHISPER)
                            .senderName("->Bob")
                            .build());
        }

        @Test
        void serialize_shouldWriteTheRecordThroughTheAdapterTable() {
            final ByteBuf frame = P267Mappers.serialize(SHOUT, null, UnpooledByteBufAllocator.DEFAULT);
            try {
                final String actual = HexFormat.of()
                        .formatHex(ByteBufUtil.getBytes(frame, Frames.HEADER, frame.readableBytes() - Frames.HEADER));

                assertThat(actual).isEqualTo(GoldenBytes.written(SHOUT_PACKET::write));
            } finally {
                frame.release();
            }
        }
    }
}
