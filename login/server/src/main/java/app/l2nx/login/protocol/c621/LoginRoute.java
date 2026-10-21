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
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerList;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Mobius
 */
public enum LoginRoute {
    AUTH_GAME_GUARD(
            LoginClientOpcode.AUTH_GAME_GUARD,
            route(AuthGameGuard::decode, AuthGameGuardHandler::handle),
            ConnectionState.CONNECTED),
    REQUEST_AUTH_LOGIN(
            LoginClientOpcode.REQUEST_AUTH_LOGIN,
            route(RequestAuthLogin::decode, RequestAuthLoginHandler::handle),
            ConnectionState.AUTHED_GG),
    REQUEST_SERVER_LOGIN(
            LoginClientOpcode.REQUEST_SERVER_LOGIN,
            route(RequestServerLogin::decode, RequestServerLoginHandler::handle),
            ConnectionState.AUTHED_LOGIN),
    REQUEST_SERVER_LIST(
            LoginClientOpcode.REQUEST_SERVER_LIST,
            route(RequestServerList::decode, RequestServerListHandler::handle),
            ConnectionState.AUTHED_LOGIN),
    // Sent by some High Five clients; the server never offers the agreement screen, so there is nothing to answer.
    REQUEST_PI_AGREEMENT_CHECK(LoginClientOpcode.REQUEST_PI_AGREEMENT_CHECK, null),
    REQUEST_PI_AGREEMENT(LoginClientOpcode.REQUEST_PI_AGREEMENT, null);

    private static final Map<LoginClientOpcode, LoginRoute> BY_OPCODE = new EnumMap<>(LoginClientOpcode.class);

    static {
        for (LoginRoute route : values()) {
            BY_OPCODE.put(route.opcode, route);
        }
    }

    private final LoginClientOpcode opcode;
    private final Function<ByteBuf, Consumer<LoginClient>> decoder;
    private final Set<ConnectionState> accepted;

    LoginRoute(
            LoginClientOpcode opcode, Function<ByteBuf, Consumer<LoginClient>> decoder, ConnectionState... accepted) {
        this.opcode = opcode;
        this.decoder = decoder;
        final EnumSet<ConnectionState> states = EnumSet.noneOf(ConnectionState.class);
        Collections.addAll(states, accepted);
        this.accepted = Collections.unmodifiableSet(states);
    }

    /**
     * @return {@code null} when the login server has no route for the opcode
     */
    public static LoginRoute of(LoginClientOpcode opcode) {
        return BY_OPCODE.get(opcode);
    }

    public LoginClientOpcode opcode() {
        return opcode;
    }

    public Set<ConnectionState> accepted() {
        return accepted;
    }

    /** A known opcode without a handler: the packet is dropped under its own reason, not counted as unknown. */
    public boolean isIgnored() {
        return decoder == null;
    }

    /**
     * Decodes whatever the connection state; the state is checked on the lane ({@link LanePacket}).
     *
     * @throws PacketDecodeException when the body is too short for the record
     */
    public Consumer<LoginClient> decode(ByteBuf body) {
        if (decoder == null) {
            throw new IllegalStateException(name() + " has no handler");
        }
        return decoder.apply(body);
    }

    private static <T> Function<ByteBuf, Consumer<LoginClient>> route(
            Function<ByteBuf, T> decode, BiConsumer<LoginClient, T> handler) {
        return body -> {
            final T packet = decode.apply(body);
            return client -> handler.accept(client, packet);
        };
    }
}
