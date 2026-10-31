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
package app.l2nx.login.session;

import app.l2nx.login.account.AccountInfo;
import app.l2nx.login.account.AccountRepository;
import app.l2nx.login.account.AccountRepository.IpRule;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.session.model.LoginResult;
import app.l2nx.login.session.model.ScrambledKeyPair;
import app.l2nx.login.session.model.SessionKey;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginController {

    /** The lifetime of a login session: the purge kicks a client that has not moved on to a game server by then. */
    public static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(5);

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository repository;
    private final GameServerTable gameServerTable;
    private final TaskScheduler taskScheduler;

    final Map<UUID, LoginClient> loginServerClients = new ConcurrentHashMap<>();

    private ScrambledKeyPair[] keyPairs;
    private ScheduledFuture<?> purgeTask;

    public void load() throws GeneralSecurityException {
        log.info("Loading LoginController...");
        keyPairs = new ScrambledKeyPair[10];

        final KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        final RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
        keygen.initialize(spec);

        for (int i = 0; i < 10; i++) {
            keyPairs[i] = new ScrambledKeyPair(keygen.generateKeyPair());
        }

        log.info("Cached 10 KeyPairs for RSA communication.");

        purgeTask = taskScheduler.scheduleAtFixedRate(this::purge, Instant.now().plus(LOGIN_TIMEOUT), LOGIN_TIMEOUT);
    }

    public void shutdown() {
        if (purgeTask != null) {
            purgeTask.cancel(false);
            purgeTask = null;
        }
    }

    public SessionKey newSessionKey() {
        return SessionKey.builder()
                .loginOk1(SECURE_RANDOM.nextInt())
                .loginOk2(SECURE_RANDOM.nextInt())
                .playOk1(SECURE_RANDOM.nextInt())
                .playOk2(SECURE_RANDOM.nextInt())
                .build();
    }

    public void removeAuthedLoginClient(UUID accountId) {
        if (accountId == null) {
            return;
        }

        loginServerClients.remove(accountId);
    }

    /**
     * Drops only this client: the account may already be held by a newer connection, which must stay.
     * @return whether this call removed the client
     */
    public boolean removeAuthedLoginClientIfCurrent(LoginClient client) {
        final UUID accountId = client.getAccountId();
        if (accountId == null) {
            return false;
        }

        return loginServerClients.remove(accountId, client);
    }

    public LoginClient getAuthedClient(UUID accountId) {
        return loginServerClients.get(accountId);
    }

    /**
     * Matched by login: the game server knows only the typed login. The map holds only clients between login and game
     * server entry, so the scan is short.
     * @return the authed client of that account, {@code null} if none
     */
    public LoginClient getAuthedClientByLogin(String login) {
        for (LoginClient client : loginServerClients.values()) {
            if (login.equals(client.getAccount())) {
                return client;
            }
        }

        return null;
    }

    public LoginResult tryCheckinAccount(LoginClient client, String address, AccountInfo info) {
        if (info.accessLevel() < 0) {
            return LoginResult.ACCOUNT_BANNED;
        }

        LoginResult ret = LoginResult.INVALID_PASSWORD;

        if (canCheckin(client, address, info)) {
            ret = LoginResult.ALREADY_ON_GS;
            if (!isAccountInAnyGameServer(info.id())) {
                ret = LoginResult.ALREADY_ON_LS;
                // Set before the entry exists, so a close from now on finds the entry it has to remove.
                client.setAccountId(info.id());
                if (loginServerClients.putIfAbsent(info.id(), client) == null) {
                    // A close before the entry existed had nothing to remove.
                    if (!client.isConnected()) {
                        loginServerClients.remove(info.id(), client);
                    }
                    ret = LoginResult.AUTH_SUCCESS;
                }
            }
        }

        return ret;
    }

    public boolean isAccountInAnyGameServer(UUID accountId) {
        final Collection<GameServerInfo> serverList =
                gameServerTable.getRegisteredGameServers().values();
        for (GameServerInfo gsi : serverList) {
            final GameServerLink gst = gsi.getGameServerLink();
            if ((gst != null) && gst.hasAccountOnGameServer(accountId)) {
                return true;
            }
        }

        return false;
    }

    public GameServerInfo getAccountOnGameServer(UUID accountId) {
        final Collection<GameServerInfo> serverList =
                gameServerTable.getRegisteredGameServers().values();
        for (GameServerInfo gsi : serverList) {
            final GameServerLink gst = gsi.getGameServerLink();
            if ((gst != null) && gst.hasAccountOnGameServer(accountId)) {
                return gsi;
            }
        }

        return null;
    }

    public void requestCharacters(LoginClient client) {
        requestCharacters(client, gameServerTable.getRegisteredGameServers().values());
    }

    void requestCharacters(LoginClient client, Collection<GameServerInfo> servers) {
        final Map<Integer, GameServerLink> online = new HashMap<>();
        for (GameServerInfo gsi : servers) {
            // The link may drop concurrently; a server without one cannot answer, so it is not awaited.
            final GameServerLink link = gsi.getGameServerLink();
            if (gsi.isAuthed() && (link != null) && gsi.isOpenTo(client.getVariant())) {
                online.put(gsi.getId(), link);
            }
        }
        client.awaitCharacters(online.keySet());
        online.values().forEach(link -> link.requestCharacters(client.getAccountId()));
    }

    public boolean isLoginPossible(LoginClient client, int serverId) {
        final GameServerInfo gsi = gameServerTable.getRegisteredGameServerById(serverId);
        if ((gsi != null) && gsi.isAuthed() && gsi.isOpenTo(client.getVariant())) {
            final boolean loginOk = gsi.canLogin(client);
            if (loginOk && (client.getLastServer() != serverId)) {
                try {
                    repository.updateLastServer(client.getAccountId(), serverId);
                } catch (Exception e) {
                    log.warn("Could not set lastServer: {}", e.getMessage(), e);
                }
            }

            return loginOk;
        }

        return false;
    }

    public void setCharactersOnServer(UUID accountId, int charsNum, long[] timeToDel, int serverId) {
        final LoginClient client = loginServerClients.get(accountId);
        if (client == null) {
            return;
        }

        if (charsNum > 0) {
            client.setCharsOnServ(serverId, charsNum);
        }

        if (timeToDel.length > 0) {
            client.serCharsWaitingDelOnServ(serverId, timeToDel);
        }
        client.charactersArrived(serverId);
    }

    public ScrambledKeyPair getScrambledRSAKeyPair() {
        return keyPairs[ThreadLocalRandom.current().nextInt(keyPairs.length)];
    }

    public boolean canCheckin(LoginClient client, String address, AccountInfo info) {
        try {
            final List<String> ipWhiteList = new ArrayList<>();
            final List<String> ipBlackList = new ArrayList<>();
            for (IpRule rule : repository.findIpRules(info.id())) {
                if (rule.allow()) {
                    ipWhiteList.add(rule.ip());
                } else {
                    ipBlackList.add(rule.ip());
                }
            }

            if (!ipWhiteList.isEmpty() || !ipBlackList.isEmpty()) {
                if (!ipWhiteList.isEmpty() && !ipWhiteList.contains(address)) {
                    log.warn(
                            "Account checkin attemp from address({}) not present on whitelist for account '{}'.",
                            address,
                            info.login());
                    return false;
                }

                if (!ipBlackList.isEmpty() && ipBlackList.contains(address)) {
                    log.warn(
                            "Account checkin attemp from address({}) on blacklist for account '{}'.",
                            address,
                            info.login());
                    return false;
                }
            }

            client.setAccessLevel(info.accessLevel());
            client.setLastServer(info.lastServer());
            repository.updateLastActive(info.id(), Instant.now(), address);
            return true;
        } catch (Exception e) {
            log.warn("Could not finish login process!", e);
            return false;
        }
    }

    private void purge() {
        if (loginServerClients.isEmpty()) {
            return;
        }

        final long currentTime = System.currentTimeMillis();
        final Iterator<Entry<UUID, LoginClient>> iterator =
                loginServerClients.entrySet().iterator();
        while (iterator.hasNext()) {
            final LoginClient client = iterator.next().getValue();
            if ((!client.hasJoinedGS() && ((client.getConnectionStartTime() + LOGIN_TIMEOUT.toMillis()) <= currentTime))
                    || !client.isConnected()) {
                client.closeForExpiry();
                iterator.remove();
            }
        }
    }
}
