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
package app.l2nx.highfive.login.command;

import app.l2nx.highfive.login.LoginPort;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.OnlineAccounts.PendingAuthentication;
import app.l2nx.highfive.network.ConnectionState;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.CharSelectionInfo;
import app.l2nx.highfive.network.serverpackets.LoginFail;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AuthenticationResultHandler implements Consumer<AuthenticationResult> {
    private final LoginPort port;
    private final LoginService loginService;
    private final OnlineAccounts accounts;

    @Override
    public void accept(AuthenticationResult result) {
        final PendingAuthentication pending = accounts.takePending(result.login());
        if (pending == null) {
            return;
        }
        final GameClient client = pending.client();
        if (!result.accepted()) {
            log.warn("Session key incorrect, closing the connection of login {}", result.login());
            client.close(new LoginFail(LoginFail.SYSTEM_ERROR_LOGIN_LATER));
            loginService.logout(result.login(), null);
            return;
        }
        final UUID accountId = result.accountId();
        client.setAccountId(accountId);
        accounts.enter(accountId, client);
        port.accountsEntered(List.of(accountId));
        client.setConnectionState(ConnectionState.AUTHENTICATED);
        client.setSessionId(pending.sessionKey());
        final CharSelectionInfo selection = new CharSelectionInfo(client);
        client.sendPacket(selection);
        client.setCharSelection(selection.getCharInfo());
    }
}
