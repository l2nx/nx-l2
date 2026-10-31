/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameClientOpcodeTest {
    @ParameterizedTest
    @EnumSource(GameClientOpcode.class)
    void of_shouldFindEveryOpcode(GameClientOpcode opcode) {
        assertThat(GameClientOpcode.of(opcode.opcode())).isSameAs(opcode);
    }

    @Test
    void of_shouldBeNull_whenTheOpcodeIsNotARecord() {
        assertThat(GameClientOpcode.of(0x14)).isNull();
    }
}
