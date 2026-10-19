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
package app.l2nx.highfive.scripts.ai.others;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.serverpackets.NpcSay;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Sin Wardens disappearing and chat.
 * @author GKR
 */
public class SinWardens extends Script {
    private static final int[] SIN_WARDEN_MINIONS = {
        22424, 22425, 22426, 22427, 22428, 22429, 22430, 22432, 22433, 22434, 22435, 22436, 22437, 22438
    };

    private final Map<Integer, Integer> killedMinionsCount = new ConcurrentHashMap<>();

    private SinWardens() {
        addKillId(SIN_WARDEN_MINIONS);
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        if (npc.isMinion()) {
            final Monster master = npc.asMonster().getLeader();
            if ((master != null) && !master.isDead()) {
                int killedCount = killedMinionsCount.containsKey(master.getObjectId())
                        ? killedMinionsCount.get(master.getObjectId())
                        : 0;
                killedCount++;

                if (killedCount == 5) {
                    master.broadcastPacket(new NpcSay(
                            master.getObjectId(),
                            ChatType.NPC_GENERAL,
                            master.getId(),
                            NpcStringId.WE_MIGHT_NEED_NEW_SLAVES_I_LL_BE_BACK_SOON_SO_WAIT));
                    master.doDie(killer);
                    killedMinionsCount.remove(master.getObjectId());
                } else {
                    killedMinionsCount.put(master.getObjectId(), killedCount);
                }
            }
        }
    }

    public static void main(String[] args) {
        new SinWardens();
    }
}
