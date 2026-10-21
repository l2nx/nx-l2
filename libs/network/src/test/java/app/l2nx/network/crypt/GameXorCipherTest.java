/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameXorCipherTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void encryptAndDecrypt_shouldMatchChainedGoldenVectors() throws IOException {
        final Map<String, GameXorCipher> ciphers = new LinkedHashMap<>();
        for (String[] vector : read()) {
            final GameXorCipher cipher = ciphers.computeIfAbsent(vector[0] + vector[1], k -> keyed(vector[0]));
            final ByteBuf buf =
                    Unpooled.directBuffer(2 + vector[3].length() / 2).writeShortLE(0);
            buf.writeBytes(HEX.parseHex(vector[3]));
            if ("encrypt".equals(vector[1])) {
                cipher.encrypt(buf, 2, buf.readableBytes() - 2);
            } else {
                cipher.decrypt(buf, 2, buf.readableBytes() - 2);
            }
            assertThat(HEX.formatHex(ByteBufUtil.getBytes(buf, 2, buf.readableBytes() - 2)))
                    .as("%s step %s", vector[1], vector[2])
                    .isEqualTo(vector[4]);
            assertThat(buf.getShortLE(0)).as("length bytes untouched").isZero();
            buf.release();
        }
        assertThat(ciphers).hasSize(12);
    }

    private static GameXorCipher keyed(String key) {
        final GameXorCipher cipher = new GameXorCipher();
        cipher.setKey(HEX.parseHex(key));
        return cipher;
    }

    private static List<String[]> read() throws IOException {
        try (InputStream in = GameXorCipherTest.class.getResourceAsStream("/app/l2nx/network/crypt/game-xor.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .filter(line -> !line.startsWith("#") && !line.isBlank())
                    .map(line -> line.split(","))
                    .toList();
        }
    }
}
