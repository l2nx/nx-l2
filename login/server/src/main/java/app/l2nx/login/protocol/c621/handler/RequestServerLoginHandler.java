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
package app.l2nx.login.protocol.c621.handler;

import app.l2nx.gslink.v1.GsLinkProto.ListingStatus;
import app.l2nx.login.LoginServer;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.SessionKey;
import app.l2nx.login.network.LoginClient;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import app.l2nx.protocol.login.c621.serverpackets.PlayOk;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.PlayFailReason;

public final class RequestServerLoginHandler {
    private RequestServerLoginHandler() {}

    public static void handle(LoginClient client, RequestServerLogin packet) {
        final SessionKey sk = client.getSessionKey();

        if (!LoginServer.getInstance().getProperties().showLicence()
                || sk.checkLoginPair(packet.loginOk1(), packet.loginOk2())) {
            if ((LoginServer.getInstance().getStatus() == ListingStatus.LISTING_STATUS_DOWN_VALUE)
                    || ((LoginServer.getInstance().getStatus() == ListingStatus.LISTING_STATUS_GM_ONLY_VALUE)
                            && (client.getAccessLevel() < 1))) {
                client.close(LoginFailReason.REASON_ACCESS_FAILED);
            } else if (LoginController.getInstance().isLoginPossible(client, packet.serverId())) {
                client.setJoinedGS(true);
                client.sendPacket(PlayOk.builder()
                        .playOk1(sk.getPlayOkID1())
                        .playOk2(sk.getPlayOkID2())
                        .build());
            } else {
                client.close(PlayFailReason.REASON_SERVER_OVERLOADED);
            }
        } else {
            client.close(LoginFailReason.REASON_ACCESS_FAILED);
        }
    }
}
