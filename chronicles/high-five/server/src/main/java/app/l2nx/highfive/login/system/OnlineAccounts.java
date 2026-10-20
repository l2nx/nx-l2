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
package app.l2nx.highfive.login.system;

import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.network.GameClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Builder;
import org.springframework.stereotype.Component;

/**
 * The sessions this game server holds for the login server, written from the GS-link lane and from client threads.
 * Logins guard a second session before the login server names the account; UUIDs key everything after it.
 */
@Component
public class OnlineAccounts {
    private final Map<String, GameClient> logins = new ConcurrentHashMap<>();
    private final Map<UUID, GameClient> accounts = new ConcurrentHashMap<>();
    private final Map<String, PendingAuthentication> pending = new ConcurrentHashMap<>();

    public boolean reserve(String login, GameClient client) {
        return logins.putIfAbsent(login, client) == null;
    }

    /** @return the session that held the login, {@code null} for none */
    public GameClient release(String login) {
        return logins.remove(login);
    }

    public void awaitAuthentication(String login, GameClient client, SessionKey key) {
        pending.put(
                login,
                PendingAuthentication.builder().client(client).sessionKey(key).build());
    }

    /** @return the session waiting for the login server's answer about this login, {@code null} for none */
    public PendingAuthentication takePending(String login) {
        return pending.remove(login);
    }

    public void cancelAuthentication(GameClient client) {
        pending.values().removeIf(waiting -> waiting.client() == client);
    }

    public void enter(UUID accountId, GameClient client) {
        accounts.put(accountId, client);
    }

    public void leave(UUID accountId) {
        accounts.remove(accountId);
    }

    public GameClient find(UUID accountId) {
        return accounts.get(accountId);
    }

    public List<UUID> accountIds() {
        return List.copyOf(accounts.keySet());
    }

    /** @return the accounts held until now */
    public List<UUID> leaveAll() {
        final List<UUID> accountIds = accountIds();
        accountIds.forEach(accounts::remove);
        return accountIds;
    }

    @Builder(toBuilder = true)
    public record PendingAuthentication(GameClient client, SessionKey sessionKey) {}
}
