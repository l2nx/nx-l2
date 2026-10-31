/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.MessageParam;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.protocol.game.p267.serverpackets.ActionFailed;
import app.l2nx.protocol.game.p267.serverpackets.SystemMessage;
import app.l2nx.protocol.game.p267.serverpackets.SystemMessageParam;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MessageMappersTest {
    @Nested
    class ActionCancelledMapping {
        @Test
        void map_shouldBeActionFailed() {
            assertThat(new ActionCancelledMapper().map(ActionCancelled.INSTANCE, null))
                    .isSameAs(ActionFailed.INSTANCE);
        }

        @Test
        void serialize_shouldWriteTheActionFailedOpcode() {
            assertThat(payload(P267Mappers.serialize(ActionCancelled.INSTANCE, null, UnpooledByteBufAllocator.DEFAULT)))
                    .isEqualTo("1f");
        }
    }

    @Nested
    class MessageShownMapping {
        @Test
        void map_shouldBeTheSystemMessageOfTheKey_withoutParameters() {
            final MessageShown event = MessageShown.of(MessageKey.CANNOT_RESTART_IN_COMBAT);

            assertThat(new MessageShownMapper().map(event, null))
                    .isEqualTo(
                            new SystemMessage(SystemMessageId.YOU_CANNOT_RESTART_WHILE_IN_COMBAT.getId(), List.of()));
        }

        @Test
        void map_shouldWriteTheAccountKickMessage() {
            final MessageShown event = MessageShown.of(MessageKey.ACCOUNT_LOGGED_IN_ELSEWHERE);

            assertThat(new MessageShownMapper().map(event, null))
                    .isEqualTo(new SystemMessage(
                            SystemMessageId.ANOTHER_PERSON_HAS_LOGGED_IN_WITH_THE_SAME_ACCOUNT.getId(), List.of()));
        }

        // S1_2 ("$s1") takes one parameter, so filling it never grows a shared message id's parameter count.
        @Test
        void map_shouldTurnText_intoATextParameter() {
            assertThat(MessageShownMapper.map(SystemMessageId.S1_2, List.of(new MessageParam.Text("x")), null))
                    .isEqualTo(
                            new SystemMessage(SystemMessageId.S1_2.getId(), List.of(new SystemMessageParam.Text("x"))));
        }

        @Test
        void map_shouldTurnANumber_intoALongNumberParameter() {
            assertThat(MessageShownMapper.map(SystemMessageId.S1_2, List.of(new MessageParam.Numeric(5)), null))
                    .isEqualTo(new SystemMessage(
                            SystemMessageId.S1_2.getId(), List.of(new SystemMessageParam.LongNumber(5))));
        }

        @Test
        void map_shouldTurnAPlayerName_intoAPlayerNameParameter() {
            assertThat(MessageShownMapper.map(
                            SystemMessageId.S1_2, List.of(new MessageParam.PlayerName("Alice")), null))
                    .isEqualTo(new SystemMessage(
                            SystemMessageId.S1_2.getId(), List.of(new SystemMessageParam.PlayerName("Alice"))));
        }

        @Test
        void serialize_shouldWriteTheIdAndAnEmptyParameterList() {
            final ByteBuf frame = P267Mappers.serialize(
                    MessageShown.of(MessageKey.CANNOT_RESTART_IN_COMBAT), null, UnpooledByteBufAllocator.DEFAULT);

            assertThat(payload(frame))
                    .isEqualTo("62" + littleEndian(SystemMessageId.YOU_CANNOT_RESTART_WHILE_IN_COMBAT.getId())
                            + "00000000");
        }

        @Test
        void mapper_shouldDependOnTheViewer() {
            assertThat(new MessageShownMapper().viewerDependent()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(MessageKey.class)
    void everyKey_shouldMapToAHighFiveMessage(MessageKey key) {
        assertThat(P267Messages.id(key)).isNotNull();
    }

    private static String payload(ByteBuf frame) {
        try {
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2));
        } finally {
            frame.release();
        }
    }

    private static String littleEndian(int value) {
        return HexFormat.of()
                .formatHex(new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)});
    }
}
