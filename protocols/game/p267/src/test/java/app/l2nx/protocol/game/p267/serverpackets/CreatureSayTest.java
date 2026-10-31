/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.ChatChannel;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreatureSayTest {
    private static final CreatureSay SHOUT = CreatureSay.builder()
            .senderId(0x10000001)
            .channel(ChatChannel.SHOUT)
            .senderName("A")
            .messageId(CreatureSay.NO_MESSAGE)
            .text("b")
            .build();

    @Test
    void write_shouldWriteTheNameAndTheText_ofPlayerChat() {
        assertThat(written(SHOUT::write))
                .isEqualTo("4a" + "01000010" + "01000000" + "41000000" + "ffffffff" + "62000000");
    }

    @Test
    void write_shouldWriteTheNameIdAndTheParameters_ofAMessageWithoutName() {
        final CreatureSay packet = CreatureSay.builder()
                .channel(ChatChannel.NPC_SHOUT)
                .senderNameId(5)
                .messageId(1000)
                .parameters(List.of("x", "y"))
                .build();

        assertThat(written(packet::write)).isEqualTo("4a" + d(0) + d(23) + d(5) + d(1000) + s("x") + s("y"));
    }

    @Test
    void read_shouldReadBackPlayerChat() {
        assertThat(read(written(SHOUT::write).substring(2), CreatureSay::read)).isEqualTo(SHOUT);
    }

    @Test
    void read_shouldReadTheParameters_ofANamedNpcString() {
        final CreatureSay packet = CreatureSay.builder()
                .senderId(7)
                .channel(ChatChannel.NPC_GENERAL)
                .senderName("Guard")
                .messageId(1000)
                .parameters(List.of("x", "y"))
                .build();

        assertThat(read(written(packet::write).substring(2), CreatureSay::read)).isEqualTo(packet);
    }

    @Test
    void read_shouldFail_whenTheChannelIsUnknown() {
        assertThatThrownBy(() -> read(d(0) + d(99) + s("A") + d(-1) + s("b"), CreatureSay::read))
                .isInstanceOf(PacketDecodeException.class);
    }
}
