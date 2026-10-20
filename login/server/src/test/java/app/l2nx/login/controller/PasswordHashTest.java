/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHashTest {
    @Test
    void of_shouldGiveTheBase64OfTheSha1Digest() {
        assertThat(PasswordHash.of("password")).isEqualTo("W6ph5Mm5Pz8GgiULbPgzG37mj9g=");
    }
}
