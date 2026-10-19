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
package app.l2nx.highfive.scripts.ai.areas.Hellbound.AI;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.scripts.ai.areas.Hellbound.HellboundEngine;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import java.util.Collection;

/**
 * Hellbound Slaves AI.
 * @author DS
 */
public class Slaves extends Script {
    // NPCs
    private static final int[] MASTERS = {
        22320, // Junior Watchman
        22321, // Junior Summoner
    };

    // Locations
    private static final Location MOVE_TO = new Location(-25451, 252291, -3252, 3500);

    // Misc
    private static final int TRUST_REWARD = 10;

    public Slaves() {
        addSpawnId(MASTERS);
        addKillId(MASTERS);
    }

    @Override
    public void onSpawn(Npc npc) {
        npc.asMonster().enableMinions(HellboundEngine.getInstance().getLevel() < 5);
        npc.asMonster().setOnKillDelay(1000);
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        if (npc.asMonster().getMinionList() != null) {
            final Collection<Monster> slaves = npc.asMonster().getMinionList().getSpawnedMinions();
            if ((slaves != null) && !slaves.isEmpty()) {
                for (Monster slave : slaves) {
                    if ((slave == null) || slave.isDead()) {
                        continue;
                    }

                    slave.clearAggroList();
                    slave.abortAttack();
                    slave.abortCast();
                    slave.broadcastSay(
                            ChatType.NPC_GENERAL, NpcStringId.THANK_YOU_FOR_SAVING_ME_FROM_THE_CLUTCHES_OF_EVIL);
                    if ((HellboundEngine.getInstance().getLevel() >= 1)
                            && (HellboundEngine.getInstance().getLevel() <= 2)) {
                        HellboundEngine.getInstance().updateTrust(TRUST_REWARD, false);
                    }

                    slave.getAI().setIntentionMoveTo(MOVE_TO);
                    DecayTaskManager.getInstance().add(slave);
                }
            }
        }
    }
}
