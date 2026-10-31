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

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.SessionKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Builder;
import org.springframework.stereotype.Component;

/**
 * Sessions held for the login server, written from the GS-link lane and client threads. Logins guard a second session
 * before the account is named; UUIDs key everything after.
 */
@Component
public class OnlineAccounts {
    private final Map<String, GameClient> logins = new ConcurrentHashMap<>();
    private final Map<UUID, GameClient> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAuthentication> pending = new ConcurrentHashMap<>();
    private final Map<UUID, GameClient> passwordChanges = new ConcurrentHashMap<>();

    public boolean reserve(String login, GameClient client) {
        return logins.putIfAbsent(login, client) == null;
    }

    public GameClient release(String login) {
        return logins.remove(login);
    }

    public boolean release(String login, GameClient client) {
        return logins.remove(login, client);
    }

    public UUID awaitAuthentication(String login, GameClient client, SessionKey key) {
        final UUID requestId = EntityIds.next();
        pending.put(
                requestId,
                PendingAuthentication.builder()
                        .login(login)
                        .client(client)
                        .sessionKey(key)
                        .build());
        return requestId;
    }

    /** @return the client's entry for this request, taken out; {@code null} when it is gone or another client's */
    public PendingAuthentication takePending(UUID requestId, GameClient client) {
        final PendingAuthentication waiting = pending.get(requestId);
        return ((waiting != null) && (waiting.client() == client) && pending.remove(requestId, waiting))
                ? waiting
                : null;
    }

    public List<UUID> pendingAuthentications() {
        return List.copyOf(pending.keySet());
    }

    public UUID awaitPasswordChange(GameClient client) {
        final UUID requestId = EntityIds.next();
        passwordChanges.put(requestId, client);
        return requestId;
    }

    public boolean takePasswordChange(UUID requestId, GameClient client) {
        return passwordChanges.remove(requestId, client);
    }

    public List<UUID> pendingPasswordChanges() {
        return List.copyOf(passwordChanges.keySet());
    }

    public void cancelRequests(GameClient client) {
        pending.values().removeIf(waiting -> waiting.client() == client);
        passwordChanges.values().removeIf(waiting -> waiting == client);
    }

    public GameClient requester(UUID requestId) {
        final PendingAuthentication waiting = pending.get(requestId);
        return (waiting != null) ? waiting.client() : passwordChanges.get(requestId);
    }

    public void enter(UUID accountId, GameClient client) {
        accounts.put(accountId, client);
    }

    public void leave(UUID accountId) {
        accounts.remove(accountId);
    }

    public boolean leave(UUID accountId, GameClient client) {
        return accounts.remove(accountId, client);
    }

    public GameClient find(UUID accountId) {
        return accounts.get(accountId);
    }

    public List<UUID> accountIds() {
        return List.copyOf(accounts.keySet());
    }

    public List<UUID> leaveAll() {
        final List<UUID> accountIds = accountIds();
        accountIds.forEach(accounts::remove);
        return accountIds;
    }

    @Builder(toBuilder = true)
    public record PendingAuthentication(String login, GameClient client, SessionKey sessionKey) {}
}
