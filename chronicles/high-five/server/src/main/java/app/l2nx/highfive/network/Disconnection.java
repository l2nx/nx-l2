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
package app.l2nx.highfive.network;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author NB4L1, Mobius
 */
@Slf4j
public class Disconnection {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    private final GameClient client;
    private final Player player;

    private Disconnection(GameClient client, Player player) {
        this.client = client != null ? client : player != null ? player.getClient() : null;
        this.player = player != null ? player : client != null ? client.getPlayer() : null;

        if (this.player != null) {
            this.player.stopAllTasks();
        }

        AntiFeedManager.getInstance().onDisconnect(this.client);

        if (this.client != null) {
            this.client.setPlayer(null);
        }

        if (this.player != null) {
            this.player.setClient(null);
        }
    }

    public static Disconnection of(GameClient client) {
        return new Disconnection(client, null);
    }

    public static Disconnection of(Player player) {
        return new Disconnection(null, player);
    }

    public static Disconnection of(GameClient client, Player player) {
        return new Disconnection(client, player);
    }

    /**
     * Stores the player's data and deletes it if the player is online, once no value transaction of the character is
     * in flight: a save of the memory before its outcome would undo the committed move.
     */
    public void storeAndDelete() {
        if (player == null) {
            store();
            return;
        }
        IdManager.getInstance()
                .findId(player.getObjectId())
                .ifPresentOrElse(
                        id -> WorldPartition.inFlightCharacters().whenIdle(id, () -> storeAndRelease(id)), this::store);
    }

    private void storeAndRelease(UUID characterId) {
        try {
            store();
        } finally {
            WorldPartition.loadedCharacters().release(characterId, player);
        }
    }

    private void store() {
        try {
            if (player != null) {
                player.storeMe();

                if (player.isOnline()) {
                    player.deleteMe();

                    ACCOUNTING_LOG.info("Logged out, {}", player);
                }
            } else if (client != null) {
                ACCOUNTING_LOG.info("Logged out, {}", client);
            }
        } catch (Exception e) {
            log.warn("Problem with storeAndDelete: {}", e.getMessage());
        } finally {
            if (player != null) {
                WorldPartition.loadedGameAccounts().detach(player, player.getAccountId());
            }
        }
    }

    public void storeAndDeleteWith(ServerPacket packet) {
        storeAndDelete();

        if (client != null) {
            client.close(packet);
        }
    }

    public void storeAndCloseWith(ViewEvent event) {
        storeAndDelete();

        if (client != null) {
            client.port().emitAndClose(event);
        }
    }

    public void onDisconnection() {
        if (player == null) {
            return;
        }

        if (!WorldPartition.loop().inLoop()) {
            log.warn("{} was not saved on disconnection: the world loop has stopped.", player);
            return;
        }

        if (player.canLogout()) {
            storeAndDelete();
        } else {
            try {
                WorldPartition.loop()
                        .schedule(
                                () -> {
                                    if (player.isOnline()) {
                                        storeAndDelete();
                                    }
                                },
                                AttackStanceTaskManager.COMBAT_TIME,
                                TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // The world loop has stopped, so no other writer is left and nobody waits for the combat delay.
                if (player.isOnline()) {
                    storeAndDelete();
                }
            }
        }
    }
}
