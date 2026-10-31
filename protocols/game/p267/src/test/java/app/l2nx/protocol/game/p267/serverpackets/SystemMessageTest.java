/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.q;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemMessageTest {
    private static final SystemMessage EVERY_KIND = new SystemMessage(
            2000,
            List.of(
                    new SystemMessageParam.Text("t"),
                    new SystemMessageParam.IntNumber(1),
                    new SystemMessageParam.NpcName(2),
                    new SystemMessageParam.ItemName(3),
                    new SystemMessageParam.SkillName(4, 5),
                    new SystemMessageParam.CastleName(6),
                    new SystemMessageParam.LongNumber(7L),
                    new SystemMessageParam.ZoneName(8, 9, 10),
                    new SystemMessageParam.ElementName(11),
                    new SystemMessageParam.InstanceName(12),
                    new SystemMessageParam.DoorName(13),
                    new SystemMessageParam.PlayerName("p"),
                    new SystemMessageParam.SystemString(14)));

    @Test
    void write_shouldWriteTheIdAndNoParameters() {
        assertThat(written(new SystemMessage(1234, List.of())::write)).isEqualTo("62" + d(1234) + d(0));
    }

    @Test
    void write_shouldWriteEachParameterAsItsTypeAndValue() {
        assertThat(written(EVERY_KIND::write))
                .isEqualTo("62" + d(2000) + d(13)
                        + d(0) + s("t")
                        + d(1) + d(1)
                        + d(2) + d(2)
                        + d(3) + d(3)
                        + d(4) + d(4) + d(5)
                        + d(5) + d(6)
                        + d(6) + q(7L)
                        + d(7) + d(8) + d(9) + d(10)
                        + d(9) + d(11)
                        + d(10) + d(12)
                        + d(11) + d(13)
                        + d(12) + s("p")
                        + d(13) + d(14));
    }

    @Test
    void read_shouldReadBackWhatWriteWrote() {
        assertThat(read(written(EVERY_KIND::write).substring(2), SystemMessage::read))
                .isEqualTo(EVERY_KIND);
    }

    @Test
    void read_shouldFail_whenAParameterTypeIsUnknown() {
        assertThatThrownBy(() -> read(d(1) + d(1) + d(8) + d(0), SystemMessage::read))
                .isInstanceOf(PacketDecodeException.class);
    }

    @Test
    void read_shouldFail_whenAParameterIsMissing() {
        assertThatThrownBy(() -> read(d(1) + d(2) + d(1) + d(5), SystemMessage::read))
                .isInstanceOf(PacketDecodeException.class);
    }
}
