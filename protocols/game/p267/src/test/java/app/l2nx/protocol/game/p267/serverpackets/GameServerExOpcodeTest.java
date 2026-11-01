/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameServerExOpcodeTest {
    @Nested
    class Of {
        @ParameterizedTest
        @EnumSource(GameServerExOpcode.class)
        void of_shouldFindEveryOpcode(GameServerExOpcode opcode) {
            assertThat(GameServerExOpcode.of(opcode.opcode())).isSameAs(opcode);
        }

        @Test
        void of_shouldBeNull_whenTheOpcodeIsNotARecord() {
            assertThat(GameServerExOpcode.of(0xAE)).isNull();
        }
    }

    @Nested
    class Write {
        @Test
        void write_shouldWriteThePrefixThenTheOpcodeAsALittleEndianShort() {
            assertThat(written(GameServerExOpcode.EX_REPLY_POST_ITEM_LIST::write))
                    .isEqualTo("fe" + "b200");
        }
    }
}
