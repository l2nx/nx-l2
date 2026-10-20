/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LittleEndianBlowfishCipherTest {
    private static final String VECTORS = "/app/l2nx/network/crypt/blowfish-le.csv";
    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] KEY = HEX.parseHex("6b60cb5b82ce90b1cc2b6c556c6c6c6c");

    @Nested
    class Constructor {
        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 57, 72})
        void constructor_shouldRejectKey_whenLengthIsOutsideJceRange(int length) {
            assertThatThrownBy(() -> new LittleEndianBlowfishCipher(new byte[length]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Encrypt {
        @ParameterizedTest(name = "{index}: key {0}")
        @CsvFileSource(resources = VECTORS)
        void encrypt_shouldMatchGoldenVector(String key, String plain, String cipher) {
            final byte[] actual = HEX.parseHex(plain);

            new LittleEndianBlowfishCipher(HEX.parseHex(key)).encrypt(actual, 0, actual.length);

            assertThat(actual).isEqualTo(HEX.parseHex(cipher));
        }

        @Test
        void encrypt_shouldMatchGoldenVectors_whenSharedAcrossThreads() throws Exception {
            final LittleEndianBlowfishCipher shared = new LittleEndianBlowfishCipher(KEY);
            final List<String[]> vectors = readVectors().stream()
                    .filter(vector -> vector[0].equals(HEX.formatHex(KEY)))
                    .toList();
            final List<Callable<Boolean>> tasks = IntStream.range(0, 2_000)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        final String[] vector = vectors.get(i % vectors.size());
                        final byte[] data = HEX.parseHex(vector[1]);
                        shared.encrypt(data, 0, data.length);
                        return Arrays.equals(data, HEX.parseHex(vector[2]));
                    })
                    .toList();

            try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
                for (Future<Boolean> actual : executor.invokeAll(tasks)) {
                    assertThat(actual.get()).isTrue();
                }
            }
        }

        @Test
        void encrypt_shouldLeaveBytesOutsideRegionUntouched() {
            final LittleEndianBlowfishCipher cipher = new LittleEndianBlowfishCipher(KEY);
            final byte[] block = new byte[16];
            Arrays.fill(block, (byte) 0x5A);
            final byte[] expected = block.clone();
            cipher.encrypt(expected, 0, expected.length);
            final byte[] actual = new byte[2 + block.length + 3];
            Arrays.fill(actual, (byte) 0x7F);
            System.arraycopy(block, 0, actual, 2, block.length);

            cipher.encrypt(actual, 2, block.length);

            assertThat(Arrays.copyOfRange(actual, 2, 2 + block.length)).isEqualTo(expected);
            assertThat(Arrays.copyOfRange(actual, 0, 2)).containsOnly(0x7F);
            assertThat(Arrays.copyOfRange(actual, 2 + block.length, actual.length))
                    .containsOnly(0x7F);
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {4, 12, 15})
        void encrypt_shouldReject_whenSizeIsNotBlockAligned(int size) {
            final LittleEndianBlowfishCipher cipher = new LittleEndianBlowfishCipher(KEY);

            assertThatThrownBy(() -> cipher.encrypt(new byte[16], 0, size))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Decrypt {
        @ParameterizedTest(name = "{index}: key {0}")
        @CsvFileSource(resources = VECTORS)
        void decrypt_shouldMatchGoldenVector(String key, String plain, String cipher) {
            final byte[] actual = HEX.parseHex(cipher);

            new LittleEndianBlowfishCipher(HEX.parseHex(key)).decrypt(actual, 0, actual.length);

            assertThat(actual).isEqualTo(HEX.parseHex(plain));
        }
    }

    private static List<String[]> readVectors() throws IOException {
        try (InputStream in = LittleEndianBlowfishCipherTest.class.getResourceAsStream(VECTORS)) {
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .filter(line -> !line.startsWith("#"))
                    .map(line -> line.split(","))
                    .toList();
        }
    }

    @Nested
    @ParameterizedClass(name = "{0}")
    @EnumSource(BufferKind.class)
    class ByteBufOverloads {
        @Parameter
        BufferKind kind;

        @ParameterizedTest(name = "{index}: key {0}")
        @CsvFileSource(resources = VECTORS)
        void encrypt_shouldMatchGoldenVectorAndDecryptBackInPlace(String key, String plain, String cipher) {
            final LittleEndianBlowfishCipher bf = new LittleEndianBlowfishCipher(HEX.parseHex(key));
            final ByteBuf buf = kind.wrap(HEX.parseHex(plain));
            try {
                bf.encrypt(buf, BufferKind.PREFIX, BufferKind.size(buf));
                assertThat(BufferKind.region(buf)).isEqualTo(HEX.parseHex(cipher));

                bf.decrypt(buf, BufferKind.PREFIX, BufferKind.size(buf));
                assertThat(BufferKind.region(buf)).isEqualTo(HEX.parseHex(plain));
                assertThat(buf.readerIndex()).isZero();
            } finally {
                buf.release();
            }
        }
    }
}
