/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValidateLocationTest {
    private static final ValidateLocation PACKET = ValidateLocation.builder()
            .objectId(0x10000001)
            .x(1)
            .y(-2)
            .z(3)
            .heading(4)
            .build();

    @Nested
    class Write {
        @Test
        void write_shouldWriteTheObjectAndItsLocation() {
            assertThat(written(PACKET::write))
                    .isEqualTo("79" + "01000010" + "01000000" + "feffffff" + "03000000" + "04000000");
        }
    }

    @Nested
    class Read {
        @Test
        void read_shouldReadBackWhatWriteWrote() {
            assertThat(read(written(PACKET::write).substring(2), ValidateLocation::read))
                    .isEqualTo(PACKET);
        }
    }
}
