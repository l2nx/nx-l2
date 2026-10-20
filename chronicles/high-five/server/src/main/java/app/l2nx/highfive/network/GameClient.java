/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.network;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.network.holders.CharacterInfoHolder;
import app.l2nx.highfive.network.holders.ClientHardwareInfoHolder;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.security.SecondaryPasswordAuth;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.FloodProtectors;
import app.l2nx.network.Client;
import app.l2nx.network.buffer.ReadBuffer;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Represents a client connected on GameServer.
 * @author KenM
 */
@Slf4j
public class GameClient extends Client<app.l2nx.network.Connection<GameClient>> {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    @Getter
    private final FloodProtectors floodProtectors = new FloodProtectors(this);

    @Getter
    private final ReentrantLock playerLock = new ReentrantLock();

    @Getter
    @Setter
    private ConnectionState connectionState = ConnectionState.CONNECTED;

    @Getter
    private Encryption encryption = null;

    @Getter
    private String ip = "N/A";

    @Getter
    private String accountName;

    // Null until the login server accepts the session; packets to the login server name the account by it.
    @Getter
    private UUID accountId;

    @Getter
    @Setter
    private SessionKey sessionId;

    @Getter
    @Setter
    private Player player;

    @Getter
    private SecondaryPasswordAuth secondaryAuth;

    @Getter
    @Setter
    private volatile ClientHardwareInfoHolder hardwareInfo;

    private List<CharacterInfoHolder> charSlotMapping = null;

    @Getter
    @Setter
    private volatile boolean detached = false;

    @Getter
    private boolean authedGG;

    @Getter
    @Setter
    private boolean protocolOk;

    @Getter
    @Setter
    private int protocolVersion;

    @Getter
    private int[][] trace;

    public GameClient(app.l2nx.network.Connection<GameClient> connection) {
        super(connection);
        ip = connection.getRemoteAddress();
    }

    @Override
    public void onConnected() {
        ACCOUNTING_LOG.trace("Client connected: {}", ip);
    }

    @Override
    public void onDisconnection() {
        ACCOUNTING_LOG.trace("Client disconnected: {}", this);
        LoginService.getInstance().cancelAuthentication(this);
        LoginService.getInstance().logout(accountName, accountId);
        if ((player == null) || !player.isInOfflineMode()) {
            Disconnection.of(this).onDisconnection();
        }

        connectionState = ConnectionState.DISCONNECTED;
    }

    @Override
    public boolean encrypt(WriteBuffer data, int offset, int size) {
        if (ServerConfig.PACKET_ENCRYPTION && (encryption != null)) {
            encryption.encrypt(data, offset, size);
        }

        return true;
    }

    @Override
    public boolean decrypt(ReadBuffer data, int offset, int size) {
        if (ServerConfig.PACKET_ENCRYPTION && (encryption != null)) {
            encryption.decrypt(data, offset, size);
        }

        return true;
    }

    public void closeNow() {
        disconnect();
    }

    public void close(ServerPacket packet) {
        if (packet == null) {
            closeNow();
        } else {
            // Send the close packet.
            sendPacket(packet);

            // Wait for packet to be sent.
            ThreadPool.schedule(this::closeNow, 1000);
        }
    }

    public byte[] enableCrypt() {
        final byte[] key = BlowFishKeygen.getRandomKey();
        if (ServerConfig.PACKET_ENCRYPTION) {
            encryption = new Encryption();
            encryption.setKey(key);
        }

        return key;
    }

    public void setGameGuardOk(boolean value) {
        authedGG = value;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
        if (SecondaryAuthData.getInstance().isEnabled()) {
            secondaryAuth = new SecondaryPasswordAuth(this);
        }
    }

    public void sendPacket(ServerPacket packet) {
        // Packet should never be null.
        if (packet == null) {
            log.warn("Null packet sent", new Exception());
            return;
        }

        // Send the packet data.
        writePacket(packet);

        // Run packet implementation.
        packet.runImpl(player);
    }

    public void sendPacket(SystemMessageId systemMessageId) {
        sendPacket(new SystemMessage(systemMessageId));
    }

    /**
     * Method to handle character deletion
     * @param characterSlot
     * @return a byte:
     *         <li>-1: Error: No char was found for such charslot, caught exception, etc...
     *         <li>0: character is not member of any clan, proceed with deletion
     *         <li>1: character is member of a clan, but not clan leader
     *         <li>2: character is clan leader
     */
    public byte markToDeleteChar(int characterSlot) {
        final int objectId = getObjectIdForSlot(characterSlot);
        if (objectId < 0) {
            return -1;
        }

        try {
            byte answer = 0;
            final int clanId = PlayerPersistenceManager.getInstance().findClanId(objectId);
            if (clanId != 0) {
                final Clan clan = ClanTable.getInstance().getClan(clanId);
                if (clan == null) {
                    answer = 0; // jeezes!
                } else if (clan.getLeaderId() == objectId) {
                    answer = 2;
                } else {
                    answer = 1;
                }
            }

            // Setting delete time
            if (answer == 0) {
                if (PlayerConfig.DELETE_DAYS == 0) {
                    deleteCharByObjId(objectId);
                } else {
                    PlayerPersistenceManager.getInstance()
                            .updateDeleteTime(
                                    objectId,
                                    ClockBridge.wallMillis()
                                            + (PlayerConfig.DELETE_DAYS * 86400000L)); // 24*60*60*1000 = 86400000
                }

                ACCOUNTING_LOG.info("Delete, {}, {}", objectId, this);
            }

            return answer;
        } catch (DataAccessException e) {
            log.error("Error updating delete time of character.", e);
            return -1;
        }
    }

    public void restore(int characterSlot) {
        final int objectId = getObjectIdForSlot(characterSlot);
        if (objectId < 0) {
            return;
        }

        try {
            PlayerPersistenceManager.getInstance().updateDeleteTime(objectId, 0);
        } catch (DataAccessException e) {
            log.error("Error restoring character.", e);
        }

        ACCOUNTING_LOG.info("Restore, {}, {}", objectId, this);
    }

    public static void deleteCharByObjId(int objectId) {
        if (objectId < 0) {
            return;
        }

        try {
            PlayerPersistenceManager.getInstance().deleteCharacter(objectId);
        } catch (DataAccessException e) {
            log.error("Error deleting character {}.", objectId, e);
            return;
        }

        CharInfoTable.getInstance().removeName(objectId);
    }

    public Player load(int characterSlot) {
        final int objectId = getObjectIdForSlot(characterSlot);
        if (objectId < 0) {
            return null;
        }

        Player player = World.getPlayer(objectId);
        if (player != null) {
            // exploit prevention, should not happens in normal way
            if (player.isOnlineInt() == 1) {
                log.error("Attempt of double login: {}({}) {}", player.getName(), objectId, accountName);
            }

            if (player.getClient() != null) {
                Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
            } else {
                player.storeMe();
                player.deleteMe();
            }

            return null;
        }

        player = Player.load(objectId);
        if (player != null) {
            // prevent some values for each login
            player.setRunning(); // running is default
            player.standUp(); // standing is default
            player.refreshOverloaded();
            player.refreshExpertisePenalty();
        } else {
            log.error("Could not restore in slot: {}", characterSlot);
        }

        return player;
    }

    public void setCharSelection(List<CharacterInfoHolder> characters) {
        charSlotMapping = characters;
    }

    public CharacterInfoHolder getCharSelection(int charslot) {
        if ((charSlotMapping == null) || (charslot < 0) || (charslot >= charSlotMapping.size())) {
            return null;
        }

        return charSlotMapping.get(charslot);
    }

    /**
     * @param characterSlot
     * @return
     */
    private int getObjectIdForSlot(int characterSlot) {
        final CharacterInfoHolder info = getCharSelection(characterSlot);
        if (info == null) {
            log.warn(
                    "{} tried to delete Character in slot {} but no characters exits at that slot.",
                    toString(),
                    characterSlot);
            return -1;
        }

        return info.getObjectId();
    }

    public void setClientTracert(int[][] tracert) {
        trace = tracert;
    }

    /**
     * Produces the best possible string representation of this client.
     */
    @Override
    public String toString() {
        try {
            final String ip = getIp();
            final ConnectionState state = getConnectionState();
            switch (state) {
                case DISCONNECTED: {
                    if (accountName != null) {
                        return "[Account: " + accountName + " - IP: " + (ip == null ? "disconnected" : ip) + "]";
                    }

                    return "[IP: " + (ip == null ? "disconnected" : ip) + "]";
                }
                case CONNECTED: {
                    return "[IP: " + (ip == null ? "disconnected" : ip) + "]";
                }
                case AUTHENTICATED: {
                    return "[Account: " + accountName + " - IP: " + (ip == null ? "disconnected" : ip) + "]";
                }
                case ENTERING:
                case IN_GAME: {
                    return "[Character: "
                            + (player == null ? "disconnected" : player.getName() + "[" + player.getObjectId() + "]")
                            + " - Account: " + accountName + " - IP: " + (ip == null ? "disconnected" : ip) + "]";
                }
                default: {
                    throw new IllegalStateException("Missing state on switch.");
                }
            }
        } catch (NullPointerException e) {
            return "[Character read failed due to disconnect]";
        }
    }
}
