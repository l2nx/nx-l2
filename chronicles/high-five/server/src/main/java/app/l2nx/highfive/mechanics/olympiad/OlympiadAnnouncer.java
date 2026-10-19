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
package app.l2nx.highfive.mechanics.olympiad;

import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;

/**
 * @author DS
 */
public class OlympiadAnnouncer implements Runnable {
    private static final int OLY_MANAGER = 31688;

    private int currentStadium = 0;

    @Override
    public void run() {
        OlympiadGameTask task;
        for (int i = OlympiadGameManager.getInstance().getNumberOfStadiums(); --i >= 0; currentStadium++) {
            if (currentStadium >= OlympiadGameManager.getInstance().getNumberOfStadiums()) {
                currentStadium = 0;
            }

            task = OlympiadGameManager.getInstance().getOlympiadTask(currentStadium);
            if ((task != null) && (task.getGame() != null) && task.needAnnounce()) {
                NpcStringId npcString;
                final String arenaId = String.valueOf(task.getGame().getStadiumId() + 1);
                switch (task.getGame().getType()) {
                    case NON_CLASSED: {
                        npcString =
                                NpcStringId
                                        .OLYMPIAD_CLASS_FREE_INDIVIDUAL_MATCH_IS_GOING_TO_BEGIN_IN_ARENA_S1_IN_A_MOMENT;
                        break;
                    }
                    case CLASSED: {
                        npcString =
                                NpcStringId.OLYMPIAD_CLASS_INDIVIDUAL_MATCH_IS_GOING_TO_BEGIN_IN_ARENA_S1_IN_A_MOMENT;
                        break;
                    }
                    case TEAMS: {
                        npcString =
                                NpcStringId.OLYMPIAD_CLASS_FREE_TEAM_MATCH_IS_GOING_TO_BEGIN_IN_ARENA_S1_IN_A_MOMENT;
                        break;
                    }
                    default: {
                        continue;
                    }
                }

                for (Spawn spawn : SpawnTable.getInstance().getSpawns(OLY_MANAGER)) {
                    final Npc manager = spawn.getLastSpawn();
                    if (manager != null) {
                        manager.broadcastSay(ChatType.NPC_SHOUT, npcString, arenaId);
                    }
                }
                break;
            }
        }
    }
}
