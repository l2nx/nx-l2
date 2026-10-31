/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.handler.AuthGameGuardHandler;
import app.l2nx.login.protocol.c621.handler.RequestAuthLoginHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerListHandler;
import app.l2nx.login.protocol.c621.handler.RequestServerLoginHandler;
import app.l2nx.login.protocol.c621.model.ConnectionState;
import app.l2nx.login.protocol.c621.model.LoginRoute;
import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerList;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public final class LoginRoutes {
    private final Map<LoginClientOpcode, LoginRoute> routes = new EnumMap<>(LoginClientOpcode.class);

    public LoginRoutes(
            AuthGameGuardHandler authGameGuard,
            RequestAuthLoginHandler requestAuthLogin,
            RequestServerLoginHandler requestServerLogin,
            RequestServerListHandler requestServerList) {
        add(
                LoginClientOpcode.AUTH_GAME_GUARD,
                route(AuthGameGuard::decode, authGameGuard::handle),
                ConnectionState.CONNECTED);
        add(
                LoginClientOpcode.REQUEST_AUTH_LOGIN,
                route(RequestAuthLogin::decode, requestAuthLogin::handle),
                ConnectionState.AUTHED_GG);
        add(
                LoginClientOpcode.REQUEST_SERVER_LOGIN,
                route(RequestServerLogin::decode, requestServerLogin::handle),
                ConnectionState.AUTHED_LOGIN);
        add(
                LoginClientOpcode.REQUEST_SERVER_LIST,
                route(RequestServerList::decode, requestServerList::handle),
                ConnectionState.AUTHED_LOGIN);
        // Sent by some High Five clients; the server never offers the agreement screen, so there is nothing to answer.
        add(LoginClientOpcode.REQUEST_PI_AGREEMENT_CHECK, null);
        add(LoginClientOpcode.REQUEST_PI_AGREEMENT, null);
    }

    /**
     * @return {@code null} when the login server has no route for the opcode
     */
    public LoginRoute route(LoginClientOpcode opcode) {
        return routes.get(opcode);
    }

    private void add(
            LoginClientOpcode opcode, Function<ByteBuf, Consumer<LoginClient>> decoder, ConnectionState... accepted) {
        routes.put(
                opcode,
                LoginRoute.builder()
                        .opcode(opcode)
                        .decoder(decoder)
                        .accepted(Set.of(accepted))
                        .build());
    }

    private static <T> Function<ByteBuf, Consumer<LoginClient>> route(
            Function<ByteBuf, T> decode, BiConsumer<LoginClient, T> handler) {
        return body -> {
            final T packet = decode.apply(body);
            return client -> handler.accept(client, packet);
        };
    }
}
