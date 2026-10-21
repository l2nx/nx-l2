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

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
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
import app.l2nx.highfive.network.protocol.highfive.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.security.SecondaryPasswordAuth;
import app.l2nx.highfive.util.FloodProtectors;
import app.l2nx.network.channel.OutboundQueue;
import java.net.InetSocketAddress;
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
 * Legacy facade of one game connection. The session drain, the login link and every sending thread reach it, so the
 * fields they share are volatile.
 *
 * @author KenM
 */
@Slf4j
public class GameClient {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    private final GameConnection connection;

    @Getter
    private final FloodProtectors floodProtectors = new FloodProtectors(this);

    @Getter
    private final ReentrantLock playerLock = new ReentrantLock();

    @Getter
    private final String ip;

    @Getter
    private volatile String accountName;

    // Null until the login server accepts the session.
    @Getter
    private volatile UUID accountId;

    @Getter
    @Setter
    private volatile SessionKey sessionId;

    @Getter
    @Setter
    private volatile Player player;

    @Getter
    private volatile SecondaryPasswordAuth secondaryAuth;

    @Getter
    @Setter
    private volatile ClientHardwareInfoHolder hardwareInfo;

    private volatile List<CharacterInfoHolder> charSlotMapping;

    @Getter
    @Setter
    private volatile boolean detached = false;

    @Getter
    private volatile boolean authedGG;

    @Getter
    private volatile int[][] trace;

    public GameClient(GameConnection connection) {
        this.connection = connection;
        ip = connection.channel().remoteAddress() instanceof InetSocketAddress inet
                ? inet.getAddress().getHostAddress()
                : String.valueOf(connection.channel().remoteAddress());
        ACCOUNTING_LOG.trace("Client connected: {}", ip);
    }

    public static GameClient of(Session session) {
        return session.attachment(GameClient.class);
    }

    public GameConnection connection() {
        return connection;
    }

    public Session session() {
        return connection.session();
    }

    public ClientPort port() {
        return connection.session().port();
    }

    /**
     * Runs once, from the session drain, after the connection closed. A detached client frees its account here too; its
     * character stays in the world until the owner's next selection ({@link #load}) saves and removes it.
     */
    public void onDisconnection() {
        ACCOUNTING_LOG.trace("Client disconnected: {}", this);
        LoginService.getInstance().cancelRequests(this);
        LoginService.getInstance().logout(this);
        if ((player == null) || !player.isInOfflineMode()) {
            Disconnection.of(this).onDisconnection();
        }
    }

    public void closeNow() {
        final OutboundQueue outbound = connection.outbound();
        // A pending close-on-write ends the connection once its last frame is out: a kick or a refused login sends
        // its message that way and then frees the login, whose disconnect would otherwise drop the message.
        if ((outbound != null) && outbound.isClosing()) {
            return;
        }
        connection.channel().close();
    }

    public void disconnect() {
        closeNow();
    }

    public void close(ServerPacket packet) {
        if (packet == null) {
            closeNow();
            return;
        }
        port().legacyAndClose(packet);
    }

    public SessionState getConnectionState() {
        return connection.session().state();
    }

    public void authenticated() {
        connection.session().setState(SessionState.AUTHED);
    }

    public int getProtocolVersion() {
        return connection.protocolVersion();
    }

    public boolean isProtocolOk() {
        return connection.adapter() != null;
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
        if (packet == null) {
            log.warn("Null packet sent to {}", this, new IllegalArgumentException("packet"));
            return;
        }
        port().legacy(packet);
        // TODO (#73): move the send-time side effects of a legacy packet into the slice's handlers when it is ported.
        packet.runImpl(player);
    }

    public void sendPacket(SystemMessageId systemMessageId) {
        sendPacket(new SystemMessage(systemMessageId));
    }

    /**
     * @return the deletion answer:
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
                    answer = 0;
                } else if (clan.getLeaderId() == objectId) {
                    answer = 2;
                } else {
                    answer = 1;
                }
            }

            if (answer == 0) {
                if (PlayerConfig.DELETE_DAYS == 0) {
                    deleteCharByObjId(objectId);
                } else {
                    PlayerPersistenceManager.getInstance()
                            .updateDeleteTime(
                                    objectId, ClockBridge.wallMillis() + (PlayerConfig.DELETE_DAYS * 86400000L));
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
            player.setRunning();
            player.standUp();
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

    public CharacterInfoHolder getCharSelection(int slot) {
        final List<CharacterInfoHolder> slots = charSlotMapping;
        return ((slots == null) || (slot < 0) || (slot >= slots.size())) ? null : slots.get(slot);
    }

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

    @Override
    public String toString() {
        try {
            final String ip = getIp();
            final SessionState state = getConnectionState();
            switch (state) {
                case CLOSING:
                case DISCONNECTED: {
                    if (accountName != null) {
                        return "[Account: " + accountName + " - IP: " + (ip == null ? "disconnected" : ip) + "]";
                    }

                    return "[IP: " + (ip == null ? "disconnected" : ip) + "]";
                }
                case CONNECTED: {
                    return "[IP: " + (ip == null ? "disconnected" : ip) + "]";
                }
                case AUTHED: {
                    if (player != null) {
                        return "[Character: " + player.getName() + "[" + player.getObjectId() + "]" + " - Account: "
                                + accountName + " - IP: " + (ip == null ? "disconnected" : ip) + "]";
                    }
                    return "[Account: " + accountName + " - IP: " + (ip == null ? "disconnected" : ip) + "]";
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
