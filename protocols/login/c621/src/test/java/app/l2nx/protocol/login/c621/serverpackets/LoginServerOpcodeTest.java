/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginServerOpcodeTest {
    @Nested
    class Of {
        @ParameterizedTest
        @EnumSource(LoginServerOpcode.class)
        void of_shouldFindEveryOpcode(LoginServerOpcode opcode) {
            assertThat(LoginServerOpcode.of(opcode.opcode())).isSameAs(opcode);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {-1, 0x05, 0x0C, 0xFF})
        void of_shouldBeNull_whenTheOpcodeIsUnknown(int opcode) {
            assertThat(LoginServerOpcode.of(opcode)).isNull();
        }
    }
}
