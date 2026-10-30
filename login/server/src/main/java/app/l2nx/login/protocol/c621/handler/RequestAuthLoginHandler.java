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

import app.l2nx.login.LoginServer;
import app.l2nx.login.account.AccountInfo;
import app.l2nx.login.gameserver.GameServerTable.GameServerInfo;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.ConnectionState;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.LoginResult;
import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.crypt.Credentials;
import app.l2nx.protocol.login.c621.serverpackets.AccountKicked;
import app.l2nx.protocol.login.c621.serverpackets.LoginOk;
import app.l2nx.protocol.login.c621.serverpackets.reason.AccountKickedReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import java.security.GeneralSecurityException;
import java.security.Key;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RequestAuthLoginHandler {
    private RequestAuthLoginHandler() {}

    public static void warmUp(Key privateKey) throws GeneralSecurityException {
        Cipher.getInstance(Credentials.TRANSFORMATION).init(Cipher.DECRYPT_MODE, privateKey);
    }

    public static void handle(LoginClient client, RequestAuthLogin packet) {
        final Credentials credentials;
        try {
            credentials = Credentials.decrypt(client.getRSAPrivateKey(), packet);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            // A block the key cannot decrypt (for example one larger than the modulus) is never sent by a real client.
            log.debug("Credentials block from {} cannot be decrypted: {}", client.getIp(), e.toString());
            client.close(LoginFailReason.REASON_ACCESS_FAILED);
            return;
        } catch (GeneralSecurityException e) {
            log.error("RSA cipher is unavailable, closing {}", client.getIp(), e);
            client.close(LoginFailReason.REASON_SYSTEM_ERROR);
            return;
        }

        if ((credentials.user() == null) || (credentials.password() == null)) {
            client.close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
            return;
        }

        final String clientAddr = client.getIp();
        final LoginController lc = LoginController.getInstance();
        final AccountInfo info = lc.retriveAccountInfo(clientAddr, credentials.user(), credentials.password());
        if (info == null) {
            client.close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
            return;
        }

        LoginResult result = lc.tryCheckinAccount(client, clientAddr, info);
        GameServerInfo gameServer = null;
        if (result == LoginResult.ALREADY_ON_GS) {
            gameServer = lc.getAccountOnGameServer(info.getId());
            if (gameServer == null) {
                // The account left its game server between the two checks: the check-in may pass now.
                result = lc.tryCheckinAccount(client, clientAddr, info);
                if (result == LoginResult.ALREADY_ON_GS) {
                    gameServer = lc.getAccountOnGameServer(info.getId());
                }
            }
        }

        switch (result) {
            case AUTH_SUCCESS: {
                client.setAccount(info.getLogin());
                client.setConnectionState(ConnectionState.AUTHED_LOGIN);
                client.authDone();
                client.setSessionKey(lc.newSessionKey());
                lc.requestCharacters(client);
                if (LoginServer.getInstance().getProperties().showLicence()) {
                    client.sendPacket(LoginOk.builder()
                            .loginOk1(client.getSessionKey().getLoginOkID1())
                            .loginOk2(client.getSessionKey().getLoginOkID2())
                            .build());
                } else {
                    client.sendServerList();
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
                    oldClient.close(LoginFailReason.REASON_ACCOUNT_IN_USE);
                    lc.removeAuthedLoginClientIfCurrent(oldClient);
                }

                client.close(LoginFailReason.REASON_ACCOUNT_IN_USE);
                break;
            }
            case ALREADY_ON_GS: {
                client.close(LoginFailReason.REASON_ACCOUNT_IN_USE);
                final GameServerLink link = gameServer == null ? null : gameServer.getGameServerLink();
                if (link != null) {
                    link.kickPlayer(info.getId());
                }
                break;
            }
        }
    }
}
