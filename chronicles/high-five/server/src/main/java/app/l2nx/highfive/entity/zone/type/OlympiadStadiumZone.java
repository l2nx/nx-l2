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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.OlympiadManager;
import app.l2nx.highfive.entity.zone.AbstractZoneSettings;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneRespawn;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameTask;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOlympiadMatchEnd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOlympiadUserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

/**
 * An olympiad stadium
 * @author durgus, DS
 */
@SingleWriter
public class OlympiadStadiumZone extends ZoneRespawn {
    private ObjectArrayList<Location> spectatorSpawns;

    public OlympiadStadiumZone(int id) {
        super(id, new Settings());
        checkAffected = true;
    }

    @SingleWriter
    public static final class Settings extends AbstractZoneSettings {
        private OlympiadGameTask olympiadTask = null;

        Settings() {}

        @Override
        public void clear() {
            WorldPartition.checkOwner();
            olympiadTask = null;
        }
    }

    /** Read-only snapshot of copies; {@code null} without any. */
    public List<Location> getSpectatorSpawns() {
        WorldPartition.checkOwner();
        return Location.copiesOf(spectatorSpawns);
    }

    @Override
    public Settings getSettings() {
        WorldPartition.checkOwner();
        return settings();
    }

    private Settings settings() {
        return (Settings) currentSettings();
    }

    public void registerTask(OlympiadGameTask task) {
        WorldPartition.checkOwner();
        settings().olympiadTask = task;
    }

    public void openDoors() {
        WorldPartition.checkOwner();
        for (Door door :
                InstanceManager.getInstance().getInstance(getInstanceId()).getDoors()) {
            if ((door != null) && !door.isOpen()) {
                door.openMe();
            }
        }
    }

    public void closeDoors() {
        WorldPartition.checkOwner();
        for (Door door :
                InstanceManager.getInstance().getInstance(getInstanceId()).getDoors()) {
            if ((door != null) && door.isOpen()) {
                door.closeMe();
            }
        }
    }

    public void spawnBuffers() {
        WorldPartition.checkOwner();
        for (Npc buffer :
                InstanceManager.getInstance().getInstance(getInstanceId()).getNpcs()) {
            if ((buffer instanceof OlympiadManager) && !buffer.isSpawned()) {
                buffer.spawnMe();
            }
        }
    }

    public void deleteBuffers() {
        WorldPartition.checkOwner();
        for (Npc buffer :
                InstanceManager.getInstance().getInstance(getInstanceId()).getNpcs()) {
            if ((buffer instanceof OlympiadManager) && buffer.isSpawned()) {
                buffer.decayMe();
            }
        }
    }

    public void broadcastStatusUpdate(Player player) {
        WorldPartition.checkOwner();
        final ExOlympiadUserInfo packet = new ExOlympiadUserInfo(player);
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player target : players) {
                if ((target.inObserverMode() || (target.getOlympiadSide() != player.getOlympiadSide()))
                        && (target.getInstanceId() == player.getInstanceId())) {
                    target.sendPacket(packet);
                }
            }
        }
    }

    public void broadcastPacketToObservers(ServerPacket packet) {
        WorldPartition.checkOwner();
        final int instanceId = getInstanceId();
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player creature : players) {
                if (creature.inObserverMode() && (creature.getInstanceId() == instanceId)) {
                    creature.sendPacket(packet);
                }
            }
        }
    }

    @Override
    public void broadcastPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        final int instanceId = getInstanceId();
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player creature : players) {
                if (creature.getInstanceId() == instanceId) {
                    creature.sendPacket(packet);
                }
            }
        }
    }

    @Override
    protected boolean isAffected(Creature creature) {
        if (super.isAffected(creature)) {
            if (creature.getInstanceId() != getInstanceId()) {
                return false;
            }

            return true;
        }

        return false;
    }

    @Override
    protected void onEnter(Creature creature) {
        creature.setInsideZone(ZoneId.PVP, true);

        if (creature.isPlayer()
                && (settings().olympiadTask != null)
                && settings().olympiadTask.isBattleStarted()) {
            creature.sendPacket(SystemMessageId.YOU_HAVE_ENTERED_A_COMBAT_ZONE);
            settings().olympiadTask.getGame().sendOlympiadInfo(creature);
        }

        if (!creature.isPlayable()) {
            return;
        }

        final Player player = creature.asPlayer();
        if (player != null) {
            if (!player.isGM() && !player.isInOlympiadMode() && !player.inObserverMode()) {
                WorldPartition.loop().execute(new KickPlayer(player));
            } else {
                if (player.hasPet()) {
                    player.getSummon().unSummon(player);
                }
            }
        }
    }

    @Override
    protected void onExit(Creature creature) {
        creature.setInsideZone(ZoneId.PVP, false);

        if (creature.isPlayer()
                && (settings().olympiadTask != null)
                && settings().olympiadTask.isBattleStarted()) {
            creature.sendPacket(SystemMessageId.YOU_HAVE_LEFT_A_COMBAT_ZONE);
            creature.sendPacket(ExOlympiadMatchEnd.STATIC_PACKET);
        }
    }

    public void updateZoneStatusForCharactersInside() {
        WorldPartition.checkOwner();
        if (settings().olympiadTask == null) {
            return;
        }

        final boolean battleStarted = settings().olympiadTask.isBattleStarted();
        final SystemMessage sm = battleStarted
                ? new SystemMessage(SystemMessageId.YOU_HAVE_ENTERED_A_COMBAT_ZONE)
                : new SystemMessage(SystemMessageId.YOU_HAVE_LEFT_A_COMBAT_ZONE);
        final int instanceId = getInstanceId();
        try (Snapshot<Creature> inside = ScratchBuffers.empty()) {
            collectCharactersInside(inside);
            for (Creature creature : inside) {
                if (creature.getInstanceId() != instanceId) {
                    continue;
                }

                if (battleStarted) {
                    creature.setInsideZone(ZoneId.PVP, true);
                    if (creature.isPlayer()) {
                        creature.sendPacket(sm);
                    }
                } else {
                    creature.setInsideZone(ZoneId.PVP, false);
                    if (creature.isPlayer()) {
                        creature.sendPacket(sm);
                        creature.sendPacket(ExOlympiadMatchEnd.STATIC_PACKET);
                    }
                }

                creature.broadcastInfo();
            }
        }
    }

    private static class KickPlayer implements Runnable {
        private Player player;

        public KickPlayer(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            if (player == null) {
                return;
            }

            if (player.hasSummon()) {
                player.getSummon().unSummon(player);
            }

            player.teleToLocation(TeleportWhereType.TOWN);
            player.setInstanceId(0);
            player = null;
        }
    }

    @Override
    public void parseLoc(int x, int y, int z, String type) {
        WorldPartition.checkOwner();
        if ((type != null) && type.equals("spectatorSpawn")) {
            if (spectatorSpawns == null) {
                spectatorSpawns = new ObjectArrayList<>();
            }

            spectatorSpawns.add(new Location(x, y, z));
        } else {
            super.parseLoc(x, y, z, type);
        }
    }
}
