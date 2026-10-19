/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

class XorChecksumTest {
    private static final String VECTORS = "/app/l2nx/network/crypt/xor-checksum.csv";
    private static final HexFormat HEX = HexFormat.of();

    @Nested
    class Append {
        @ParameterizedTest(name = "{index}: offset {0}")
        @CsvFileSource(resources = VECTORS)
        void append_shouldMatchGoldenVector(int offset, String input, String output) {
            final byte[] actual = HEX.parseHex(input);

            XorChecksum.append(actual, offset, actual.length - offset);

            assertThat(actual).isEqualTo(HEX.parseHex(output));
        }

        @Test
        void append_shouldWriteSameChecksum_whenRegionStartsPastFirstWord() {
            final byte[] region = HEX.parseHex("0102030405060708090a0b0c00000000");
            final byte[] expected = region.clone();
            XorChecksum.append(expected, 0, expected.length);
            final byte[] actual = new byte[6 + region.length];
            System.arraycopy(region, 0, actual, 6, region.length);

            XorChecksum.append(actual, 6, region.length);

            assertThat(Arrays.copyOfRange(actual, 6, actual.length)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 3, 10})
        void append_shouldReject_whenSizeIsNotPositiveMultipleOfWord(int size) {
            assertThatThrownBy(() -> XorChecksum.append(new byte[16], 0, size))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Verify {
        @ParameterizedTest(name = "{index}: offset {0}")
        @CsvFileSource(resources = VECTORS)
        void verify_shouldAccept_whenChecksumMatches(int offset, String input, String output) {
            final byte[] data = HEX.parseHex(output);

            assertThat(XorChecksum.verify(data, offset, data.length - offset)).isTrue();
        }

        @ParameterizedTest(name = "{index}: offset {0}")
        @CsvFileSource(resources = VECTORS)
        void verify_shouldReject_whenByteIsCorrupted(int offset, String input, String output) {
            final byte[] data = HEX.parseHex(output);
            data[offset] ^= 1;

            assertThat(XorChecksum.verify(data, offset, data.length - offset)).isFalse();
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 4, 6, 10})
        void verify_shouldReject_whenSizeIsNotMultipleOfWordAboveOneWord(int size) {
            assertThat(XorChecksum.verify(new byte[16], 0, size)).isFalse();
        }
    }
}
