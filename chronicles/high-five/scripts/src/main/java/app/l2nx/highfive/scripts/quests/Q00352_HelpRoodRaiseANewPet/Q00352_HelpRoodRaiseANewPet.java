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
package app.l2nx.highfive.scripts.quests.Q00352_HelpRoodRaiseANewPet;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.util.LocationUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Help Rood Raise A New Pet! (352)
 * @author Adry_85
 */
public class Q00352_HelpRoodRaiseANewPet extends Quest {
    private static class DropInfo {
        @Getter
        public int firstChance;

        @Getter
        public int secondChance;

        public DropInfo(int firstChance, int secondChance) {
            this.firstChance = firstChance;
            this.secondChance = secondChance;
        }
    }

    // NPC
    private static final int ROOD = 31067;

    // Items
    private static final int LIENRIK_EGG1 = 5860;
    private static final int LIENRIK_EGG2 = 5861;

    // Misc
    private static final int MIN_LEVEL = 39;

    private static final Map<Integer, DropInfo> MOBS = new HashMap<>();

    static {
        MOBS.put(20786, new DropInfo(46, 48)); // lienrik
        MOBS.put(21644, new DropInfo(46, 48)); // lienrik_a
        MOBS.put(21645, new DropInfo(69, 71)); // lienrik_lad_a
    }

    public Q00352_HelpRoodRaiseANewPet() {
        super(352);
        addStartNpc(ROOD);
        addTalkId(ROOD);
        addKillId(MOBS.keySet());
        registerQuestItems(LIENRIK_EGG1, LIENRIK_EGG2);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final QuestState qs = getQuestState(player, false);
        if (qs == null) {
            return null;
        }

        String htmltext = null;
        switch (event) {
            case "31067-02.htm":
            case "31067-03.htm":
            case "31067-07.html":
            case "31067-10.html": {
                htmltext = event;
                break;
            }
            case "31067-04.htm": {
                qs.setMemoState(1);
                qs.startQuest();
                htmltext = event;
                break;
            }
            case "31067-08.html": {
                qs.exitQuest(true, true);
                htmltext = event;
                break;
            }
        }

        return htmltext;
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        final QuestState qs = getQuestState(killer, false);
        if ((qs == null) || !LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, npc, killer, true)) {
            return;
        }

        final DropInfo info = MOBS.get(npc.getId());
        final int random = getRandom(100);
        if (random < info.getFirstChance()) {
            giveItemRandomly(killer, npc, LIENRIK_EGG1, 1, 0, 1, true);
        } else if (random < info.getSecondChance()) {
            giveItemRandomly(killer, npc, LIENRIK_EGG2, 1, 0, 1, true);
        }
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        final QuestState qs = getQuestState(player, true);
        String htmltext = getNoQuestMsg(player);
        if (qs.isCreated()) {
            htmltext = (player.getLevel() >= MIN_LEVEL) ? "31067-01.htm" : "31067-05.html";
        } else if (qs.isStarted()) {
            final long lienrikEgg1Count = getQuestItemsCount(player, LIENRIK_EGG1);
            final long lienrikEgg2Count = getQuestItemsCount(player, LIENRIK_EGG2);
            if ((lienrikEgg1Count == 0) && (lienrikEgg2Count == 0)) {
                htmltext = "31067-06.html";
            } else if ((lienrikEgg1Count >= 1) && (lienrikEgg2Count == 0)) {
                if (lienrikEgg1Count >= 10) {
                    giveAdena(player, (lienrikEgg1Count * 34) + 4000, true);
                } else {
                    giveAdena(player, (lienrikEgg1Count * 34) + 2000, true);
                }

                takeItems(player, LIENRIK_EGG1, -1);
                htmltext = "31067-10.html";
            } else if (lienrikEgg1Count >= 1) {
                giveAdena(player, 4000 + ((lienrikEgg1Count * 34) + (lienrikEgg2Count * 1025)), true);
                takeItems(player, LIENRIK_EGG1, -1);
                takeItems(player, LIENRIK_EGG2, -1);
                htmltext = "31067-11.html";
            }
        }

        return htmltext;
    }
}
