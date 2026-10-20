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
package app.l2nx.highfive;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.ConnectionState;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.loginserverpackets.receive.AuthResponse;
import app.l2nx.highfive.network.loginserverpackets.receive.ChangePasswordResponse;
import app.l2nx.highfive.network.loginserverpackets.receive.InitLS;
import app.l2nx.highfive.network.loginserverpackets.receive.KickPlayer;
import app.l2nx.highfive.network.loginserverpackets.receive.LoginServerFail;
import app.l2nx.highfive.network.loginserverpackets.receive.PlayerAuthResponse;
import app.l2nx.highfive.network.loginserverpackets.receive.RequestCharacters;
import app.l2nx.highfive.network.loginserverpackets.send.AuthRequest;
import app.l2nx.highfive.network.loginserverpackets.send.BlowFishKey;
import app.l2nx.highfive.network.loginserverpackets.send.ChangeAccessLevel;
import app.l2nx.highfive.network.loginserverpackets.send.ChangePassword;
import app.l2nx.highfive.network.loginserverpackets.send.PlayerAuthRequest;
import app.l2nx.highfive.network.loginserverpackets.send.PlayerInGame;
import app.l2nx.highfive.network.loginserverpackets.send.PlayerLogout;
import app.l2nx.highfive.network.loginserverpackets.send.PlayerTracert;
import app.l2nx.highfive.network.loginserverpackets.send.ReplyCharacters;
import app.l2nx.highfive.network.loginserverpackets.send.SendMail;
import app.l2nx.highfive.network.loginserverpackets.send.ServerStatus;
import app.l2nx.highfive.network.loginserverpackets.send.TempBan;
import app.l2nx.highfive.network.serverpackets.CharSelectionInfo;
import app.l2nx.highfive.network.serverpackets.LoginFail;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.PacketCipher;
import app.l2nx.network.crypt.XorChecksum;
import app.l2nx.network.packet.SimpleWritablePacket;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles communication between the game server and login server.<br>
 * Manages player authentication, server status updates and various administrative functions.
 */
@Slf4j
public class LoginServerThread extends Thread {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    // Protocol constants.
    private static final int REVISION = 0x0106; // @see app.l2nx.login.LoginServer#PROTOCOL_REV
    private static final int RECONNECT_DELAY = 5000; // 5 seconds.
    private static final int BLOWFISH_KEY_SIZE = 40;
    private static final int HEX_ID_SIZE = 16;
    private static final int PACKET_PADDING = 8;
    private static final SecureRandom KEY_RANDOM = new SecureRandom();

    // Connection configuration.
    private final String hostname;
    private final int port;
    private final int gamePort;
    private final boolean acceptAlternate;
    private final boolean reserveHost;
    private final List<String> subnets;
    private final List<String> hosts;

    // Connection state.
    private Socket socket;
    private OutputStream out;
    private PacketCipher blowfish;
    private byte[] hexID;
    private int requestID;

    // Server state.
    @Getter
    private int maxPlayer;

    private final Set<WaitingClient> waitingClients = ConcurrentHashMap.newKeySet();
    private final Map<String, GameClient> accountsInGameServer = new ConcurrentHashMap<>();

    @Getter
    private int serverStatus;

    @Getter
    private String serverName;

    protected LoginServerThread() {
        super("LoginServerThread");

        // Initialize connection settings.
        port = ServerConfig.GAME_SERVER_LOGIN_PORT;
        gamePort = ServerConfig.PORT_GAME;
        hostname = ServerConfig.GAME_SERVER_LOGIN_HOST;
        acceptAlternate = ServerConfig.ACCEPT_ALTERNATE_ID;
        reserveHost = ServerConfig.RESERVE_HOST_ON_LOGIN;
        subnets = ServerConfig.GAME_SERVER_SUBNETS;
        hosts = ServerConfig.GAME_SERVER_HOSTS;
        maxPlayer = ServerConfig.MAXIMUM_ONLINE_USERS;

        // Initialize server identification.
        hexID = ServerConfig.HEX_ID;
        if (hexID == null) {
            requestID = ServerConfig.REQUEST_ID;
            hexID = generateHexBytes(HEX_ID_SIZE);
        } else {
            requestID = ServerConfig.SERVER_ID;
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            int lengthHi = 0;
            int lengthLo = 0;
            int length = 0;
            boolean checksumOk = false;

            try {
                // Establish connection to login server.
                log.info("Connecting to login on {}:{}", hostname, port);
                socket = new Socket(hostname, port);
                final InputStream in = socket.getInputStream();
                out = new BufferedOutputStream(socket.getOutputStream());

                // Initialize Blowfish encryption with default key.
                final byte[] blowfishKey = generateHexBytes(BLOWFISH_KEY_SIZE);
                blowfish = new LittleEndianBlowfishCipher("_;v.]05-31!|+-%xT!^[$\00".getBytes(StandardCharsets.UTF_8));

                while (!isInterrupted()) {
                    // Read packet length.
                    lengthLo = in.read();
                    lengthHi = in.read();
                    length = (lengthHi * 256) + lengthLo;
                    if (lengthHi < 0) {
                        log.trace("Login terminated the connection.");
                        break;
                    }

                    // Read packet data.
                    final byte[] incoming = new byte[length - 2];
                    int receivedBytes = 0;
                    int newBytes = 0;
                    int left = length - 2;
                    while ((newBytes != -1) && (receivedBytes < (length - 2))) {
                        newBytes = in.read(incoming, receivedBytes, left);
                        receivedBytes += newBytes;
                        left -= newBytes;
                    }

                    if (receivedBytes != (length - 2)) {
                        log.warn("Incomplete packet received, closing connection (LS)");
                        break;
                    }

                    if ((incoming.length % 8) != 0) {
                        log.warn("Packet length is not a multiple of the cipher block, closing connection (LS)");
                        break;
                    }

                    // Decrypt and verify packet.
                    blowfish.decrypt(incoming, 0, incoming.length);
                    checksumOk = XorChecksum.verify(incoming, 0, incoming.length);
                    if (!checksumOk) {
                        log.warn("Incorrect packet checksum, ignoring packet (LS)");
                        break;
                    }

                    // Process packet based on type.
                    final int packetType = incoming[0] & 0xff;
                    switch (packetType) {
                        case 0x00: // InitLS - Initialize login server communication.
                        {
                            final InitLS init = new InitLS(incoming);
                            if (init.getRevision() != REVISION) {
                                log.warn("/!\\ Revision mismatch between LS and GS /!\\");
                                break;
                            }

                            // Generate RSA public key from login server data.
                            RSAPublicKey publicKey;
                            try {
                                final KeyFactory kfac = KeyFactory.getInstance("RSA");
                                final BigInteger modulus = new BigInteger(init.getRSAKey());
                                final RSAPublicKeySpec kspec1 =
                                        new RSAPublicKeySpec(modulus, RSAKeyGenParameterSpec.F4);
                                publicKey = (RSAPublicKey) kfac.generatePublic(kspec1);
                            } catch (GeneralSecurityException e) {
                                log.warn("Trouble initializing RSA public key from login server.");
                                break;
                            }

                            // Send encrypted blowfish key and switch to secure communication.
                            sendPacket(new BlowFishKey(blowfishKey, publicKey));
                            blowfish = new LittleEndianBlowfishCipher(blowfishKey);
                            sendPacket(new AuthRequest(
                                    requestID,
                                    acceptAlternate,
                                    hexID,
                                    gamePort,
                                    reserveHost,
                                    maxPlayer,
                                    subnets,
                                    hosts));
                            break;
                        }
                        case 0x01: // LoginServerFail - Registration failure.
                        {
                            final LoginServerFail lsf = new LoginServerFail(incoming);
                            log.info("Registration failed: {}", lsf.getReasonString());
                            break;
                        }
                        case 0x02: // AuthResponse - Server registration successful.
                        {
                            final AuthResponse aresp = new AuthResponse(incoming);
                            final int serverID = aresp.getServerId();
                            serverName = aresp.getServerName();
                            ServerConfig.saveHexid(serverID, hexToString(hexID));
                            log.info("Registered on login as Server {}: {}", serverID, serverName);

                            // Configure and send server status.
                            final ServerStatus st = new ServerStatus();
                            if (ServerConfig.SERVER_LIST_BRACKET) {
                                st.addAttribute(ServerStatus.SERVER_LIST_SQUARE_BRACKET, ServerStatus.ON);
                            } else {
                                st.addAttribute(ServerStatus.SERVER_LIST_SQUARE_BRACKET, ServerStatus.OFF);
                            }

                            st.addAttribute(ServerStatus.SERVER_TYPE, ServerConfig.SERVER_LIST_TYPE);
                            if (GeneralConfig.SERVER_GMONLY) {
                                st.addAttribute(ServerStatus.SERVER_LIST_STATUS, ServerStatus.STATUS_GM_ONLY);
                            } else {
                                st.addAttribute(ServerStatus.SERVER_LIST_STATUS, ServerStatus.STATUS_AUTO);
                            }

                            if (ServerConfig.SERVER_LIST_AGE == 15) {
                                st.addAttribute(ServerStatus.SERVER_AGE, ServerStatus.SERVER_AGE_15);
                            } else if (ServerConfig.SERVER_LIST_AGE == 18) {
                                st.addAttribute(ServerStatus.SERVER_AGE, ServerStatus.SERVER_AGE_18);
                            } else {
                                st.addAttribute(ServerStatus.SERVER_AGE, ServerStatus.SERVER_AGE_ALL);
                            }

                            sendPacket(st);

                            // Send list of currently online players.
                            final List<String> playerList = new ArrayList<>();
                            for (Player player : World.getPlayers()) {
                                if (!player.isInOfflineMode()) {
                                    playerList.add(player.getAccountName());
                                }
                            }

                            if (!playerList.isEmpty()) {
                                sendPacket(new PlayerInGame(playerList));
                            }
                            break;
                        }
                        case 0x03: // PlayerAuthResponse - Player authentication result.
                        {
                            final PlayerAuthResponse par = new PlayerAuthResponse(incoming);
                            final String account = par.getAccount();
                            WaitingClient wcToRemove = null;

                            // Find the waiting client for this account.
                            synchronized (waitingClients) {
                                for (WaitingClient wc : waitingClients) {
                                    if (wc.account.equals(account)) {
                                        wcToRemove = wc;
                                        break;
                                    }
                                }
                            }

                            if (wcToRemove != null) {
                                if (par.isAuthed()) {
                                    // Authentication successful - setup client.
                                    final PlayerInGame pig = new PlayerInGame(par.getAccount());
                                    sendPacket(pig);
                                    wcToRemove.gameClient.setConnectionState(ConnectionState.AUTHENTICATED);
                                    wcToRemove.gameClient.setSessionId(wcToRemove.sessionKey);
                                    final CharSelectionInfo cl = new CharSelectionInfo(
                                            wcToRemove.account, wcToRemove.gameClient.getSessionId().playOkID1);
                                    wcToRemove.gameClient.sendPacket(cl);
                                    wcToRemove.gameClient.setCharSelection(cl.getCharInfo());
                                } else {
                                    // Authentication failed - close connection.
                                    log.warn(
                                            "Session key incorrect. Closing connection for account {}",
                                            wcToRemove.account);
                                    wcToRemove.gameClient.close(new LoginFail(LoginFail.SYSTEM_ERROR_LOGIN_LATER));
                                    sendLogout(wcToRemove.account);
                                }

                                waitingClients.remove(wcToRemove);
                            }
                            break;
                        }
                        case 0x04: // KickPlayer - Force disconnect player.
                        {
                            final KickPlayer kp = new KickPlayer(incoming);
                            doKickPlayer(kp.getAccount());
                            break;
                        }
                        case 0x05: // RequestCharacters - Get character info for account.
                        {
                            final RequestCharacters rc = new RequestCharacters(incoming);
                            getCharsOnServer(rc.getAccount());
                            break;
                        }
                        case 0x06: // ChangePasswordResponse - Password change result.
                        {
                            new ChangePasswordResponse(incoming);
                            break;
                        }
                    }
                }
            } catch (UnknownHostException e) {
                log.warn("Unknown host: ", e);
            } catch (SocketException e) {
                log.warn("LoginServer not available, trying to reconnect...");
            } catch (IOException e) {
                log.warn("Disconnected from Login, trying to reconnect: ", e);
            } finally {
                // Clean up connection resources.
                try {
                    socket.close();
                    if (isInterrupted()) {
                        return;
                    }
                } catch (Exception e) {
                    // Ignore cleanup exceptions.
                }
            }

            // Wait before attempting reconnection.
            try {
                Thread.sleep(RECONNECT_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Adds waiting client and sends authentication request to login server.
     * @param accountName the account name
     * @param client the game client
     * @param key the session key
     */
    public void addWaitingClientAndSendRequest(String accountName, GameClient client, SessionKey key) {
        synchronized (waitingClients) {
            waitingClients.add(new WaitingClient(accountName, client, key));
        }

        sendPacket(new PlayerAuthRequest(accountName, key));
    }

    /**
     * Removes waiting client from authentication queue.
     * @param client the game client to remove
     */
    public void removeWaitingClient(GameClient client) {
        WaitingClient toRemove = null;
        synchronized (waitingClients) {
            for (WaitingClient c : waitingClients) {
                if (c.gameClient == client) {
                    toRemove = c;
                    break;
                }
            }

            if (toRemove != null) {
                waitingClients.remove(toRemove);
            }
        }
    }

    /**
     * Sends logout notification for specified account.
     * @param account the account name
     */
    public void sendLogout(String account) {
        if (account == null) {
            return;
        }

        final GameClient removed = accountsInGameServer.remove(account);
        if (removed != null) {
            removed.disconnect();
        }

        sendPacket(new PlayerLogout(account));
    }

    /**
     * Adds game server login entry for account tracking.
     * @param account the account name
     * @param client the game client
     * @return true if account was not already logged in, false otherwise
     */
    public boolean addGameServerLogin(String account, GameClient client) {
        return accountsInGameServer.putIfAbsent(account, client) == null;
    }

    /**
     * Sends access level change notification to login server.
     * @param account the account name
     * @param level the new access level
     */
    public void sendAccessLevel(String account, int level) {
        sendPacket(new ChangeAccessLevel(account, level));
    }

    /**
     * Sends client trace route information to login server.
     * @param account the account name
     * @param address the trace route addresses (5 hops)
     */
    public void sendClientTracert(String account, String[] address) {
        sendPacket(new PlayerTracert(account, address[0], address[1], address[2], address[3], address[4]));
    }

    /**
     * Sends mail notification to login server.
     * @param account the account name
     * @param mailId the mail identifier
     * @param args additional mail arguments
     */
    public void sendMail(String account, String mailId, String... args) {
        sendPacket(new SendMail(account, mailId, args));
    }

    /**
     * Sends temporary ban notification to login server.
     * @param account the account name
     * @param ip the IP address to ban
     * @param time the ban duration in milliseconds
     */
    public void sendTempBan(String account, String ip, long time) {
        sendPacket(new TempBan(account, ip, time));
    }

    /**
     * Converts hex byte array to hexadecimal string representation.
     * @param hex the hex byte array
     * @return the hex string
     */
    private String hexToString(byte[] hex) {
        return new BigInteger(hex).toString(16);
    }

    private static byte[] generateHexBytes(int size) {
        final byte[] array = new byte[size];
        KEY_RANDOM.nextBytes(array);
        for (int i = 0; i < array.length; i++) {
            while (array[i] == 0) {
                array[i] = (byte) KEY_RANDOM.nextInt(Byte.MAX_VALUE);
            }
        }

        return array;
    }

    /**
     * Forcibly kicks player from the game server.
     * @param account the account name to kick
     */
    private void doKickPlayer(String account) {
        final GameClient client = accountsInGameServer.get(account);
        if (client != null) {
            final SystemMessage msg =
                    new SystemMessage(SystemMessageId.ANOTHER_PERSON_HAS_LOGGED_IN_WITH_THE_SAME_ACCOUNT);

            if (client.isDetached()) {
                final Player player = client.getPlayer();
                if (player != null) {
                    player.storeMe();
                    player.deleteMe();
                }

                client.close(msg);
            } else {
                Disconnection.of(client).storeAndDeleteWith(msg);
                ACCOUNTING_LOG.info("Kicked by login, {}", client);
            }
        }

        sendLogout(account);
    }

    /**
     * Retrieves character count and deletion times for specified account.
     * @param account the account name
     */
    private void getCharsOnServer(String account) {
        int chars = 0;
        final List<Long> charToDel = new ArrayList<>();

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT deletetime FROM characters WHERE account_name=?")) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chars++;
                    final long delTime = rs.getLong("deletetime");
                    if (delTime != 0) {
                        charToDel.add(delTime);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Exception in getCharsOnServer: {}", e.getMessage(), e);
        }

        sendPacket(new ReplyCharacters(account, chars, charToDel));
    }

    /**
     * Sends packet to login server with proper encryption and checksums.
     * @param packet the packet to send
     */
    private void sendPacket(SimpleWritablePacket packet) {
        if ((blowfish == null) || (socket == null) || socket.isClosed()) {
            return;
        }

        try {
            packet.write(); // Write initial packet data.
            packet.writeInt(0); // Reserved space for checksum.
            int size = packet.getLength() - 2; // Size without header.

            // Add padding to align packet to 8-byte boundary.
            final int padding = size % PACKET_PADDING;
            if (padding != 0) {
                for (int i = padding; i < PACKET_PADDING; i++) {
                    packet.writeByte(0);
                }
            }

            // Get final packet data for encryption.
            final byte[] data = packet.getSendableBytes();
            size = data.length - 2; // Data size without header.

            synchronized (out) {
                XorChecksum.append(data, 2, size);
                blowfish.encrypt(data, 2, size);

                out.write(data);
                try {
                    out.flush();
                } catch (IOException e) {
                    // LoginServer might have terminated connection.
                }
            }
        } catch (Exception e) {
            log.error("IOException while sending packet {}", packet.getClass().getSimpleName(), e);
        }
    }

    /**
     * Sets maximum player count and notifies login server.
     * @param maxPlayer the maximum player count
     */
    public void setMaxPlayer(int maxPlayer) {
        sendServerStatus(ServerStatus.MAX_PLAYERS, maxPlayer);
        this.maxPlayer = maxPlayer;
    }

    /**
     * Sends server status update to login server.
     * @param id the status attribute identifier
     * @param value the status value
     */
    public void sendServerStatus(int id, int value) {
        final ServerStatus serverStatus = new ServerStatus();
        serverStatus.addAttribute(id, value);
        sendPacket(serverStatus);
    }

    /**
     * Sends server type configuration to login server.
     */
    public void sendServerType() {
        final ServerStatus serverStatus = new ServerStatus();
        serverStatus.addAttribute(ServerStatus.SERVER_TYPE, ServerConfig.SERVER_LIST_TYPE);
        sendPacket(serverStatus);
    }

    /**
     * Sends password change request to login server.
     * @param accountName the account name
     * @param charName the character name
     * @param oldpass the current password
     * @param newpass the new password
     */
    public void sendChangePassword(String accountName, String charName, String oldpass, String newpass) {
        sendPacket(new ChangePassword(accountName, charName, oldpass, newpass));
    }

    /**
     * Gets server status as human-readable string.
     * @return the status string representation
     */
    public String getStatusString() {
        return ServerStatus.STATUS_STRING[serverStatus];
    }

    /**
     * Sets server status and notifies login server.
     * @param status the new server status
     */
    public void setServerStatus(int status) {
        switch (status) {
            case ServerStatus.STATUS_AUTO:
            case ServerStatus.STATUS_DOWN:
            case ServerStatus.STATUS_FULL:
            case ServerStatus.STATUS_GM_ONLY:
            case ServerStatus.STATUS_GOOD:
            case ServerStatus.STATUS_NORMAL: {
                sendServerStatus(ServerStatus.SERVER_LIST_STATUS, status);
                serverStatus = status;
                break;
            }
            default: {
                throw new IllegalArgumentException("Invalid server status: " + status);
            }
        }
    }

    /**
     * Gets game client for specified account name.
     * @param name the account name
     * @return the game client or null if not found
     */
    public GameClient getClient(String name) {
        return name != null ? accountsInGameServer.get(name) : null;
    }

    /**
     * Session key container for player authentication between login and game servers.
     */
    public static class SessionKey {
        public int playOkID1;
        public int playOkID2;
        public int loginOkID1;
        public int loginOkID2;

        public SessionKey(int loginOK1, int loginOK2, int playOK1, int playOK2) {
            playOkID1 = playOK1;
            playOkID2 = playOK2;
            loginOkID1 = loginOK1;
            loginOkID2 = loginOK2;
        }

        @Override
        public String toString() {
            return "PlayOk: " + playOkID1 + " " + playOkID2 + " LoginOk:" + loginOkID1 + " " + loginOkID2;
        }
    }

    /**
     * Represents a client waiting for authentication response from login server.
     */
    private static class WaitingClient {
        public String account;
        public GameClient gameClient;
        public SessionKey sessionKey;

        public WaitingClient(String acc, GameClient client, SessionKey key) {
            account = acc;
            gameClient = client;
            sessionKey = key;
        }
    }

    public static LoginServerThread getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final LoginServerThread INSTANCE = new LoginServerThread();
    }
}
