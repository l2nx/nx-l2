/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.serverpackets.reason;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReasonCodesTest {
    @Nested
    class LoginFail {
        @ParameterizedTest
        @EnumSource(LoginFailReason.class)
        void of_shouldFindTheReason(LoginFailReason reason) {
            assertThat(LoginFailReason.of(reason.code())).isEqualTo(reason);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {-1, 0x03, 0x39, Integer.MAX_VALUE})
        void of_shouldBeNull_whenTheCodeIsUnknown(int code) {
            assertThat(LoginFailReason.of(code)).isNull();
        }
    }

    @Nested
    class PlayFail {
        @ParameterizedTest
        @EnumSource(PlayFailReason.class)
        void of_shouldFindTheReason(PlayFailReason reason) {
            assertThat(PlayFailReason.of(reason.code())).isEqualTo(reason);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {-1, 0x06, 0x39, Integer.MAX_VALUE})
        void of_shouldBeNull_whenTheCodeIsUnknown(int code) {
            assertThat(PlayFailReason.of(code)).isNull();
        }
    }

    @Nested
    class AccountKicked {
        @ParameterizedTest
        @EnumSource(AccountKickedReason.class)
        void of_shouldFindTheReason(AccountKickedReason reason) {
            assertThat(AccountKickedReason.of(reason.code())).isEqualTo(reason);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {Integer.MIN_VALUE, 0x00, 0x21, Integer.MAX_VALUE})
        void of_shouldBeNull_whenTheCodeIsUnknown(int code) {
            assertThat(AccountKickedReason.of(code)).isNull();
        }
    }
}
