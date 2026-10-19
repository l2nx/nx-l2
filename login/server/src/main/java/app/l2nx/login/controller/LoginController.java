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
import app.l2nx.login.repository.AccountDataRepository;
import app.l2nx.login.repository.AccountRepository;
import app.l2nx.login.repository.AccountRepository.IpAuth;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoginController {

    private static final String TEMP_BAN_VAR = "ban_temp";

    /** Time before kicking the client if he didn't logged yet */
    public static final int LOGIN_TIMEOUT = 5 * 60 * 1000; // 5 minutes.

    private static final int BLOWFISH_KEYS = 20;

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();
    protected byte[][] blowfishKeys;
    protected ScrambledKeyPair[] keyPairs;

    /** Authed Clients on LoginServer */
    protected Map<String, LoginClient> loginServerClients = new ConcurrentHashMap<>();

    private final Map<String, Integer> failedLoginAttemps = new HashMap<>();

    @Getter
    private final Map<String, Long> bannedIps = new ConcurrentHashMap<>();

    private final AccountRepository accounts;
    private final AccountDataRepository accountData;
    private final LoginProperties properties;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> purgeTask;

    private static LoginController INSTANCE;

    public LoginController(
            AccountRepository accounts,
            AccountDataRepository accountData,
            LoginProperties properties,
            TaskScheduler taskScheduler) {
        this.accounts = accounts;
        this.accountData = accountData;
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
        purgeTask = taskScheduler.scheduleAtFixedRate(
                this::purge, Instant.now().plusMillis(LOGIN_TIMEOUT), Duration.ofMillis(LOGIN_TIMEOUT));
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
        blowfishKeys = new byte[BLOWFISH_KEYS][16];

        for (int i = 0; i < BLOWFISH_KEYS; i++) {
            SECURE_RANDOM.nextBytes(blowfishKeys[i]);
        }

        log.info("Stored {} keys for Blowfish communication.", blowfishKeys.length);
    }

    /**
     * @return Returns a random key.
     */
    public byte[] getBlowfishKey() {
        return blowfishKeys[(int) (Math.random() * BLOWFISH_KEYS)];
    }

    public SessionKey assignSessionKeyToClient(String account, LoginClient client) {
        final SessionKey key = new SessionKey(
                SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt());
        loginServerClients.put(account, client);
        return key;
    }

    public void removeAuthedLoginClient(String account) {
        if (account == null) {
            return;
        }

        loginServerClients.remove(account);
    }

    public LoginClient getAuthedClient(String account) {
        return loginServerClients.get(account);
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

            final Optional<AccountInfo> stored = accounts.findAccountInfo(login, System.currentTimeMillis());
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
                accounts.create(login, hashBase64, System.currentTimeMillis(), clientAddr);
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
            if (!isAccountInAnyGameServer(info.getLogin())) {
                // Account is not on any GS verify LS itself.
                ret = LoginResult.ALREADY_ON_LS;
                if (loginServerClients.putIfAbsent(info.getLogin(), client) == null) {
                    ret = LoginResult.AUTH_SUCCESS;
                }
            }
        }

        return ret;
    }

    /**
     * Adds the address to the ban list of the login server, with the given duration.
     * @param address The Address to be banned.
     * @param duration is milliseconds
     */
    public void addBanForAddress(String address, long duration) {
        if (duration > 0) {
            bannedIps.putIfAbsent(address, System.currentTimeMillis() + duration);
        } else // Permanent ban.
        {
            bannedIps.putIfAbsent(address, Long.MAX_VALUE);
        }
    }

    public boolean isBannedAddress(String address) {
        final String[] parts = address.split("\\.");
        Long bi = bannedIps.get(address);
        if (bi == null) {
            bi = bannedIps.get(parts[0] + "." + parts[1] + "." + parts[2] + ".0");
        }

        if (bi == null) {
            bi = bannedIps.get(parts[0] + "." + parts[1] + ".0.0");
        }

        if (bi == null) {
            bi = bannedIps.get(parts[0] + ".0.0.0");
        }

        if (bi != null) {
            if ((bi > 0) && (bi < System.currentTimeMillis())) {
                bannedIps.remove(address);
                log.info("Removed expired ip address ban {}.", address);
                return false;
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
        return bannedIps.remove(address) != null;
    }

    public SessionKey getKeyForAccount(String account) {
        final LoginClient client = loginServerClients.get(account);
        if (client != null) {
            return client.getSessionKey();
        }

        return null;
    }

    public boolean isAccountInAnyGameServer(String account) {
        final Collection<GameServerInfo> serverList =
                GameServerTable.getInstance().getRegisteredGameServers().values();
        for (GameServerInfo gsi : serverList) {
            final GameServerThread gst = gsi.getGameServerThread();
            if ((gst != null) && gst.hasAccountOnGameServer(account)) {
                return true;
            }
        }

        return false;
    }

    public GameServerInfo getAccountOnGameServer(String account) {
        final Collection<GameServerInfo> serverList =
                GameServerTable.getInstance().getRegisteredGameServers().values();
        for (GameServerInfo gsi : serverList) {
            final GameServerThread gst = gsi.getGameServerThread();
            if ((gst != null) && gst.hasAccountOnGameServer(account)) {
                return gsi;
            }
        }

        return null;
    }

    public void getCharactersOnAccount(String account) {
        for (GameServerInfo gsi :
                GameServerTable.getInstance().getRegisteredGameServers().values()) {
            if (gsi.isAuthed()) {
                gsi.getGameServerThread().requestCharacters(account);
            }
        }
    }

    public boolean isLoginPossible(LoginClient client, int serverId) {
        final GameServerInfo gsi = GameServerTable.getInstance().getRegisteredGameServerById(serverId);
        if ((gsi != null) && gsi.isAuthed()) {
            final boolean loginOk = gsi.canLogin(client);
            if (loginOk && (client.getLastServer() != serverId)) {
                try {
                    accounts.updateLastServer(client.getAccount(), serverId);
                } catch (Exception e) {
                    log.warn("Could not set lastServer: {}", e.getMessage(), e);
                }
            }

            return loginOk;
        }

        return false;
    }

    public void setAccountAccessLevel(String account, int banLevel) {
        try {
            accounts.updateAccessLevel(account, banLevel);
        } catch (Exception e) {
            log.warn("Could not set accessLevel: {}", e.getMessage(), e);
        }
    }

    public void setAccountLastTracert(String account, String pcIp, String hop1, String hop2, String hop3, String hop4) {
        try {
            accounts.updateTracert(account, pcIp, hop1, hop2, hop3, hop4);
        } catch (Exception e) {
            log.warn("Could not set last tracert: {}", e.getMessage(), e);
        }
    }

    /**
     * @param banTime stored as {@code ban_temp}; the account reads as banned while it lies after the current time
     */
    public void setAccountTempBan(String account, long banTime) {
        try {
            accountData.save(account, TEMP_BAN_VAR, Long.toString(banTime));
        } catch (DataAccessException e) {
            log.warn("Could not set temporary ban: {}", e.getMessage());
        }
    }

    public boolean changePassword(String account, String currentHash, String newHash) {
        return accounts.updatePassword(account, currentHash, newHash);
    }

    /**
     * @return the stored password hash, {@code null} if the account does not exist
     */
    public String getPasswordHash(String account) {
        return accounts.findPassword(account).orElse(null);
    }

    public void setCharactersOnServer(String account, int charsNum, long[] timeToDel, int serverId) {
        final LoginClient client = loginServerClients.get(account);
        if (client == null) {
            return;
        }

        if (charsNum > 0) {
            client.setCharsOnServ(serverId, charsNum);
        }

        if (timeToDel.length > 0) {
            client.serCharsWaitingDelOnServ(serverId, timeToDel);
        }
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
            for (IpAuth ipAuth : accounts.findIpAuths(info.getLogin())) {
                final String ip = ipAuth.ip();
                final String type = ipAuth.type();
                if (!isValidIPAddress(ip)) {
                    continue;
                }

                if (type.equals("allow")) {
                    ipWhiteList.add(ip);
                } else if (type.equals("deny")) {
                    ipBlackList.add(ip);
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
            accounts.updateLastActive(info.getLogin(), System.currentTimeMillis(), address);
            return true;
        } catch (Exception e) {
            log.warn("Could not finish login process!", e);
            return false;
        }
    }

    public boolean isValidIPAddress(String ipAddress) {
        final String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String s : parts) {
            final int i = Integer.parseInt(s);
            if ((i < 0) || (i > 255)) {
                return false;
            }
        }

        return true;
    }

    private void purge() {
        if (loginServerClients.isEmpty()) {
            return;
        }

        final long currentTime = System.currentTimeMillis();
        final Iterator<Entry<String, LoginClient>> iterator =
                loginServerClients.entrySet().iterator();
        while (iterator.hasNext()) {
            final LoginClient client = iterator.next().getValue();
            if ((!client.hasJoinedGS() && ((client.getConnectionStartTime() + LOGIN_TIMEOUT) <= currentTime))
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
