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
package app.l2nx.highfive.login;

import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.LoginFail;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoginService {
    public static final String PASSWORD_CHANGE_FAILED = "A problem occurred while changing password!";

    private static volatile LoginService instance;

    private final LoginPort port;
    private final OnlineAccounts accounts;
    private final ServerListing listing;

    public LoginService(LoginPort port, OnlineAccounts accounts, ServerListing listing) {
        this.port = port;
        this.accounts = accounts;
        this.listing = listing;
        instance = this;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static LoginService getInstance() {
        final LoginService bound = instance;
        if (bound == null) {
            throw new IllegalStateException("LoginService is not bound: the Spring context has not created it");
        }
        return bound;
    }

    /** Holds the login against a second session before the login server names the account. */
    public boolean reserveLogin(String login, GameClient client) {
        return accounts.reserve(login, client);
    }

    public void authenticate(String login, GameClient client, SessionKey key) {
        accounts.awaitAuthentication(login, client, key);
        if (!port.authenticate(login, key)) {
            log.warn("The login server is unreachable, closing the connection of login {}", login);
            accounts.takePending(login);
            failAuthentication(login, client);
        }
    }

    public void failAuthentication(String login, GameClient client) {
        client.close(new LoginFail(LoginFail.SYSTEM_ERROR_LOGIN_LATER));
        logout(login, null);
    }

    public void cancelAuthentication(GameClient client) {
        accounts.cancelAuthentication(client);
    }

    /** @param accountId {@code null} while the login server has not accepted the session */
    public void logout(String login, UUID accountId) {
        if (login != null) {
            final GameClient released = accounts.release(login);
            if (released != null) {
                released.disconnect();
            }
        }
        if (accountId != null) {
            accounts.leave(accountId);
            port.accountLeft(accountId);
        }
    }

    /** Frees every account still held here, offline traders included. */
    public void logoutAll() {
        for (UUID accountId : accounts.leaveAll()) {
            port.accountLeft(accountId);
        }
    }

    /** A session has its account id only once the login server accepted it. */
    public GameClient findClient(UUID accountId) {
        return accountId != null ? accounts.find(accountId) : null;
    }

    public void changeAccessLevel(UUID accountId, int level) {
        port.changeAccessLevel(accountId, level);
    }

    public void reportRoute(UUID accountId, List<String> route) {
        port.reportRoute(accountId, route);
    }

    public void requestTempBan(UUID accountId, String ip, Duration duration) {
        port.requestTempBan(accountId, ip, duration);
    }

    public boolean changePassword(UUID accountId, String characterName, String oldPassword, String newPassword) {
        // Awaited before the request goes out: the answer may arrive before the port call returns.
        accounts.awaitPasswordChange(characterName);
        if (!port.changePassword(accountId, characterName, oldPassword, newPassword)) {
            accounts.takePasswordChange(characterName);
            return false;
        }
        return true;
    }

    public ListingStatus getStatus() {
        return listing.getStatus();
    }

    public void setStatus(ListingStatus status) {
        listing.setStatus(status);
        port.publishListing(Map.of(ListingAttribute.STATUS, status.code()));
    }

    public int getMaxPlayers() {
        return listing.getMaxPlayers();
    }

    public void setMaxPlayers(int maxPlayers) {
        listing.setMaxPlayers(maxPlayers);
        port.publishListing(Map.of(ListingAttribute.MAX_PLAYERS, maxPlayers));
    }

    public void setType(int type) {
        listing.setType(type);
        port.publishListing(Map.of(ListingAttribute.TYPE, type));
    }

    public void setAge(int age) {
        listing.setAge(age);
        port.publishListing(Map.of(ListingAttribute.AGE, ServerListing.ageCode(age)));
    }

    public String getServerName() {
        return listing.getServerName();
    }
}
