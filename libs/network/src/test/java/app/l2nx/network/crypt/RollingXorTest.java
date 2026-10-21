/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class RollingXorTest {
    private static final HexFormat HEX = HexFormat.of();

    @Nested
    class Encrypt {
        @ParameterizedTest(name = "{index}: offset {0}, key {1}")
        @CsvFileSource(resources = "/app/l2nx/network/crypt/rolling-xor.csv")
        void encrypt_shouldMatchGoldenVector(int offset, int key, String input, String output) {
            final byte[] actual = HEX.parseHex(input);

            RollingXor.encrypt(actual, offset, actual.length - offset, key);

            assertThat(actual).isEqualTo(HEX.parseHex(output));
        }

        @Test
        void encrypt_shouldProduceSameRegion_whenRegionStartsPastFirstWord() {
            final byte[] region = HEX.parseHex("0102030405060708090a0b0c0d0e0f101112131400000000");
            final byte[] expected = region.clone();
            RollingXor.encrypt(expected, 0, expected.length, 0x1234ABCD);
            final byte[] actual = new byte[8 + region.length];
            System.arraycopy(region, 0, actual, 8, region.length);

            RollingXor.encrypt(actual, 8, region.length, 0x1234ABCD);

            assertThat(Arrays.copyOfRange(actual, 8, actual.length)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 8, 14})
        void encrypt_shouldReject_whenSizeIsNotMultipleOfWordOfThreeWords(int size) {
            assertThatThrownBy(() -> RollingXor.encrypt(new byte[16], 0, size, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Decrypt {
        @ParameterizedTest(name = "{index}: offset {0}, key {1}")
        @CsvFileSource(resources = "/app/l2nx/network/crypt/rolling-xor.csv")
        void decrypt_shouldRestoreThePlainWords_ofTheGoldenVector(int offset, int key, String input, String output) {
            final byte[] actual = HEX.parseHex(output);

            RollingXor.decrypt(actual, offset, actual.length - offset);

            assertThat(actual).isEqualTo(withKeyWord(HEX.parseHex(input), HEX.parseHex(output)));
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 8, 14})
        void decrypt_shouldReject_whenSizeIsNotMultipleOfWordOfThreeWords(int size) {
            assertThatThrownBy(() -> RollingXor.decrypt(new byte[16], 0, size))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @ParameterizedClass(name = "{0}")
    @EnumSource(BufferKind.class)
    class ByteBufOverloads {
        @Parameter
        BufferKind kind;

        @ParameterizedTest(name = "{index}: offset {0}, key {1}")
        @CsvFileSource(resources = "/app/l2nx/network/crypt/rolling-xor.csv")
        void encrypt_shouldMatchGoldenVectorInPlace(int offset, int key, String input, String output) {
            final ByteBuf buf = kind.wrap(HEX.parseHex(input));
            try {
                RollingXor.encrypt(buf, BufferKind.PREFIX + offset, BufferKind.size(buf) - offset, key);

                assertThat(BufferKind.region(buf)).isEqualTo(HEX.parseHex(output));
            } finally {
                buf.release();
            }
        }

        @ParameterizedTest(name = "{index}: offset {0}, key {1}")
        @CsvFileSource(resources = "/app/l2nx/network/crypt/rolling-xor.csv")
        void decrypt_shouldRestoreThePlainWordsInPlace(int offset, int key, String input, String output) {
            final ByteBuf buf = kind.wrap(HEX.parseHex(output));
            try {
                RollingXor.decrypt(buf, BufferKind.PREFIX + offset, BufferKind.size(buf) - offset);

                assertThat(BufferKind.region(buf)).isEqualTo(withKeyWord(HEX.parseHex(input), HEX.parseHex(output)));
            } finally {
                buf.release();
            }
        }
    }

    private static byte[] withKeyWord(byte[] plain, byte[] encrypted) {
        final int keyIndex = plain.length - 8;
        System.arraycopy(encrypted, keyIndex, plain, keyIndex, 4);
        return plain;
    }
}
