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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
import app.l2nx.highfive.managers.games.BlockCheckerManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;

/**
 * @author xban1x
 */
@SingleWriter
public class ArenaParticipantsHolder {
    private final int arena;
    private final ObjectArrayList<Player> redPlayers = new ObjectArrayList<>(6);
    private final ObjectArrayList<Player> bluePlayers = new ObjectArrayList<>(6);
    private final BlockCheckerManager event;

    public ArenaParticipantsHolder(int arena) {
        this.arena = arena;
        event = new BlockCheckerManager(this, this.arena);
    }

    /** @return a read-only snapshot of the red team in join order */
    public List<Player> getRedPlayers() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(redPlayers));
    }

    /** @return a read-only snapshot of the blue team in join order */
    public List<Player> getBluePlayers() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(bluePlayers));
    }

    public BlockCheckerManager getEvent() {
        WorldPartition.checkOwner();
        return event;
    }

    /** @return a read-only snapshot of both teams, red first */
    public List<Player> getAllPlayers() {
        WorldPartition.checkOwner();
        final ObjectArrayList<Player> all = new ObjectArrayList<>(redPlayers.size() + bluePlayers.size());
        all.addAll(redPlayers);
        all.addAll(bluePlayers);
        return ObjectLists.unmodifiable(all);
    }

    public void addPlayer(Player player, int team) {
        WorldPartition.checkOwner();
        if (team == 0) {
            redPlayers.add(player);
        } else {
            bluePlayers.add(player);
        }
    }

    public void removePlayer(Player player, int team) {
        WorldPartition.checkOwner();
        if (team == 0) {
            redPlayers.remove(player);
        } else {
            bluePlayers.remove(player);
        }
    }

    public int getPlayerTeam(Player player) {
        WorldPartition.checkOwner();
        if (redPlayers.contains(player)) {
            return 0;
        } else if (bluePlayers.contains(player)) {
            return 1;
        } else {
            return -1;
        }
    }

    public int getRedTeamSize() {
        WorldPartition.checkOwner();
        return redPlayers.size();
    }

    public int getBlueTeamSize() {
        WorldPartition.checkOwner();
        return bluePlayers.size();
    }

    public void broadCastPacketToTeam(ServerPacket packet) {
        WorldPartition.checkOwner();
        broadcast(packet);
    }

    public void clearPlayers() {
        WorldPartition.checkOwner();
        redPlayers.clear();
        bluePlayers.clear();
    }

    public void updateEvent() {
        WorldPartition.checkOwner();
        event.updatePlayersOnStart(this);
    }

    /** Moves players from the bigger team until the sizes differ by one at most. */
    public void checkAndShuffle() {
        WorldPartition.checkOwner();
        final int difference = redPlayers.size() - bluePlayers.size();
        if (Math.abs(difference) < 2) {
            return;
        }

        broadcast(new SystemMessage(SystemMessageId.TEAM_MEMBERS_WERE_MODIFIED_BECAUSE_THE_TEAMS_WERE_UNBALANCED));
        // Each move changes both sizes, so half the difference balances the teams; the move edits the team lists.
        try (ScratchBuffers.Snapshot<Player> bigger =
                ScratchBuffers.snapshot(difference > 0 ? redPlayers : bluePlayers)) {
            for (int i = 0; i < (Math.abs(difference) / 2); i++) {
                HandysBlockCheckerManager.getInstance().changePlayerToTeam(bigger.get(i), arena);
            }
        }
    }

    private void broadcast(ServerPacket packet) {
        try (ScratchBuffers.Snapshot<Player> players = ScratchBuffers.empty()) {
            players.addAll(redPlayers);
            players.addAll(bluePlayers);
            for (Player p : players) {
                p.sendPacket(packet);
            }
        }
    }
}
