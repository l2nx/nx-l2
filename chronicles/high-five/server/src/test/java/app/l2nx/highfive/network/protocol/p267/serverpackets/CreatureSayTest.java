/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessageTest.body;
import static app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessageTest.d;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import org.junit.jupiter.api.Test;

class CreatureSayTest {
    @Test
    void writeImpl_shouldWriteANullParameterAsAnEmptyString() {
        final CreatureSay say = new CreatureSay(ChatType.SHOUT, 7, SystemMessageId.S1_S2);
        say.addStringParameter(null);
        say.addStringParameter("b");

        assertThat(body(say))
                .isEqualTo("4a" + d(0) + d(1) + d(7) + d(SystemMessageId.S1_S2.getId()) + "0000" + "6200" + "0000");
    }
}
