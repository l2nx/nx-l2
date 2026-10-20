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
package app.l2nx.highfive.network.gslink;

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.ListingAttribute;
import app.l2nx.highfive.login.LoginPort;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.command.AuthenticationResult;
import app.l2nx.highfive.login.command.KickAccount;
import app.l2nx.highfive.login.command.PasswordChangeResult;
import app.l2nx.highfive.login.command.RegistrationResult;
import app.l2nx.highfive.login.command.ReportCharacters;
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
import app.l2nx.highfive.network.loginserverpackets.send.ServerStatus;
import app.l2nx.highfive.network.loginserverpackets.send.TempBan;
import app.l2nx.highfive.repository.LegacyValues;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The game server's end of the link to the login server: carries the {@link LoginPort} calls out and hands what the
 * login server sends to the commands of the login slice.
 */
@Slf4j
@Component
public class LoginServerLink extends Thread implements LoginPort {

    // Protocol constants.
    private static final int REVISION = 0x0107; // @see app.l2nx.login.LoginServer#PROTOCOL_REV
    private static final int RECONNECT_DELAY = 5000; // 5 seconds.
    private static final int BLOWFISH_KEY_SIZE = 40;
    private static final int HEX_ID_SIZE = 16;
    private static final int PACKET_PADDING = 8;
    private static final SecureRandom KEY_RANDOM = new SecureRandom();

    private static volatile LoginServerLink instance;

    private final Consumer<RegistrationResult> registrationResults;
    private final Consumer<AuthenticationResult> authenticationResults;
    private final Consumer<KickAccount> kicks;
    private final Consumer<ReportCharacters> characterReports;
    private final Consumer<PasswordChangeResult> passwordChangeResults;

    // Connection configuration.
    private String hostname;
    private int port;
    private int gamePort;
    private boolean acceptAlternate;
    private boolean reserveHost;
    private List<String> subnets;
    private List<String> hosts;
    private int maxPlayers;

    // Connection state.
    private Socket socket;
    private OutputStream out;
    private PacketCipher blowfish;
    private byte[] hexID;
    private int requestID;

    // The handlers answer through this link, so Spring creates the link first and resolves a handler on first use.
    public LoginServerLink(
            @Lazy Consumer<RegistrationResult> registrationResults,
            @Lazy Consumer<AuthenticationResult> authenticationResults,
            @Lazy Consumer<KickAccount> kicks,
            @Lazy Consumer<ReportCharacters> characterReports,
            @Lazy Consumer<PasswordChangeResult> passwordChangeResults) {
        super("LoginServerLink");
        this.registrationResults = registrationResults;
        this.authenticationResults = authenticationResults;
        this.kicks = kicks;
        this.characterReports = characterReports;
        this.passwordChangeResults = passwordChangeResults;
        instance = this;
    }

    /**
     * Reads the connection settings; call after the configuration is loaded and before {@link #start()}.
     */
    public void load() {
        // Initialize connection settings.
        port = ServerConfig.GAME_SERVER_LOGIN_PORT;
        gamePort = ServerConfig.PORT_GAME;
        hostname = ServerConfig.GAME_SERVER_LOGIN_HOST;
        acceptAlternate = ServerConfig.ACCEPT_ALTERNATE_ID;
        reserveHost = ServerConfig.RESERVE_HOST_ON_LOGIN;
        subnets = ServerConfig.GAME_SERVER_SUBNETS;
        hosts = ServerConfig.GAME_SERVER_HOSTS;
        maxPlayers = ServerConfig.MAXIMUM_ONLINE_USERS;

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
                                    maxPlayers,
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
                            final AuthResponse response = new AuthResponse(incoming);
                            registrationResults.accept(RegistrationResult.builder()
                                    .serverId(response.getServerId())
                                    .serverName(response.getServerName())
                                    .hexId(hexToString(hexID))
                                    .build());
                            break;
                        }
                        case 0x03: // PlayerAuthResponse - Player authentication result.
                        {
                            final PlayerAuthResponse response = new PlayerAuthResponse(incoming);
                            authenticationResults.accept(AuthenticationResult.builder()
                                    .login(response.getLogin())
                                    .accountId(response.getAccountId())
                                    .accepted(response.isAuthed())
                                    .build());
                            break;
                        }
                        case 0x04: // KickPlayer - Force disconnect player.
                        {
                            kicks.accept(new KickAccount(new KickPlayer(incoming).getAccountId()));
                            break;
                        }
                        case 0x05: // RequestCharacters - Get character info for account.
                        {
                            characterReports.accept(
                                    new ReportCharacters(new RequestCharacters(incoming).getAccountId()));
                            break;
                        }
                        case 0x06: // ChangePasswordResponse - Password change result.
                        {
                            final ChangePasswordResponse response = new ChangePasswordResponse(incoming);
                            passwordChangeResults.accept(PasswordChangeResult.builder()
                                    .characterName(response.getCharacterName())
                                    .message(response.getMessage())
                                    .build());
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

    @Override
    public void authenticate(String login, SessionKey key) {
        sendPacket(new PlayerAuthRequest(login, key));
    }

    @Override
    public void accountsEntered(List<UUID> accountIds) {
        sendPacket(new PlayerInGame(accountIds));
    }

    @Override
    public void accountLeft(UUID accountId) {
        sendPacket(new PlayerLogout(accountId));
    }

    @Override
    public void changeAccessLevel(UUID accountId, int level) {
        sendPacket(new ChangeAccessLevel(accountId, level));
    }

    @Override
    public void reportRoute(UUID accountId, List<String> route) {
        sendPacket(new PlayerTracert(accountId, route.get(0), route.get(1), route.get(2), route.get(3), route.get(4)));
    }

    @Override
    public void requestTempBan(UUID accountId, String ip, Duration duration) {
        sendPacket(new TempBan(accountId, ip, duration.toMinutes()));
    }

    @Override
    public void changePassword(UUID accountId, String characterName, String oldPassword, String newPassword) {
        sendPacket(new ChangePassword(accountId, characterName, oldPassword, newPassword));
    }

    @Override
    public void reportCharacters(UUID accountId, int count, List<Instant> deleteTimes) {
        sendPacket(new ReplyCharacters(
                accountId,
                count,
                deleteTimes.stream().map(LegacyValues::epochMillisOrZero).toList()));
    }

    @Override
    public void publishListing(Map<ListingAttribute, Integer> attributes) {
        final ServerStatus status = new ServerStatus();
        attributes.forEach((attribute, value) -> status.addAttribute(attributeId(attribute), value));
        sendPacket(status);
    }

    private static int attributeId(ListingAttribute attribute) {
        return switch (attribute) {
            case STATUS -> ServerStatus.SERVER_LIST_STATUS;
            case TYPE -> ServerStatus.SERVER_TYPE;
            case SQUARE_BRACKET -> ServerStatus.SERVER_LIST_SQUARE_BRACKET;
            case MAX_PLAYERS -> ServerStatus.MAX_PLAYERS;
            case AGE -> ServerStatus.SERVER_AGE;
        };
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

    // TODO: callers receive the bean through their constructor (#41).
    public static LoginServerLink getInstance() {
        final LoginServerLink current = instance;
        if (current == null) {
            throw new IllegalStateException("LoginServerLink is used before the Spring context created it.");
        }
        return current;
    }
}
