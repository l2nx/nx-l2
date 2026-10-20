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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.network.GameClient;

/**
 * @version $Revision: 1.9.2.3.2.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class AuthLogin extends ClientPacket {
    // loginName + keys must match what the loginserver used.
    private String loginName;
    private int playKey1;
    private int playKey2;
    private int loginKey1;
    private int loginKey2;

    @Override
    protected void readImpl() {
        loginName = readString().toLowerCase();
        playKey2 = readInt();
        playKey1 = readInt();
        loginKey1 = readInt();
        loginKey2 = readInt();
    }

    @Override
    protected void runImpl() {
        final GameClient client = getClient();
        if (loginName.isEmpty() || !client.isProtocolOk()) {
            client.closeNow();
            return;
        }

        // Avoid potential exploits.
        if (client.getAccountName() == null) {
            // Preventing duplicate login in case client login server socket was disconnected or this packet was not
            // sent yet.
            if (LoginService.getInstance().reserveLogin(loginName, client)) {
                client.setAccountName(loginName);
                final SessionKey key = SessionKey.builder()
                        .loginOk1(loginKey1)
                        .loginOk2(loginKey2)
                        .playOk1(playKey1)
                        .playOk2(playKey2)
                        .build();

                // client.setSessionId(key);
                LoginService.getInstance().authenticate(loginName, client, key);
            } else {
                client.close(null);
            }
        }
    }
}
