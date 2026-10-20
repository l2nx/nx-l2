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
package app.l2nx.login.protocol.c621.clientpackets;

import app.l2nx.login.LoginServer;
import app.l2nx.login.controller.AccountInfo;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.enums.AccountKickedReason;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.ConnectionState;
import app.l2nx.login.protocol.c621.serverpackets.AccountKicked;
import app.l2nx.login.protocol.c621.serverpackets.LoginOk;
import app.l2nx.login.protocol.c621.serverpackets.ServerList;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import lombok.extern.slf4j.Slf4j;

/**
 * Format: x 0 (a leading null) x: the rsa encrypted block with the login an password.
 */
@Slf4j
public class RequestAuthLogin extends LoginClientPacket {

    private final byte[] raw1 = new byte[128];
    private final byte[] raw2 = new byte[128];
    private boolean newAuthMethod = false;

    @Override
    protected boolean readImpl() {
        if (remaining() >= 256) {
            newAuthMethod = true;
            readBytes(raw1);
            readBytes(raw2);
            return true;
        } else if (remaining() >= 128) {
            readBytes(raw1);
            return true;
        }

        return false;
    }

    @Override
    public void run() {
        final LoginClient client = getClient();
        final byte[] decrypted = new byte[newAuthMethod ? 256 : 128];
        try {
            final Cipher rsaCipher = Cipher.getInstance("RSA/ECB/nopadding");
            rsaCipher.init(Cipher.DECRYPT_MODE, client.getRSAPrivateKey());
            rsaCipher.doFinal(raw1, 0, 128, decrypted, 0);
            if (newAuthMethod) {
                rsaCipher.doFinal(raw2, 0, 128, decrypted, 128);
            }
        } catch (GeneralSecurityException e) {
            log.info("", e);
            return;
        }

        final String user;
        final String password;
        try {
            if (newAuthMethod) {
                user = new String(decrypted, 0x4E, 50).trim() + new String(decrypted, 0xCE, 14).trim();
                password = new String(decrypted, 0xDC, 16).trim();
            } else {
                user = new String(decrypted, 0x5E, 14).trim();
                password = new String(decrypted, 0x6C, 16).trim();
            }
        } catch (Exception e) {
            log.warn("run: ", e);
            return;
        }

        final String clientAddr = client.getIp();
        final LoginController lc = LoginController.getInstance();
        final AccountInfo info = lc.retriveAccountInfo(clientAddr, user, password);
        if (info == null) {
            // Account or password was wrong.
            client.close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
            return;
        }

        switch (lc.tryCheckinAccount(client, clientAddr, info)) {
            case AUTH_SUCCESS: {
                client.setAccount(info.getLogin());
                client.setAccountId(info.getId());
                client.setConnectionState(ConnectionState.AUTHED_LOGIN);
                client.setSessionKey(lc.assignSessionKeyToClient(info.getId(), client));
                lc.getCharactersOnAccount(info.getId());
                if (LoginServer.getInstance().getProperties().showLicence()) {
                    client.sendPacket(new LoginOk(client.getSessionKey()));
                } else {
                    client.sendPacket(new ServerList(client));
                }
                break;
            }
            case INVALID_PASSWORD: {
                client.close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
                break;
            }
            case ACCOUNT_BANNED: {
                client.close(new AccountKicked(AccountKickedReason.REASON_PERMANENTLY_BANNED));
                return;
            }
            case ALREADY_ON_LS: {
                final LoginClient oldClient = lc.getAuthedClient(info.getId());
                if (oldClient != null) {
                    // Kick the other client.
                    oldClient.close(LoginFailReason.REASON_ACCOUNT_IN_USE);
                    lc.removeAuthedLoginClient(info.getId());
                }

                // Also kick current client.
                client.close(LoginFailReason.REASON_ACCOUNT_IN_USE);
                break;
            }
            case ALREADY_ON_GS: {
                final GameServerInfo gsi = lc.getAccountOnGameServer(info.getId());
                if (gsi != null) {
                    client.close(LoginFailReason.REASON_ACCOUNT_IN_USE);
                    if (gsi.isAuthed()) {
                        gsi.getGameServerThread().kickPlayer(info.getId());
                    }
                }
                break;
            }
        }
    }
}
