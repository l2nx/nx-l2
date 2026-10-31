/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets.reason;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReasonCodesTest {
    @Nested
    class LoginFailCodes {
        @ParameterizedTest
        @EnumSource(LoginFailReason.class)
        void of_shouldFindEveryReasonByItsCode(LoginFailReason reason) {
            assertThat(LoginFailReason.of(reason.code())).isSameAs(reason);
        }
    }

    @Nested
    class CharCreateFailCodes {
        @ParameterizedTest
        @EnumSource(CharCreateFailReason.class)
        void of_shouldFindEveryReasonByItsCode(CharCreateFailReason reason) {
            assertThat(CharCreateFailReason.of(reason.code())).isSameAs(reason);
        }
    }

    @Nested
    class CharDeleteFailCodes {
        @ParameterizedTest
        @EnumSource(CharDeleteFailReason.class)
        void of_shouldFindEveryReasonByItsCode(CharDeleteFailReason reason) {
            assertThat(CharDeleteFailReason.of(reason.code())).isSameAs(reason);
        }
    }

    @Nested
    class Code {
        @Test
        void code_shouldBeTheLegacyConstants() {
            assertThat(LoginFailReason.SYSTEM_ERROR_LOGIN_LATER.code()).isEqualTo(1);
            assertThat(LoginFailReason.ACCESS_FAILED_TRY_LATER5.code()).isEqualTo(10);
            assertThat(CharCreateFailReason.REASON_CHOOSE_ANOTHER_SVR.code()).isEqualTo(6);
            assertThat(CharDeleteFailReason.REASON_DELETION_FAILED.code()).isEqualTo(1);
        }
    }
}
