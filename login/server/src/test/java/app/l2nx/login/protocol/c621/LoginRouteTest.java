/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.protocol.c621.clientpackets.LoginClientOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoginRouteTest {
    @Nested
    class Of {
        @ParameterizedTest
        @EnumSource(LoginClientOpcode.class)
        void of_shouldRouteEveryClientOpcode(LoginClientOpcode opcode) {
            final LoginRoute actual = LoginRoute.of(opcode);

            assertThat(actual).isNotNull();
            assertThat(actual.opcode()).isEqualTo(opcode);
        }
    }

    @Nested
    class Decode {
        @ParameterizedTest
        @EnumSource(
                value = LoginRoute.class,
                names = {"REQUEST_PI_AGREEMENT_CHECK", "REQUEST_PI_AGREEMENT"})
        void decode_shouldRefuse_whenTheOpcodeIsIgnored(LoginRoute route) {
            final ByteBuf body = Unpooled.buffer().writeZero(256);
            try {
                assertThat(route.isIgnored()).isTrue();
                assertThatIllegalStateException().isThrownBy(() -> route.decode(body));
            } finally {
                body.release();
            }
        }

        @ParameterizedTest
        @EnumSource(
                value = LoginRoute.class,
                names = {"REQUEST_PI_AGREEMENT_CHECK", "REQUEST_PI_AGREEMENT"},
                mode = EnumSource.Mode.EXCLUDE)
        void decode_shouldBindAHandler_whenTheOpcodeHasOne(LoginRoute route) {
            final ByteBuf body = Unpooled.buffer().writeZero(256);
            try {
                assertThat(route.isIgnored()).isFalse();
                assertThat(route.accepted()).isNotEmpty();
                assertThat(route.decode(body)).isNotNull();
            } finally {
                body.release();
            }
        }
    }
}
