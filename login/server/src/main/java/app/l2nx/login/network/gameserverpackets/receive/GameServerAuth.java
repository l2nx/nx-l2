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
package app.l2nx.login.network.gameserverpackets.receive;

import app.l2nx.login.LoginServer;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.GameServerPacketHandler.GameServerState;
import app.l2nx.login.network.gameserverpackets.send.AuthResponse;
import app.l2nx.login.network.gameserverpackets.send.LoginServerFail;
import app.l2nx.network.packet.SimpleReadablePacket;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates a game server connection and binds it to a registered server entry.<br>
 * The request publishes the server id, hexId and host pairs used for network routing, then completes registration.
 * <ul>
 * <li>Reads and validates packet sizes to avoid oversized allocations.</li>
 * <li>Matches or registers the server entry and attaches connection metadata.</li>
 * <li>Replies with AuthResponse and transitions the connection to AUTHED on success.</li>
 * </ul>
 * @author BazookaRpm
 */
@Slf4j
public class GameServerAuth extends SimpleReadablePacket {

    // Packet Limits.
    private static final int MAX_HEX_ID_BYTES = 64;
    private static final int MAX_HOST_PAIRS = 32;
    private static final int HOST_STRINGS_PER_PAIR = 2;

    // Connection Context.
    private final GameServerLink server;

    // Packet Data.
    private final int desiredServerId;
    private final boolean acceptAlternativeId;
    private final int port;
    private final int maxPlayers;
    private final byte[] hexId;
    private final String[] hosts;

    /**
     * Reads the authentication packet and completes the registration process when valid.<br>
     * Invalid payload sizes are rejected early to reduce DoS impact.
     * @param decrypt
     * @param server
     */
    public GameServerAuth(byte[] decrypt, GameServerLink server) {
        super(decrypt);
        readByte(); // Packet id, it is already processed.

        this.server = server;

        final int desiredServerId = readByte();
        final boolean acceptAlternativeId = readByte() != 0;
        readByte(); // Reserved host flag, it is not used.

        final int port = readShort();
        final int maxPlayers = readInt();

        byte[] hexId = new byte[0];
        String[] hosts = new String[0];

        final int hexIdSize = readInt();
        if ((hexIdSize < 0) || (hexIdSize > MAX_HEX_ID_BYTES)) {
            this.desiredServerId = desiredServerId;
            this.acceptAlternativeId = acceptAlternativeId;
            this.port = port;
            this.maxPlayers = maxPlayers;
            this.hexId = hexId;
            this.hosts = hosts;

            log.warn(
                    "Rejected GS auth due to invalid hexIdSize={} for desiredServerId={} (maxHexIdBytes={}).",
                    hexIdSize,
                    desiredServerId,
                    MAX_HEX_ID_BYTES);
            this.server.forceClose(LoginServerFail.REASON_WRONG_HEXID);
            return;
        }

        if (hexIdSize != 0) {
            hexId = readBytes(hexIdSize);
        }

        final int hostPairs = readInt();
        if ((hostPairs < 0) || (hostPairs > MAX_HOST_PAIRS)) {
            this.desiredServerId = desiredServerId;
            this.acceptAlternativeId = acceptAlternativeId;
            this.port = port;
            this.maxPlayers = maxPlayers;
            this.hexId = hexId;
            this.hosts = hosts;

            log.warn(
                    "Rejected GS auth due to invalid hostPairs={} for desiredServerId={} (maxHostPairs={}).",
                    hostPairs,
                    desiredServerId,
                    MAX_HOST_PAIRS);
            this.server.forceClose(LoginServerFail.REASON_WRONG_HEXID);
            return;
        }

        final int hostStringCount = hostPairs * HOST_STRINGS_PER_PAIR;
        if (hostStringCount != 0) {
            hosts = new String[hostStringCount];
            for (int i = 0; i < hostStringCount; i++) {
                hosts[i] = readString();
            }
        }

        this.desiredServerId = desiredServerId;
        this.acceptAlternativeId = acceptAlternativeId;
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.hexId = hexId;
        this.hosts = hosts;

        if (handleRegProcess()) {
            this.server.sendPacket(
                    new AuthResponse(this.server.getGameServerInfo().getId()));
            this.server.setLoginConnectionState(GameServerState.AUTHED);
        }
    }

    private boolean handleRegProcess() {
        final GameServerTable gameServerTable = GameServerTable.getInstance();
        final int desiredId = desiredServerId;

        final GameServerInfo registeredInfo = gameServerTable.getRegisteredGameServerById(desiredId);
        if (registeredInfo == null) {
            if (!LoginServer.getInstance().getProperties().gameServer().acceptNew()) {
                log.info(
                        "Rejected GS auth for desiredServerId={} because new game servers are not accepted.",
                        desiredId);
                server.forceClose(LoginServerFail.REASON_WRONG_HEXID);
                return false;
            }

            final GameServerInfo newInfo = new GameServerInfo(desiredId, hexId, server);
            if (!gameServerTable.register(desiredId, newInfo)) {
                log.info(
                        "Rejected GS auth for desiredServerId={} because the id became reserved during registration.",
                        desiredId);
                server.forceClose(LoginServerFail.REASON_ID_RESERVED);
                return false;
            }

            server.attachGameServerInfo(newInfo, port, hosts, maxPlayers);
            gameServerTable.registerServerOnDB(newInfo);
            return true;
        }

        if (Arrays.equals(registeredInfo.getHexId(), hexId)) {
            synchronized (registeredInfo) {
                if (registeredInfo.isAuthed()) {
                    log.info(
                            "Rejected GS auth for desiredServerId={} due to already authenticated session.", desiredId);
                    server.forceClose(LoginServerFail.REASON_ALREADY_LOGGED8IN);
                    return false;
                }

                server.attachGameServerInfo(registeredInfo, port, hosts, maxPlayers);
                return true;
            }
        }

        if (!LoginServer.getInstance().getProperties().gameServer().acceptNew() || !acceptAlternativeId) {
            log.info(
                    "Rejected GS auth for desiredServerId={} due to wrong hexId and no alternative id allowed.",
                    desiredId);
            server.forceClose(LoginServerFail.REASON_WRONG_HEXID);
            return false;
        }

        final GameServerInfo alternativeInfo = new GameServerInfo(desiredId, hexId, server);
        if (!gameServerTable.registerWithFirstAvailableId(alternativeInfo)) {
            log.info("Rejected GS auth for desiredServerId={} due to no free alternative id available.", desiredId);
            server.forceClose(LoginServerFail.REASON_NO_FREE_ID);
            return false;
        }

        server.attachGameServerInfo(alternativeInfo, port, hosts, maxPlayers);
        gameServerTable.registerServerOnDB(alternativeInfo);
        return true;
    }
}
