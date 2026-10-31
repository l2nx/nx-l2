/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import app.l2nx.highfive.login.command.Authenticate;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.session.SessionKey;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.clientpackets.AuthLogin;
import io.netty.buffer.ByteBuf;
import java.util.Locale;

public final class LoginDecoders {
    private LoginDecoders() {}

    public static Object authLogin(ByteBuf frame, GameConnection connection) {
        final AuthLogin packet = AuthLogin.decode(frame);
        final String login = packet.login().toLowerCase(Locale.ROOT);
        if (login.isBlank()) {
            throw new PacketDecodeException("AuthLogin without a login");
        }
        return Authenticate.builder()
                .login(login)
                .key(SessionKey.builder()
                        .loginOk1(packet.loginOk1())
                        .loginOk2(packet.loginOk2())
                        .playOk1(packet.playOk1())
                        .playOk2(packet.playOk2())
                        .build())
                .build();
    }
}
