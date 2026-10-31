/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameServerOpcodeTest {
    @ParameterizedTest
    @EnumSource(GameServerOpcode.class)
    void of_shouldFindEveryOpcode(GameServerOpcode opcode) {
        assertThat(GameServerOpcode.of(opcode.opcode())).isSameAs(opcode);
    }
}
