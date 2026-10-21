/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.HexFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ScrambledModulusTest {
    // 128 bytes 0x80, 0x01, 0x02, ... 0x7f: the top bit set, as in every 1024-bit modulus.
    private static final BigInteger MODULUS = new BigInteger(1, sequence());
    // Derived from the scrambling the login server always used.
    private static final String SCRAMBLED = "0d0f0d13404040404040404040cd4f4d53404040404040404040404040404040"
            + "4040404040404040404040404040404040404040404040404040404040404040"
            + "4d4e4f500405060708090a0b0c4d4e4f501112131415161718191a1b1c1d1e1f"
            + "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f";

    @Nested
    class Scramble {
        @Test
        void scramble_shouldSwapAndXorTheHalves() {
            final byte[] actual = ScrambledModulus.scramble(MODULUS);

            assertThat(HexFormat.of().formatHex(actual)).isEqualTo(SCRAMBLED);
        }

        @Test
        void scramble_shouldDropTheSignByte_ofTheModulus() {
            assertThat(MODULUS.toByteArray()).hasSize(129);

            assertThat(ScrambledModulus.scramble(MODULUS)).hasSize(128);
        }
    }

    @Nested
    class Unscramble {
        @Test
        void unscramble_shouldRestoreTheModulus() {
            assertThat(ScrambledModulus.unscramble(ScrambledModulus.scramble(MODULUS)))
                    .isEqualTo(MODULUS);
        }

        @RepeatedTest(3)
        void unscramble_shouldRestoreTheModulus_ofAGeneratedKey() throws Exception {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
            final RSAPublicKey key = (RSAPublicKey) generator.generateKeyPair().getPublic();

            final RSAPublicKey actual = ScrambledModulus.publicKey(ScrambledModulus.scramble(key.getModulus()));

            assertThat(actual).isEqualTo(key);
        }

        @Test
        void unscramble_shouldRefuse_whenTheLengthIsNot128() {
            assertThatIllegalArgumentException().isThrownBy(() -> ScrambledModulus.unscramble(new byte[127]));
        }
    }

    private static byte[] sequence() {
        final byte[] bytes = new byte[128];
        bytes[0] = (byte) 0x80;
        for (int i = 1; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }
}
