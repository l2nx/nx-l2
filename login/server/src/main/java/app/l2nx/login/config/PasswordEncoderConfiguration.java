/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfiguration {
    private static final String ARGON2 = "argon2";

    @Bean
    PasswordEncoder passwordEncoder(LoginProperties properties, MeterRegistry registry) {
        return new BoundedPasswordEncoder(argon2(), properties.password().maxConcurrentHashes(), registry);
    }

    /**
     * Argon2id is the only encoding: an id the map lacks (a SHA-1 hash of the old baseline) fails instead of matching.
     * The {@code {id}} prefix keeps a later parameter or algorithm change an upgrade on login, not a migration.
     */
    static PasswordEncoder argon2() {
        return new DelegatingPasswordEncoder(
                ARGON2, Map.of(ARGON2, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
