/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.login.view.AuthenticationFailed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import app.l2nx.protocol.game.p267.serverpackets.LoginFail;
import app.l2nx.protocol.game.p267.serverpackets.reason.LoginFailReason;

/** The login failure "system error, log in later", the only reason the game server sends. */
public final class AuthenticationFailedMapper implements OutboundMapper<AuthenticationFailed> {
    private static final LoginFail SYSTEM_ERROR_LOGIN_LATER = new LoginFail(LoginFailReason.SYSTEM_ERROR_LOGIN_LATER);

    @Override
    public Class<AuthenticationFailed> eventType() {
        return AuthenticationFailed.class;
    }

    @Override
    public GameServerPacket map(AuthenticationFailed event, GameClient viewer) {
        return SYSTEM_ERROR_LOGIN_LATER;
    }

    @Override
    public int sizeHint() {
        return 5;
    }
}
