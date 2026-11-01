/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameClientExOpcodeTest {
    @Nested
    class Of {
        @ParameterizedTest
        @EnumSource(GameClientExOpcode.class)
        void of_shouldFindEveryOpcode(GameClientExOpcode opcode) {
            assertThat(GameClientExOpcode.of(opcode.opcode())).isSameAs(opcode);
        }

        @Test
        void of_shouldBeNull_whenTheOpcodeIsNotARecord() {
            assertThat(GameClientExOpcode.of(0x64)).isNull();
        }
    }

    @Nested
    class Write {
        @Test
        void write_shouldWriteThePrefixThenTheOpcodeAsALittleEndianShort() {
            assertThat(written(GameClientExOpcode.REQUEST_CANCEL_POST_ATTACHMENT::write))
                    .isEqualTo("d0" + "6f00");
        }
    }
}
