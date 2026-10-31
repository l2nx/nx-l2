/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.session.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionKeyTest {
    @Nested
    class Matches {
        @ParameterizedTest(name = "playOk {0}, loginOk {1}, checkLoginPair {2} -> {3}")
        @CsvSource({
            "true,  true,  true,  true",
            "true,  false, true,  false",
            "true,  false, false, true",
            "false, true,  false, false",
            "false, true,  true,  false"
        })
        void matches_shouldCompareTheLoginPairOnlyWhenAsked(
                boolean playOkEqual, boolean loginOkEqual, boolean checkLoginPair, boolean expected) {
            final SessionKey issued = key(1, 2, 3, 4);
            final SessionKey presented = key(loginOkEqual ? 1 : 9, 2, playOkEqual ? 3 : 9, 4);

            assertThat(issued.matches(presented, checkLoginPair)).isEqualTo(expected);
        }
    }

    @Nested
    class ToString {
        @Test
        void toString_shouldNotPrintTheKey() {
            assertThat(key(11, 22, 33, 44).toString()).doesNotContain("11", "22", "33", "44");
        }
    }

    private static SessionKey key(int loginOk1, int loginOk2, int playOk1, int playOk2) {
        return SessionKey.builder()
                .loginOk1(loginOk1)
                .loginOk2(loginOk2)
                .playOk1(playOk1)
                .playOk2(playOk2)
                .build();
    }
}
