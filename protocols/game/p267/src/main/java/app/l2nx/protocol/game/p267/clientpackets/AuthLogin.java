/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import lombok.Builder;

/** The login as the client typed it and the session key the login server handed out, in wire order. */
@Builder(toBuilder = true)
public record AuthLogin(String login, int playOk2, int playOk1, int loginOk1, int loginOk2) {
    public static final int MAX_LOGIN = 64;

    public AuthLogin {
        Objects.requireNonNull(login, "login");
    }

    public static AuthLogin decode(ByteBuf body) {
        return Bodies.parse(
                "AuthLogin",
                body,
                in -> AuthLogin.builder()
                        .login(Wire.readString(in, MAX_LOGIN))
                        .playOk2(in.readIntLE())
                        .playOk1(in.readIntLE())
                        .loginOk1(in.readIntLE())
                        .loginOk2(in.readIntLE())
                        .build());
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.AUTH_LOGIN.opcode());
        Wire.writeString(out, login);
        out.writeIntLE(playOk2);
        out.writeIntLE(playOk1);
        out.writeIntLE(loginOk1);
        out.writeIntLE(loginOk2);
    }

    /** Never prints the session key. */
    @Override
    public String toString() {
        return "AuthLogin[login=" + login + "]";
    }
}
