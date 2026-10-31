/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.bytes;
import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Test;

class KeyPacketTest {
    private static final String KEY = "0102030405060708";
    private static final KeyPacket PACKET =
            KeyPacket.builder().accepted(true).key(bytes(KEY)).serverId(3).build();

    @Test
    void write_shouldWriteTheVersionCheckLayout() {
        assertThat(written(PACKET::write)).isEqualTo("2e01" + KEY + "01000000" + "03000000" + "01" + "00000000");
    }

    @Test
    void read_shouldReadBackWhatWriteWrote() {
        assertThat(read(written(PACKET::write).substring(2), KeyPacket::read)).isEqualTo(PACKET);
    }

    @Test
    void constructor_shouldRefuseAKeyThatIsNotItsTravellingHalf() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KeyPacket(true, new byte[16], 0));
    }

    @Test
    void read_shouldFail_whenTheKeyIsShort() {
        assertThatThrownBy(() -> read("01" + "010203", KeyPacket::read)).isInstanceOf(PacketDecodeException.class);
    }
}
