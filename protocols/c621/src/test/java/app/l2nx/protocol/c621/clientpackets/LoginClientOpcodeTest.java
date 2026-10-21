/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginClientOpcodeTest {
    @Nested
    class Of {
        @ParameterizedTest(name = "0x{0}")
        @CsvSource({
            "00, REQUEST_AUTH_LOGIN",
            "02, REQUEST_SERVER_LOGIN",
            "05, REQUEST_SERVER_LIST",
            "07, AUTH_GAME_GUARD",
            "0E, REQUEST_PI_AGREEMENT_CHECK",
            "0F, REQUEST_PI_AGREEMENT"
        })
        void of_shouldFindTheOpcode(String opcode, LoginClientOpcode expected) {
            assertThat(LoginClientOpcode.of(Integer.parseInt(opcode, 16))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {-1, 0x01, 0x10, 0xFF})
        void of_shouldBeNull_whenTheOpcodeIsUnknown(int opcode) {
            assertThat(LoginClientOpcode.of(opcode)).isNull();
        }
    }
}
