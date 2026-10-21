/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.clientpackets;

import static app.l2nx.protocol.login.c621.GoldenBytes.hex;
import static app.l2nx.protocol.login.c621.GoldenBytes.pattern;
import static app.l2nx.protocol.login.c621.GoldenBytes.read;
import static app.l2nx.protocol.login.c621.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestAuthLoginTest {
    private static final byte[] BLOCK1 = pattern(128, 1);
    private static final byte[] BLOCK2 = pattern(128, 2);

    @Nested
    class Decode {
        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {128, 200, 255})
        void decode_shouldReadOneBlock_whenTheBodyHoldsLessThanTwo(int length) {
            final String body = hex(BLOCK1) + "ff".repeat(length - 128);

            final RequestAuthLogin actual = read(body, RequestAuthLogin::decode);

            assertThat(actual.block1()).isEqualTo(BLOCK1);
            assertThat(actual.block2()).isNull();
        }

        @Test
        void decode_shouldReadTwoBlocks_whenTheBodyHoldsTwo() {
            final RequestAuthLogin actual = read(hex(BLOCK1) + hex(BLOCK2) + "ffff", RequestAuthLogin::decode);

            assertThat(actual.block1()).isEqualTo(BLOCK1);
            assertThat(actual.block2()).isEqualTo(BLOCK2);
        }

        @Test
        void decode_shouldFail_whenTheBodyIsShorterThanABlock() {
            assertThatThrownBy(() -> read("ff".repeat(127), RequestAuthLogin::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Encode {
        @Test
        void encode_shouldWriteTheOpcodeAndOneBlock_whenThereIsNoSecond() {
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(BLOCK1).build();

            assertThat(written(packet::encode)).isEqualTo("00" + hex(BLOCK1));
        }

        @Test
        void encode_shouldWriteTheOpcodeAndBothBlocks() {
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(BLOCK1).block2(BLOCK2).build();

            assertThat(written(packet::encode)).isEqualTo("00" + hex(BLOCK1) + hex(BLOCK2));
        }
    }

    @Nested
    class Value {
        @Test
        void requestAuthLogin_shouldKeepItsOwnCopies_whenTheCallerChangesABlock() {
            final byte[] block1 = BLOCK1.clone();
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(block1).block2(BLOCK2).build();

            block1[0] = 0x7F;
            packet.block2()[0] = 0x7F;

            assertThat(packet)
                    .isEqualTo(RequestAuthLogin.builder()
                            .block1(BLOCK1)
                            .block2(BLOCK2)
                            .build())
                    .hasSameHashCodeAs(RequestAuthLogin.builder()
                            .block1(BLOCK1)
                            .block2(BLOCK2)
                            .build());
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 127, 129})
        void requestAuthLogin_shouldRefuseABlockOfAnotherSize(int length) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            RequestAuthLogin.builder().block1(new byte[length]).build());
        }

        @Test
        void requestAuthLogin_shouldNameTheMissingBlock() {
            assertThatNullPointerException()
                    .isThrownBy(() -> RequestAuthLogin.builder().block2(BLOCK2).build())
                    .withMessage("block1");
        }

        @Test
        void toString_shouldNotPrintTheBlocks() {
            final RequestAuthLogin packet =
                    RequestAuthLogin.builder().block1(BLOCK1).block2(BLOCK2).build();

            assertThat(packet.toString()).isEqualTo("RequestAuthLogin[blocks=2]");
        }
    }
}
