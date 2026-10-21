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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
import app.l2nx.highfive.managers.games.BlockCheckerManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * @author xban1x
 */
public class ArenaParticipantsHolder {
    private final int arena;

    @Getter
    private final List<Player> redPlayers;

    @Getter
    private final List<Player> bluePlayers;

    @Getter
    private final BlockCheckerManager event;

    public ArenaParticipantsHolder(int arena) {
        this.arena = arena;
        redPlayers = new ArrayList<>(6);
        bluePlayers = new ArrayList<>(6);
        event = new BlockCheckerManager(this, this.arena);
    }

    public List<Player> getAllPlayers() {
        final List<Player> all = new ArrayList<>(redPlayers);
        all.addAll(bluePlayers);
        return all;
    }

    public void addPlayer(Player player, int team) {
        if (team == 0) {
            redPlayers.add(player);
        } else {
            bluePlayers.add(player);
        }
    }

    public void removePlayer(Player player, int team) {
        if (team == 0) {
            redPlayers.remove(player);
        } else {
            bluePlayers.remove(player);
        }
    }

    public int getPlayerTeam(Player player) {
        if (redPlayers.contains(player)) {
            return 0;
        } else if (bluePlayers.contains(player)) {
            return 1;
        } else {
            return -1;
        }
    }

    public int getRedTeamSize() {
        return redPlayers.size();
    }

    public int getBlueTeamSize() {
        return bluePlayers.size();
    }

    public void broadCastPacketToTeam(ServerPacket packet) {
        for (Player p : redPlayers) {
            p.sendPacket(packet);
        }

        for (Player p : bluePlayers) {
            p.sendPacket(packet);
        }
    }

    public void clearPlayers() {
        redPlayers.clear();
        bluePlayers.clear();
    }

    public void updateEvent() {
        event.updatePlayersOnStart(this);
    }

    public void checkAndShuffle() {
        final int redSize = redPlayers.size();
        final int blueSize = bluePlayers.size();
        if (redSize > (blueSize + 1)) {
            broadCastPacketToTeam(
                    new SystemMessage(SystemMessageId.TEAM_MEMBERS_WERE_MODIFIED_BECAUSE_THE_TEAMS_WERE_UNBALANCED));
            for (int i = 0; i < ((redSize - (blueSize + 1)) + 1); i++) {
                final Player plr = redPlayers.get(i);
                if (plr == null) {
                    continue;
                }

                HandysBlockCheckerManager.getInstance().changePlayerToTeam(plr, arena);
            }
        } else if (blueSize > (redSize + 1)) {
            broadCastPacketToTeam(
                    new SystemMessage(SystemMessageId.TEAM_MEMBERS_WERE_MODIFIED_BECAUSE_THE_TEAMS_WERE_UNBALANCED));
            for (int i = 0; i < ((blueSize - (redSize + 1)) + 1); i++) {
                final Player plr = bluePlayers.get(i);
                if (plr == null) {
                    continue;
                }

                HandysBlockCheckerManager.getInstance().changePlayerToTeam(plr, arena);
            }
        }
    }
}
