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
package app.l2nx.highfive.scripts.quests.Q00272_WrathOfAncestors;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.script.QuestSound;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.State;

/**
 * Wrath of Ancestors (272)
 * @author xban1x
 */
public class Q00272_WrathOfAncestors extends Quest {
    // NPC
    private static final int LIVINA = 30572;

    // Items
    private static final int GRAVE_ROBBERS_HEAD = 1474;

    // Monsters
    private static final int[] MONSTERS = {
        20319, // Goblin Grave Robber
        20320, // Goblin Tomb Raider Leader
    };

    // Misc
    private static final int MIN_LEVEL = 5;

    public Q00272_WrathOfAncestors() {
        super(272);
        addStartNpc(LIVINA);
        addTalkId(LIVINA);
        addKillId(MONSTERS);
        registerQuestItems(GRAVE_ROBBERS_HEAD);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final QuestState qs = getQuestState(player, false);
        if ((qs != null) && event.equalsIgnoreCase("30572-04.htm")) {
            qs.startQuest();
            return event;
        }

        return null;
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        final QuestState qs = getQuestState(killer, false);
        if ((qs != null) && qs.isCond(1)) {
            giveItems(killer, GRAVE_ROBBERS_HEAD, 1);
            if (getQuestItemsCount(killer, GRAVE_ROBBERS_HEAD) >= 50) {
                qs.setCond(2, true);
            } else {
                playSound(killer, QuestSound.ITEMSOUND_QUEST_ITEMGET);
            }
        }
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        final QuestState qs = getQuestState(player, true);
        String htmltext = getNoQuestMsg(player);
        switch (qs.getState()) {
            case State.CREATED: {
                htmltext = (player.getRace() == Race.ORC)
                        ? (player.getLevel() >= MIN_LEVEL) ? "30572-03.htm" : "30572-02.htm"
                        : "30572-01.htm";
                break;
            }
            case State.STARTED: {
                switch (qs.getCond()) {
                    case 1: {
                        htmltext = "30572-05.html";
                        break;
                    }
                    case 2: {
                        giveAdena(player, 1500, true);
                        qs.exitQuest(true, true);
                        htmltext = "30572-06.html";
                        break;
                    }
                }
                break;
            }
        }

        return htmltext;
    }
}
