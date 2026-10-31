/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.serverpackets;

import static app.l2nx.network.test.GoldenBytes.bytes;
import static app.l2nx.network.test.GoldenBytes.hex;
import static app.l2nx.network.test.GoldenBytes.pattern;
import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InitTest {
    private static final byte[] MODULUS = pattern(128, 5);
    private static final String KEY = "0102030405060708090a0b0c0d0e0f10";
    private static final String GAME_GUARD = "4e95dd29" + "fc9cc377" + "20b6ad97" + "f7e0bd07";
    private static final String BODY = "0df0ad0b" + "21c60000" + hex(MODULUS) + GAME_GUARD + KEY + "00";
    private static final Init PACKET = Init.builder()
            .sessionId(0x0BADF00D)
            .scrambledModulus(MODULUS)
            .blowfishKey(bytes(KEY))
            .build();

    @Nested
    class Write {
        @Test
        void write_shouldWriteTheSessionTheRevisionTheModulusTheGameGuardWordsAndTheTerminatedKey() {
            final String actual = written(PACKET::write);

            assertThat(actual).isEqualTo("00" + BODY);
            assertThat(actual).hasSize(2 * 170);
        }
    }

    @Nested
    class Read {
        @Test
        void read_shouldReadTheKeyUpToItsTerminator_whenPaddingFollows() {
            final Init actual = read(BODY + "00".repeat(14), Init::read);

            assertThat(actual).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheRevisionIsNotC621() {
            final String body = "0df0ad0b" + "22c60000" + hex(MODULUS) + GAME_GUARD + KEY + "00";

            assertThatThrownBy(() -> read(body, Init::read)).isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void read_shouldFail_whenTheKeyHasNoTerminator() {
            final String body = BODY.substring(0, BODY.length() - 2);

            assertThatThrownBy(() -> read(body, Init::read)).isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void read_shouldFail_whenTheBodyStopsInsideTheModulus() {
            assertThatThrownBy(() -> read(BODY.substring(0, 40), Init::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Value {
        @Test
        void init_shouldKeepItsOwnCopies_whenTheCallerChangesAnArray() {
            final byte[] key = bytes(KEY);
            final Init packet = PACKET.toBuilder().blowfishKey(key).build();

            key[0] = 0x7F;
            packet.blowfishKey()[1] = 0x7F;
            packet.scrambledModulus()[0] = 0x7F;

            assertThat(packet).isEqualTo(PACKET).hasSameHashCodeAs(PACKET);
        }

        @Test
        void init_shouldRefuseAModulusOfAnotherSize() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            PACKET.toBuilder().scrambledModulus(new byte[127]).build());
        }

        @Test
        void init_shouldNameTheMissingModulus() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PACKET.toBuilder().scrambledModulus(null).build())
                    .withMessage("scrambledModulus");
        }

        @Test
        void init_shouldNameTheMissingKey() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PACKET.toBuilder().blowfishKey(null).build())
                    .withMessage("blowfishKey");
        }

        @Test
        void toString_shouldNotPrintTheKeys() {
            assertThat(PACKET.toString()).doesNotContain(KEY).doesNotContain(hex(MODULUS));
        }
    }
}
