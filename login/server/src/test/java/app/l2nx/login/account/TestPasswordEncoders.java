/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.account;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The production encoder's shape at a cost of kilobytes, not 16 MiB, per hash. */
public final class TestPasswordEncoders {
    private static final String ARGON2 = "argon2";

    private TestPasswordEncoders() {}

    public static PasswordEncoder argon2() {
        return new DelegatingPasswordEncoder(ARGON2, Map.of(ARGON2, new Argon2PasswordEncoder(16, 32, 1, 16, 1)));
    }

    public static String weakArgon2Hash(String raw) {
        return "{" + ARGON2 + "}" + new Argon2PasswordEncoder(16, 32, 1, 8, 1).encode(raw);
    }
}
