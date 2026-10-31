/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.handler.AuthGameGuardHandler;
import app.l2nx.login.protocol.c621.handler.RequestAuthLoginHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerListHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerLoginHandler;
import app.l2nx.login.protocol.c621.model.LoginRoute;
import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoginRoutesTest {
    private final AuthGameGuardHandler authGameGuard = mock(AuthGameGuardHandler.class);
    private final LoginRoutes routes = new LoginRoutes(
            authGameGuard,
            mock(RequestAuthLoginHandler.class),
            mock(RequestServerLoginHandler.class),
            mock(RequestServerListHandler.class));

    @Nested
    class Route {
        @ParameterizedTest
        @EnumSource(LoginClientOpcode.class)
        void route_shouldRouteEveryClientOpcode(LoginClientOpcode opcode) {
            final LoginRoute actual = routes.route(opcode);

            assertThat(actual).isNotNull();
            assertThat(actual.opcode()).isEqualTo(opcode);
        }
    }

    @Nested
    class Decode {
        @ParameterizedTest
        @EnumSource(
                value = LoginClientOpcode.class,
                names = {"REQUEST_PI_AGREEMENT_CHECK", "REQUEST_PI_AGREEMENT"})
        void decode_shouldRefuse_whenTheOpcodeIsIgnored(LoginClientOpcode opcode) {
            final LoginRoute route = routes.route(opcode);
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
                value = LoginClientOpcode.class,
                names = {"REQUEST_PI_AGREEMENT_CHECK", "REQUEST_PI_AGREEMENT"},
                mode = EnumSource.Mode.EXCLUDE)
        void decode_shouldBindAHandler_whenTheOpcodeHasOne(LoginClientOpcode opcode) {
            final LoginRoute route = routes.route(opcode);
            final ByteBuf body = Unpooled.buffer().writeZero(256);
            try {
                assertThat(route.isIgnored()).isFalse();
                assertThat(route.accepted()).isNotEmpty();
                assertThat(route.decode(body)).isNotNull();
            } finally {
                body.release();
            }
        }

        @Test
        void decode_shouldHandThePacketToTheHandlerBean() {
            final LoginClient client = mock(LoginClient.class);
            final ByteBuf body = Unpooled.buffer().writeZero(256);
            try {
                routes.route(LoginClientOpcode.AUTH_GAME_GUARD).decode(body).accept(client);
            } finally {
                body.release();
            }

            verify(authGameGuard).handle(same(client), any(AuthGameGuard.class));
        }
    }
}
