/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.ChatChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class Say2Test {
    @Nested
    class Decode {
        @Test
        void decode_shouldReadTextAndChannel_whenThereIsNoTarget() {
            assertThat(read(s("hello") + d(1), Say2::decode))
                    .isEqualTo(Say2.builder()
                            .text("hello")
                            .channelCode(ChatChannel.SHOUT.code())
                            .build());
        }

        @Test
        void decode_shouldReadTheTargetOfAWhisper() {
            assertThat(read(s("psst") + d(2) + s("Bob"), Say2::decode))
                    .isEqualTo(Say2.builder()
                            .text("psst")
                            .channelCode(ChatChannel.WHISPER.code())
                            .target("Bob")
                            .build());
        }

        @Test
        void decode_shouldKeepAnUnknownChannelCode_whenThereIsNoTarget() {
            final Say2 actual = read(s("hi") + d(99) + s("ignored"), Say2::decode);

            assertThat(actual)
                    .isEqualTo(Say2.builder().text("hi").channelCode(99).build());
            assertThat(actual.channel()).isNull();
        }

        @Test
        void decode_shouldKeepAnyTextLength_whenTheFrameHoldsIt() {
            final String text = "x".repeat(4096);

            assertThat(read(s(text) + d(0), Say2::decode).text()).isEqualTo(text);
        }

        @Test
        void decode_shouldKeepAnyTargetLength_whenTheFrameHoldsIt() {
            final String target = "b".repeat(65);

            assertThat(read(s("x") + d(2) + s(target), Say2::decode).target()).isEqualTo(target);
        }

        @Test
        void decode_shouldFail_whenTheTextRunsToTheEndOfTheFrame() {
            assertThatThrownBy(() -> read("7800".repeat(3), Say2::decode)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Encode {
        @Test
        void encode_shouldWriteNoTarget_whenTheChannelIsNotAWhisper() {
            final Say2 packet = Say2.builder()
                    .text("a")
                    .channelCode(ChatChannel.TRADE.code())
                    .target("x")
                    .build();

            assertThat(written(packet::encode)).isEqualTo("49" + s("a") + d(8));
        }

        @Test
        void encode_shouldWriteTheTarget_whenTheChannelIsAWhisper() {
            final Say2 packet = Say2.builder()
                    .text("a")
                    .channelCode(ChatChannel.WHISPER.code())
                    .target("Bob")
                    .build();

            assertThat(written(packet::encode)).isEqualTo("49" + s("a") + d(2) + s("Bob"));
        }
    }
}
