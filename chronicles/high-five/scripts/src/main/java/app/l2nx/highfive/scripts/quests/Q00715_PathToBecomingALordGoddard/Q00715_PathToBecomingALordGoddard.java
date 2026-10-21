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
package app.l2nx.highfive.scripts.quests.Q00715_PathToBecomingALordGoddard;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.NpcSay;

/**
 * Path to Becoming a Lord - Goddard (715)
 * @author Sacrifice
 */
public class Q00715_PathToBecomingALordGoddard extends Quest {
    private static final int ALFRED = 35363;

    private static final int WATER_SPIRIT_ASHUTAR = 25316;
    private static final int FIRE_SPIRIT_NASTRON = 25306;

    private static final int GODDARD_CASTLE = 7;

    public Q00715_PathToBecomingALordGoddard() {
        super(715);
        addStartNpc(ALFRED);
        addKillId(WATER_SPIRIT_ASHUTAR, FIRE_SPIRIT_NASTRON);
        addTalkId(ALFRED);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final Castle castle = CastleManager.getInstance().getCastleById(GODDARD_CASTLE);
        if (castle.getOwner() == null) {
            return "Castle has no lord.";
        }

        final QuestState qs = player.getQuestState(getName());
        switch (event) {
            case "35363-03.html": {
                qs.startQuest();
                break;
            }
            case "35363-04a.html": {
                qs.setCond(3);
                break;
            }
            case "35363-04b.html": {
                qs.setCond(2);
                break;
            }
            case "35363-08.html": {
                if (castle.getOwner().getLeader().getPlayer() != null) {
                    final NpcSay packet = new NpcSay(
                            npc.getObjectId(),
                            ChatType.NPC_SHOUT,
                            npc.getId(),
                            NpcStringId
                                    .S1_HAS_BECOME_THE_LORD_OF_THE_TOWN_OF_GODDARD_MAY_THERE_BE_GLORY_IN_THE_TERRITORY_OF_GODDARD);
                    packet.addStringParameter(player.getName());
                    npc.broadcastPacket(packet);
                    qs.exitQuest(true, true);
                }
                break;
            }
        }

        return event;
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        final QuestState qs = killer.getQuestState(getName());
        if (qs == null) {
            return;
        }

        if (qs.isCond(2) && (npc.getId() == FIRE_SPIRIT_NASTRON)) {
            qs.setCond(4);
        } else if (qs.isCond(3) && (npc.getId() == WATER_SPIRIT_ASHUTAR)) {
            qs.setCond(5);
        }

        if (qs.isCond(6) && (npc.getId() == WATER_SPIRIT_ASHUTAR)) {
            qs.setCond(9);
        } else if (qs.isCond(7) && (npc.getId() == FIRE_SPIRIT_NASTRON)) {
            qs.setCond(8);
        }
    }

    @Override
    public String onTalk(Npc npc, Player talker) {
        final Castle castle = CastleManager.getInstance().getCastleById(GODDARD_CASTLE);
        if (castle.getOwner() == null) {
            return "Castle has no lord.";
        }

        final QuestState qs = getQuestState(talker, true);
        String htmltext = getNoQuestMsg(talker);
        final Player castleOwner = castle.getOwner().getLeader().getPlayer();
        if (qs.isCond(0)) {
            if (castleOwner == qs.getPlayer()) {
                if (!hasFort()) {
                    htmltext = "35363-01.html";
                } else {
                    htmltext = "35363-00.html";
                    qs.exitQuest(true);
                }
            } else {
                htmltext = "35363-00a.html";
                qs.exitQuest(true);
            }
        } else if (qs.isCond(1)) {
            htmltext = "35363-03.html";
        } else if (qs.isCond(2)) {
            htmltext = "35363-05b.html";
        } else if (qs.isCond(3)) {
            htmltext = "35363-05a.html";
        } else if (qs.isCond(4)) {
            qs.setCond(6);
            htmltext = "35363-06b.html";
        } else if (qs.isCond(5)) {
            qs.setCond(7);
            htmltext = "35363-06a.html";
        } else if (qs.isCond(6)) {
            htmltext = "35363-06b.html";
        } else if (qs.isCond(7)) {
            htmltext = "35363-06a.html";
        } else if (qs.isCond(8) || qs.isCond(9)) {
            htmltext = "35363-07.html";
        }

        return htmltext;
    }

    private boolean hasFort() {
        for (Fort fortress : FortManager.getInstance().getForts()) {
            if (fortress.getContractedCastleId() == GODDARD_CASTLE) {
                return true;
            }
        }

        return false;
    }
}
