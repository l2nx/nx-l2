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
package app.l2nx.login.controller;

import app.l2nx.login.GameServerThread;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.enums.LoginResult;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.repository.AccountRepository;
import app.l2nx.login.repository.AccountRepository.IpRule;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import javax.crypto.Cipher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoginController {

    /** The lifetime of a login session: the purge kicks a client that has not moved on to a game server by then. */
    public static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(5);

    private static final int BLOWFISH_KEYS = 20;
    private static final int BLOWFISH_KEY_SIZE = 16;

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();
    protected byte[][] blowfishKeys;
    protected ScrambledKeyPair[] keyPairs;

    /** Authed Clients on LoginServer */
    protected Map<UUID, LoginClient> loginServerClients = new ConcurrentHashMap<>();

    private final Map<String, Integer> failedLoginAttemps = new HashMap<>();

    @Getter
    private final Map<String, Long> bannedIps = new ConcurrentHashMap<>();

    private final AccountRepository accounts;
    private final LoginProperties properties;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> purgeTask;

    private static LoginController INSTANCE;

    public LoginController(AccountRepository accounts, LoginProperties properties, TaskScheduler taskScheduler) {
        this.accounts = accounts;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        INSTANCE = this;
    }

    public void load() throws GeneralSecurityException {
        log.info("Loading LoginController...");
        keyPairs = new ScrambledKeyPair[10];

        final KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        final RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
        keygen.initialize(spec);

        // Generate the initial set of keys.
        for (int i = 0; i < 10; i++) {
            keyPairs[i] = new ScrambledKeyPair(keygen.generateKeyPair());
        }

        log.info("Cached 10 KeyPairs for RSA communication.");

        testCipher((RSAPrivateKey) keyPairs[0].getPrivateKey());

        // Store keys for blowfish communication.
        generateBlowFishKeys();

        // Start the client purge task.
        purgeTask = taskScheduler.scheduleAtFixedRate(this::purge, Instant.now().plus(LOGIN_TIMEOUT), LOGIN_TIMEOUT);
    }

    public void shutdown() {
        if (purgeTask != null) {
            purgeTask.cancel(false);
            purgeTask = null;
        }
    }

    /**
     * This is mostly to force the initialization of the Crypto Implementation, avoiding it being done on runtime when its first needed.<br>
     * In short it avoids the worst-case execution time on runtime by doing it on loading.
     * @param key Any private RSA Key just for testing purposes.
     * @throws GeneralSecurityException if a underlying exception was thrown by the Cipher
     */
    private void testCipher(RSAPrivateKey key) throws GeneralSecurityException {
        // Avoid worst-case execution.
        final Cipher rsaCipher = Cipher.getInstance("RSA/ECB/nopadding");
        rsaCipher.init(Cipher.DECRYPT_MODE, key);
    }

    private void generateBlowFishKeys() {
        blowfishKeys = new byte[BLOWFISH_KEYS][];

        for (int i = 0; i < BLOWFISH_KEYS; i++) {
            blowfishKeys[i] = newBlowfishKey(SECURE_RANDOM);
        }

        log.info("Stored {} keys for Blowfish communication.", blowfishKeys.length);
    }

    /**
     * The client takes the key of {@code Init} as a zero-terminated string: a zero byte inside would leave the client
     * a shorter key than the server's, every frame it sends would fail the checksum and the login would hang.
     */
    static byte[] newBlowfishKey(RandomGenerator random) {
        final byte[] key = new byte[BLOWFISH_KEY_SIZE];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) random.nextInt(1, 256);
        }
        return key;
    }

    /**
     * @return Returns a random key.
     */
    public byte[] getBlowfishKey() {
        return blowfishKeys[(int) (Math.random() * BLOWFISH_KEYS)];
    }

    public SessionKey newSessionKey() {
        return new SessionKey(
                SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt());
    }

    /**
     * Drops whatever client holds the account, for the game server taking the account over.
     */
    public void removeAuthedLoginClient(UUID accountId) {
        if (accountId == null) {
            return;
        }

        loginServerClients.remove(accountId);
    }

    /**
     * Drops only this client: the account may already be held by a newer connection, which must stay.
     */
    public void removeAuthedLoginClientIfCurrent(LoginClient client) {
        final UUID accountId = client.getAccountId();
        if (accountId == null) {
            return;
        }

        loginServerClients.remove(accountId, client);
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

    public AccountInfo retriveAccountInfo(String clientAddr, String login, String password) {
        return retriveAccountInfo(clientAddr, login, password, true);
    }

    private void recordFailedLoginAttemp(String addr) {
        // We need to synchronize this!
        // When multiple connections from the same address fail to login at the same time, unexpected behavior can
        // happen.
        Integer failedLoginAttemps;
        synchronized (this.failedLoginAttemps) {
            failedLoginAttemps = this.failedLoginAttemps.get(addr);
            if (failedLoginAttemps == null) {
                failedLoginAttemps = 1;
            } else {
                ++failedLoginAttemps;
            }

            this.failedLoginAttemps.put(addr, failedLoginAttemps);
        }

        if (failedLoginAttemps >= properties.ban().failedLogins()) {
            addBanForAddress(addr, properties.ban().duration().toMillis());

            // We need to clear the failed login attempts here, so after the IP ban is over the client has another 5
            // attempts.
            clearFailedLoginAttemps(addr);
            log.warn("Added banned address {}! Too many login attempts.", addr);
        }
    }

    private void clearFailedLoginAttemps(String clientAddr) {
        synchronized (failedLoginAttemps) {
            failedLoginAttemps.remove(clientAddr);
        }
    }

    private AccountInfo retriveAccountInfo(
            String clientAddr, String login, String password, boolean autoCreateIfEnabled) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA");
            final byte[] raw = password.getBytes(StandardCharsets.UTF_8);
            final String hashBase64 = Base64.getEncoder().encodeToString(md.digest(raw));

            final Optional<AccountInfo> stored = accounts.findAccountInfo(login, Instant.now());
            if (stored.isPresent()) {
                final AccountInfo info = stored.get();
                if (!info.checkPassHash(hashBase64)) {
                    recordFailedLoginAttemp(clientAddr);
                    return null;
                }

                clearFailedLoginAttemps(clientAddr);
                return info;
            }

            if (!autoCreateIfEnabled || !properties.autoCreateAccounts()) {
                // Account does not exist and auto create account is not desired.
                recordFailedLoginAttemp(clientAddr);
                return null;
            }

            try {
                accounts.create(login, hashBase64, Instant.now(), clientAddr);
            } catch (Exception e) {
                log.warn("Exception while auto creating account for '{}'!", login, e);
                return null;
            }

            log.info("Auto created account '{}'.", login);
            return retriveAccountInfo(clientAddr, login, password, false);
        } catch (Exception e) {
            log.warn("Exception while retriving account info for '{}'!", login, e);
            return null;
        }
    }

    public LoginResult tryCheckinAccount(LoginClient client, String address, AccountInfo info) {
        if (info.getAccessLevel() < 0) {
            return LoginResult.ACCOUNT_BANNED;
        }

        LoginResult ret = LoginResult.INVALID_PASSWORD;

        // Check auth.
        if (canCheckin(client, address, info)) {
            // Login was successful, verify presence on Gameservers.
            ret = LoginResult.ALREADY_ON_GS;
            if (!isAccountInAnyGameServer(info.getId())) {
                // Account is not on any GS verify LS itself.
                ret = LoginResult.ALREADY_ON_LS;
                // Set before the entry exists, so a close from now on finds the entry it has to remove.
                client.setAccountId(info.getId());
                if (loginServerClients.putIfAbsent(info.getId(), client) == null) {
                    // A close before the entry existed had nothing to remove.
                    if (!client.isConnected()) {
                        loginServerClients.remove(info.getId(), client);
                    }
                    ret = LoginResult.AUTH_SUCCESS;
                }
            }
        }

        return ret;
    }

    /**
     * Adds the address to the ban list of the login server, with the given duration.
     * @param address an IP literal; a host name is refused, not resolved
     * @param duration is milliseconds
     * @return {@code false} when the address is not an IP literal
     */
    public boolean addBanForAddress(String address, long duration) {
        final String key = banKey(address);
        if (key == null) {
            log.warn("Ignored a ban of '{}': not an IP address.", address);
            return false;
        }
        if (duration > 0) {
            bannedIps.putIfAbsent(key, System.currentTimeMillis() + duration);
        } else // Permanent ban.
        {
            bannedIps.putIfAbsent(key, Long.MAX_VALUE);
        }
        return true;
    }

    /** The {@link InetAddress#getHostAddress()} form that {@link #isBannedAddress} looks up, e.g. IPv6 uncompressed. */
    private static String banKey(String address) {
        if (address == null) {
            return null;
        }
        try {
            return InetAddress.ofLiteral(address).getHostAddress();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isBannedAddress(InetAddress address) {
        final String host = address.getHostAddress();
        final List<String> keys = new ArrayList<>(4);
        keys.add(host);
        // Zeroed-octet subnet bans exist only for IPv4; IPv6 addresses are banned exactly.
        if (address instanceof Inet4Address) {
            final String[] parts = host.split("\\.");
            keys.add(parts[0] + "." + parts[1] + "." + parts[2] + ".0");
            keys.add(parts[0] + "." + parts[1] + ".0.0");
            keys.add(parts[0] + ".0.0.0");
        }
        for (String key : keys) {
            final Long expiry = bannedIps.get(key);
            if (expiry == null) {
                continue;
            }
            if ((expiry > 0) && (expiry < System.currentTimeMillis())) {
                bannedIps.remove(key, expiry);
                log.info("Removed expired ip address ban {}.", key);
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Remove the specified address from the ban list
     * @param address The address to be removed from the ban list
     * @return true if the ban was removed, false if there was no ban for this ip
     */
    public boolean removeBanForAddress(String address) {
        final String key = banKey(address);
        return (key != null) && (bannedIps.remove(key) != null);
    }

    public boolean isAccountInAnyGameServer(UUID accountId) {
        final Collection<GameServerInfo> serverList =
                GameServerTable.getInstance().getRegisteredGameServers().values();
        for (GameServerInfo gsi : serverList) {
            final GameServerThread gst = gsi.getGameServerThread();
            if ((gst != null) && gst.hasAccountOnGameServer(accountId)) {
                return true;
            }
        }

        return false;
    }

    public GameServerInfo getAccountOnGameServer(UUID accountId) {
        final Collection<GameServerInfo> serverList =
                GameServerTable.getInstance().getRegisteredGameServers().values();
        for (GameServerInfo gsi : serverList) {
            final GameServerThread gst = gsi.getGameServerThread();
            if ((gst != null) && gst.hasAccountOnGameServer(accountId)) {
                return gsi;
            }
        }

        return null;
    }

    public void requestCharacters(LoginClient client) {
        requestCharacters(
                client, GameServerTable.getInstance().getRegisteredGameServers().values());
    }

    void requestCharacters(LoginClient client, Collection<GameServerInfo> servers) {
        final Map<Integer, GameServerThread> online = new HashMap<>();
        for (GameServerInfo gsi : servers) {
            // The link may drop concurrently; a server without one cannot answer, so it is not awaited.
            final GameServerThread link = gsi.getGameServerThread();
            if (gsi.isAuthed() && (link != null)) {
                online.put(gsi.getId(), link);
            }
        }
        client.awaitCharacters(online.keySet());
        online.values().forEach(link -> link.requestCharacters(client.getAccountId()));
    }

    public boolean isLoginPossible(LoginClient client, int serverId) {
        final GameServerInfo gsi = GameServerTable.getInstance().getRegisteredGameServerById(serverId);
        if ((gsi != null) && gsi.isAuthed()) {
            final boolean loginOk = gsi.canLogin(client);
            if (loginOk && (client.getLastServer() != serverId)) {
                try {
                    accounts.updateLastServer(client.getAccountId(), serverId);
                } catch (Exception e) {
                    log.warn("Could not set lastServer: {}", e.getMessage(), e);
                }
            }

            return loginOk;
        }

        return false;
    }

    public void setAccountAccessLevel(UUID accountId, int banLevel) {
        try {
            accounts.updateAccessLevel(accountId, banLevel);
        } catch (Exception e) {
            log.warn("Could not set accessLevel: {}", e.getMessage(), e);
        }
    }

    public void setAccountLastTracert(UUID accountId, String pcIp, String hop1, String hop2, String hop3, String hop4) {
        try {
            accounts.updateTracert(accountId, pcIp, hop1, hop2, hop3, hop4);
        } catch (Exception e) {
            log.warn("Could not set last tracert: {}", e.getMessage(), e);
        }
    }

    public void setAccountTempBan(UUID accountId, Duration duration) {
        try {
            accounts.updateBannedUntil(accountId, Instant.now().plus(duration));
        } catch (DataAccessException e) {
            log.warn("Could not set temporary ban: {}", e.getMessage());
        }
    }

    public boolean changePassword(UUID accountId, String currentHash, String newHash) {
        return accounts.updatePassword(accountId, currentHash, newHash);
    }

    /**
     * @return the stored password hash, {@code null} if the account does not exist
     */
    public String getPasswordHash(UUID accountId) {
        return accounts.findPassword(accountId).orElse(null);
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

    /**
     * This method returns one of the cached {@link ScrambledKeyPair ScrambledKeyPairs} for communication with Login Clients.
     * @return a scrambled keypair
     */
    public ScrambledKeyPair getScrambledRSAKeyPair() {
        return keyPairs[ThreadLocalRandom.current().nextInt(keyPairs.length)];
    }

    /**
     * @param client the client
     * @param address client host address
     * @param info the account info to checkin
     * @return true when ok to checkin, false otherwise
     */
    public boolean canCheckin(LoginClient client, String address, AccountInfo info) {
        try {
            final List<String> ipWhiteList = new ArrayList<>();
            final List<String> ipBlackList = new ArrayList<>();
            for (IpRule rule : accounts.findIpRules(info.getId())) {
                if (rule.allow()) {
                    ipWhiteList.add(rule.ip());
                } else {
                    ipBlackList.add(rule.ip());
                }
            }

            // Check IP.
            if (!ipWhiteList.isEmpty() || !ipBlackList.isEmpty()) {
                if (!ipWhiteList.isEmpty() && !ipWhiteList.contains(address)) {
                    log.warn(
                            "Account checkin attemp from address({}) not present on whitelist for account '{}'.",
                            address,
                            info.getLogin());
                    return false;
                }

                if (!ipBlackList.isEmpty() && ipBlackList.contains(address)) {
                    log.warn(
                            "Account checkin attemp from address({}) on blacklist for account '{}'.",
                            address,
                            info.getLogin());
                    return false;
                }
            }

            client.setAccessLevel(info.getAccessLevel());
            client.setLastServer(info.getLastServer());
            accounts.updateLastActive(info.getId(), Instant.now(), address);
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
                client.close(LoginFailReason.REASON_ACCESS_FAILED);
                iterator.remove();
            }
        }
    }

    public static LoginController getInstance() {
        return INSTANCE;
    }
}
