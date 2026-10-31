/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.f;
import static app.l2nx.protocol.game.p267.WireHex.q;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static app.l2nx.protocol.game.p267.WireHex.zeros;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharSelectedTest {
    private static final CharSelected PACKET = CharSelected.builder()
            .name("Alice")
            .objectId(0x10000001)
            .title("Knight")
            .playOk1(7)
            .clanId(0)
            .female(true)
            .race(0)
            .classId(0)
            .x(-71338)
            .y(258271)
            .z(-3104)
            .currentHp(80.5)
            .currentMp(30.25)
            .sp(1234)
            .exp(5000L)
            .level(20)
            .karma(0)
            .pkKills(1)
            .intelligence(21)
            .strength(40)
            .constitution(43)
            .mentality(25)
            .dexterity(30)
            .wit(11)
            .gameMinute(615)
            .build();

    @Nested
    class Write {
        @Test
        void write_shouldWriteTheLegacyLayout() {
            assertThat(written(PACKET::write))
                    .isEqualTo("0b" + s("Alice") + d(0x10000001) + s("Knight") + d(7) + d(0) + d(0) + d(1) + d(0) + d(0)
                            + d(1) + d(-71338) + d(258271) + d(-3104) + f(80.5) + f(30.25) + d(1234) + q(5000L) + d(20)
                            + d(0) + d(1) + d(21) + d(40) + d(43) + d(25) + d(30) + d(11) + d(615) + d(0) + d(0)
                            + zeros(16) + zeros(64) + d(0));
        }
    }

    @Nested
    class Read {
        @Test
        void read_shouldReadBackWhatWriteWrote() {
            assertThat(read(written(PACKET::write).substring(2), CharSelected::read))
                    .isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheTailIsCut() {
            final String body = written(PACKET::write).substring(2);

            assertThatThrownBy(() -> read(body.substring(0, body.length() - 8), CharSelected::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }
}
