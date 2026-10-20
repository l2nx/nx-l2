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
package app.l2nx.login.protocol.c621.clientpackets;

import app.l2nx.login.LoginServer;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.SessionKey;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.enums.PlayFailReason;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.gameserverpackets.receive.ServerStatus;
import app.l2nx.login.protocol.c621.serverpackets.PlayOk;

/**
 * <pre>
 * Format is ddc
 * d: first part of session id
 * d: second part of session id
 * c: server ID
 * </pre>
 */
public class RequestServerLogin extends LoginClientPacket {
    private int skey1;
    private int skey2;
    private int serverId;

    @Override
    protected boolean readImpl() {
        if (remaining() >= 9) {
            skey1 = readInt();
            skey2 = readInt();
            serverId = readByte();
            return true;
        }

        return false;
    }

    @Override
    public void run() {
        final LoginClient client = getClient();
        final SessionKey sk = client.getSessionKey();

        // The login pair is checked only when the license screen was shown.
        if (!LoginServer.getInstance().getProperties().showLicence() || sk.checkLoginPair(skey1, skey2)) {
            if ((LoginServer.getInstance().getStatus() == ServerStatus.STATUS_DOWN)
                    || ((LoginServer.getInstance().getStatus() == ServerStatus.STATUS_GM_ONLY)
                            && (client.getAccessLevel() < 1))) {
                client.close(LoginFailReason.REASON_ACCESS_FAILED);
            } else if (LoginController.getInstance().isLoginPossible(client, serverId)) {
                client.setJoinedGS(true);
                client.sendPacket(new PlayOk(sk));
            } else {
                client.close(PlayFailReason.REASON_SERVER_OVERLOADED);
            }
        } else {
            client.close(LoginFailReason.REASON_ACCESS_FAILED);
        }
    }
}
