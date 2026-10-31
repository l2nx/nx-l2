/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoginKeysTest {
    @Nested
    class NewBlowfishKey {
        @Test
        void newBlowfishKey_shouldHaveNoZeroByte_whenTheRandomDrawsItsLowestValues() {
            final byte[] actual = LoginKeys.newBlowfishKey(() -> 0L);

            assertThat(actual).hasSize(16).containsOnly(1);
        }
    }
}
