/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigurationTest {
    private static final PasswordEncoder ENCODER = new PasswordEncoderConfiguration().passwordEncoder();

    @Nested
    class Encode {
        @Test
        void encode_shouldWriteArgon2idWithTheSpringSecurityDefaults() {
            final String actual = ENCODER.encode("secret");

            assertThat(actual).startsWith("{argon2}$argon2id$v=19$m=16384,t=2,p=1$");
        }
    }

    @Nested
    class Matches {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"{sha1}5en6G6MezRroT3XKqkdPOmY/BfQ=", "5en6G6MezRroT3XKqkdPOmY/BfQ="})
        void matches_shouldRefuse_whenTheHashIsNotArgon2(String hash) {
            assertThatThrownBy(() -> ENCODER.matches("secret", hash)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class UpgradeEncoding {
        @Test
        void upgradeEncoding_shouldBeTrue_whenTheHashTookLessMemory() {
            assertThat(ENCODER.upgradeEncoding(TestPasswordEncoders.weakArgon2Hash("secret")))
                    .isTrue();
        }
    }
}
