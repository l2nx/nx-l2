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
package app.l2nx.login.gslink;

import app.l2nx.login.LoginServer;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.ScrambledKeyPair;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.network.GameServerPacketHandler;
import app.l2nx.login.network.GameServerPacketHandler.GameServerState;
import app.l2nx.login.network.gameserverpackets.send.ChangePasswordResponse;
import app.l2nx.login.network.gameserverpackets.send.InitLS;
import app.l2nx.login.network.gameserverpackets.send.KickPlayer;
import app.l2nx.login.network.gameserverpackets.send.LoginServerFail;
import app.l2nx.login.network.gameserverpackets.send.RequestCharacters;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.PacketCipher;
import app.l2nx.network.crypt.XorChecksum;
import app.l2nx.network.packet.SimpleWritablePacket;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles connection and communication between login server and game server.<br>
 * Manages authentication, packet exchange and player account tracking for a single game server connection.
 * <ul>
 * <li>RSA key exchange for secure communication</li>
 * <li>Blowfish encryption setup for packet encryption</li>
 * <li>Game server authentication and registration</li>
 * <li>Continuous packet processing and forwarding</li>
 * <li>Player account management and tracking</li>
 * </ul>
 * @author -Wooden-, KenM, Mobius
 */
@Slf4j
public class GameServerLink extends Thread {

    // Constants.
    private static final int PACKET_HEADER_SIZE = 2;
    private static final int BLOWFISH_BLOCK_SIZE = 8;
    private static final int HIGH_BYTE_MULTIPLIER = 256;
    private static final String DEFAULT_BLOWFISH_KEY = "_;v.]05-31!|+-%xT!^[$\00";

    // Player Account Management.
    private final Set<UUID> accountsOnGameServer = ConcurrentHashMap.newKeySet();

    // Network Communication.
    private final Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    // Cryptography.
    private final RSAPublicKey publicKey;

    @Getter
    private final RSAPrivateKey privateKey;

    private PacketCipher blowfishCipher;

    // Connection State Management.
    private final String connectionIp;

    @Getter
    private String connectionIpAddress;

    @Getter
    @Setter
    private GameServerInfo gameServerInfo;

    @Getter
    @Setter
    private GameServerState loginConnectionState = GameServerState.CONNECTED;

    /**
     * Constructs a new GameServerLink for handling communication with a game server.<br>
     * Initializes the network streams, RSA key pair and Blowfish encryption. The thread automatically starts upon construction.
     * @param socket the TCP socket connection to the game server
     */
    public GameServerLink(Socket socket) {
        this.socket = socket;
        connectionIp = socket.getInetAddress().getHostAddress();

        try {
            // Initialize network streams for communication.
            inputStream = this.socket.getInputStream();
            outputStream = new BufferedOutputStream(this.socket.getOutputStream());
        } catch (IOException e) {
            log.warn("Failed to initialize network streams - {}", e.getMessage());
        }

        // Retrieve RSA key pair for secure initial communication.
        final ScrambledKeyPair keyPair = LoginController.getInstance().getScrambledRSAKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivateKey();
        publicKey = (RSAPublicKey) keyPair.getPublicKey();

        // Initialize Blowfish cipher with default key for packet encryption.
        blowfishCipher = new LittleEndianBlowfishCipher(DEFAULT_BLOWFISH_KEY.getBytes(StandardCharsets.UTF_8));

        // Set thread name for easier debugging and monitoring.
        // Java 18: setName(getClass().getSimpleName() + "-" + getId() + "@" + _connectionIp);
        // Java 19+: Using threadId() method.
        setName(getClass().getSimpleName() + "-" + threadId() + "@" + connectionIp);

        // Start the thread to begin processing.
        start();
    }

    /**
     * Main thread execution method that handles the game server connection lifecycle.<br>
     * Processes incoming packets continuously until connection is terminated.
     */
    @Override
    public void run() {
        // Resolve and store the connection IP address.
        connectionIpAddress = socket.getInetAddress().getHostAddress();

        // Check if the connecting IP is banned.
        if (isBannedGameserverIP(connectionIpAddress)) {
            log.info(
                    "GameServerRegistration: IP Address {} is on the banned IP list. Connection rejected.",
                    connectionIpAddress);
            forceClose(LoginServerFail.REASON_IP_BANNED);
            return; // Terminate processing for banned IP.
        }

        // Send initial packet with RSA public key for secure key exchange.
        final InitLS initializationPacket = new InitLS(publicKey.getModulus().toByteArray());
        try {
            sendPacket(initializationPacket);

            // Main packet processing loop.
            int lengthHighByte = 0;
            int lengthLowByte = 0;
            int packetLength = 0;
            boolean checksumValid = false;

            while (true) {
                // Read packet length header (2 bytes: low byte, high byte).
                lengthLowByte = inputStream.read();
                lengthHighByte = inputStream.read();
                packetLength = (lengthHighByte * HIGH_BYTE_MULTIPLIER) + lengthLowByte;

                // Check for connection termination.
                if ((lengthHighByte < 0) || socket.isClosed()) {
                    log.trace("Game server terminated the connection gracefully.");
                    break;
                }

                // Allocate buffer for packet data (excluding header).
                final byte[] packetData = new byte[packetLength - PACKET_HEADER_SIZE];
                int totalBytesReceived = 0;
                int bytesRead = 0;
                int remainingBytes = packetLength - PACKET_HEADER_SIZE;

                // Read the complete packet data.
                while ((bytesRead != -1) && (totalBytesReceived < (packetLength - PACKET_HEADER_SIZE))) {
                    bytesRead = inputStream.read(packetData, totalBytesReceived, remainingBytes);
                    totalBytesReceived += bytesRead;
                    remainingBytes -= bytesRead;
                }

                // Validate that we received the complete packet.
                if (totalBytesReceived != (packetLength - PACKET_HEADER_SIZE)) {
                    log.warn(
                            "Incomplete packet received from game server. Expected {} bytes, got {}. Closing connection.",
                            (packetLength - PACKET_HEADER_SIZE),
                            totalBytesReceived);
                    break;
                }

                if ((packetData.length % 8) != 0) {
                    log.warn(
                            "Packet length {} from game server is not a multiple of the cipher block. Closing connection.",
                            packetData.length);
                    break;
                }

                // Decrypt the packet data using Blowfish cipher.
                blowfishCipher.decrypt(packetData, 0, packetData.length);

                // Verify packet integrity using checksum.
                checksumValid = XorChecksum.verify(packetData, 0, packetData.length);
                if (!checksumValid) {
                    log.warn(
                            "Packet checksum verification failed. Possible data corruption or tampering detected. Closing connection.");
                    return;
                }

                // Forward the validated packet to the packet handler.
                GameServerPacketHandler.handle(packetData, this);
            }
        } catch (IOException e) {
            // Log connection loss with server identification.
            final String serverIdentification = getServerId() != -1
                    ? "[" + getServerId() + "] " + GameServerTable.getInstance().getServerNameById(getServerId())
                    : "(" + connectionIpAddress + ")";
            final String connectionLostMessage =
                    "GameServer " + serverIdentification + ": Connection lost - " + e.getMessage();
            log.info(connectionLostMessage);
        } finally {
            // Cleanup operations when thread terminates.
            if (isAuthed()) {
                // Mark the game server as offline.
                if (gameServerInfo != null) {
                    gameServerInfo.setDown();
                }

                log.info(
                        "Server [{}] {} is now marked as disconnected.",
                        getServerId(),
                        GameServerTable.getInstance().getServerNameById(getServerId()));
            }

            // Remove this game server from active connections and cleanup flood protection.
            LoginServer.getInstance().getGameServerListener().removeGameServer(this);
            LoginServer.getInstance().getGameServerListener().removeFloodProtection(connectionIp);
        }
    }

    /**
     * Checks if a specific account is currently logged into this game server.
     * @return true if the account is logged into this game server, false otherwise
     */
    public boolean hasAccountOnGameServer(UUID accountId) {
        return accountsOnGameServer.contains(accountId);
    }

    /**
     * Returns the current number of players logged into this game server.
     * @return the count of active player accounts on this game server
     */
    public int getPlayerCount() {
        return accountsOnGameServer.size();
    }

    /**
     * Attaches GameServerInfo to this thread and configures the game server settings.<br>
     * This method completes the game server registration process by:
     * <ul>
     * <li>Linking the GameServerInfo object to this thread</li>
     * <li>Setting the server's network configuration (port and hosts)</li>
     * <li>Configuring player capacity limits</li>
     * <li>Marking the server as authenticated and ready to accept players</li>
     * </ul>
     * @param gameServerInfo the GameServerInfo object to attach
     * @param port the port number the game server is listening on
     * @param hosts array of host addresses (pairs of host and subnet)
     * @param maxPlayers maximum number of players the server can accommodate
     */
    public void attachGameServerInfo(GameServerInfo gameServerInfo, int port, String[] hosts, int maxPlayers) {
        // Establish bidirectional relationship between thread and server info.
        setGameServerInfo(gameServerInfo);
        gameServerInfo.setGameServerLink(this);

        // Configure server network settings.
        gameServerInfo.setPort(port);
        setGameHosts(hosts);

        // Set player capacity and mark as authenticated.
        gameServerInfo.setMaxPlayers(maxPlayers);
        gameServerInfo.setAuthed(true);
    }

    /**
     * Forces the connection to close immediately and sends a failure reason to the game server.<br>
     * This method is used when the game server needs to be disconnected due to violations such as IP banning, authentication failures, or other security issues.
     * @param reasonCode the reason code for the disconnection (from LoginServerFail constants)
     */
    public void forceClose(int reasonCode) {
        // Send failure notification to the game server.
        sendPacket(new LoginServerFail(reasonCode));

        try {
            // Close the socket connection immediately.
            socket.close();
        } catch (IOException e) {
            log.trace("Failed to close socket for banned server. Socket may already be closed - {}", e.getMessage());
        }
    }

    /**
     * Checks if the specified IP address is banned from connecting as a game server.<br>
     * This method can be extended to implement IP-based access control.
     * @param ipAddress the IP address to check
     * @return true if the IP address is banned, false otherwise
     */
    public static boolean isBannedGameserverIP(String ipAddress) {
        // TODO: Implement IP banning logic here.
        // This could check against a database table, configuration file, or in-memory cache.
        return false;
    }

    /**
     * Sends a packet to the connected game server with proper encryption and checksums.<br>
     * The packet is automatically encrypted using Blowfish cipher and padded to the required block size. A checksum is appended for integrity verification.
     * @param packet the packet to send to the game server
     */
    public void sendPacket(SimpleWritablePacket packet) {
        // Verify connection is still valid.
        if ((blowfishCipher == null) || (socket == null) || socket.isClosed()) {
            return;
        }

        try {
            // Prepare packet data.
            packet.write(); // Write the packet content.
            packet.writeInt(0); // Reserve space for checksum.

            int dataSize = packet.getLength() - PACKET_HEADER_SIZE; // Size without header.
            final int paddingNeeded = dataSize % BLOWFISH_BLOCK_SIZE; // Calculate padding for 8-byte blocks.

            // Add padding bytes to align with Blowfish block size.
            if (paddingNeeded != 0) {
                for (int i = paddingNeeded; i < BLOWFISH_BLOCK_SIZE; i++) {
                    packet.writeByte(0);
                }
            }

            // Get the complete packet data including header.
            final byte[] packetData = packet.getSendableBytes();
            dataSize = packetData.length - PACKET_HEADER_SIZE; // Recalculate size after padding.

            // Thread-safe packet transmission.
            synchronized (outputStream) {
                // Append checksum for integrity verification.
                XorChecksum.append(packetData, PACKET_HEADER_SIZE, dataSize);

                // Encrypt the data portion (excluding header).
                blowfishCipher.encrypt(packetData, PACKET_HEADER_SIZE, dataSize);

                // Send the complete packet.
                outputStream.write(packetData);
                try {
                    outputStream.flush();
                } catch (IOException e) {
                    // Game server might have terminated connection during flush.
                    log.trace("Failed to flush output stream. Game server may have disconnected - {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error(
                    "IOException occurred while sending packet {}",
                    packet.getClass().getSimpleName(),
                    e);
        }
    }

    /**
     * Sends a kick command to the game server to disconnect a specific player.<br>
     * This is typically used when a player violates login server policies or when administrative action is required.
     */
    public void kickPlayer(UUID accountId) {
        sendPacket(new KickPlayer(accountId));
    }

    /**
     * Requests character information for a specific account from the game server.<br>
     * This is used during the login process to retrieve character list data.
     */
    public void requestCharacters(UUID accountId) {
        sendPacket(new RequestCharacters(accountId));
    }

    /**
     * Sends a password change response to the game server for a specific character.<br>
     * This notifies the game server about the result of a password change operation.
     * @param characterName the name of the character whose password was changed
     * @param responseMessage the message to send back to the client
     */
    public void changePasswordResponse(String characterName, String responseMessage) {
        sendPacket(new ChangePasswordResponse(characterName, responseMessage));
    }

    /**
     * Configures the game server's host addresses and subnets.<br>
     * Updates the server's network configuration and logs the changes for monitoring. Host addresses are provided in pairs (host, subnet mask).
     * @param hosts array of host addresses in pairs (host1, subnet1, host2, subnet2, ...)
     */
    public void setGameHosts(String[] hosts) {
        log.info(
                "Updated Gameserver [{}] {} IP's:",
                getServerId(),
                GameServerTable.getInstance().getServerNameById(getServerId()));

        // Clear existing server addresses.
        gameServerInfo.clearServerAddresses();

        // Process host pairs (host, subnet).
        for (int i = 0; i < hosts.length; i += 2) {
            try {
                gameServerInfo.addServerAddress(hosts[i], hosts[i + 1]);
            } catch (Exception e) {
                log.warn("Failed to resolve hostname \"{}\" - {}", hosts[i], e.getMessage());
            }
        }

        // Log all configured server addresses.
        for (String serverAddress : gameServerInfo.getServerAddresses()) {
            log.info(serverAddress);
        }
    }

    /**
     * Checks if this game server thread is authenticated and ready to handle players.
     * @return true if the game server is authenticated, false otherwise
     */
    public boolean isAuthed() {
        if (gameServerInfo == null) {
            return false;
        }

        return gameServerInfo.isAuthed();
    }

    /**
     * Returns the unique server ID of this game server.
     * @return the server ID, or -1 if not assigned
     */
    public int getServerId() {
        if (gameServerInfo != null) {
            return gameServerInfo.getId();
        }

        return -1;
    }

    /**
     * Updates the Blowfish cipher used for packet encryption.<br>
     * This is typically called after the initial key exchange to use the session key.
     * @param blowfishCipher the new Blowfish cipher to use
     */
    public void setBlowFish(PacketCipher blowfishCipher) {
        this.blowfishCipher = blowfishCipher;
    }

    /**
     * Adds an account to the set of players logged into this game server.<br>
     * Also removes the account from the login server's authenticated client list to prevent duplicate logins.
     */
    public void addAccountOnGameServer(UUID accountId) {
        accountsOnGameServer.add(accountId);
        LoginController.getInstance().removeAuthedLoginClient(accountId);
    }

    /**
     * Removes an account from the set of players logged into this game server.<br>
     * Also cleans up the account from the login server's authenticated client list.
     */
    public void removeAccountOnGameServer(UUID accountId) {
        accountsOnGameServer.remove(accountId);
        LoginController.getInstance().removeAuthedLoginClient(accountId);
    }
}
