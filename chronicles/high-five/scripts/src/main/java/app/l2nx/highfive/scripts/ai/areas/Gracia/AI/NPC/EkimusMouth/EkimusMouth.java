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
package app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.EkimusMouth;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.SeedOfInfinityManager;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;

public class EkimusMouth extends Script {
    // NPC
    private static final int EKIMUS_MOUTH = 32537;

    public EkimusMouth() {
        addStartNpc(EKIMUS_MOUTH);
        addFirstTalkId(EKIMUS_MOUTH);
        addTalkId(EKIMUS_MOUTH);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        String htmltext = event;
        QuestState qs = player.getQuestState(getName());
        if (qs == null) {
            qs = newQuestState(player);
        }

        if (event.equalsIgnoreCase("hos_enter")) {
            if (SeedOfInfinityManager.getCurrentStage() == 1) {
                htmltext = "32537-1.htm";
            } else if (SeedOfInfinityManager.getCurrentStage() == 4) {
                htmltext = "32537-2.htm";
            }
        } else if (event.equalsIgnoreCase("hoe_enter")) {
            if (SeedOfInfinityManager.getCurrentStage() == 1) {
                htmltext = "32537-3.htm";
            } else if (SeedOfInfinityManager.getCurrentStage() == 4) {
                htmltext = "32537-4.htm";
            }
        }

        return htmltext;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        QuestState qs = player.getQuestState(getName());
        if (qs == null) {
            qs = newQuestState(player);
        }

        return "32537.htm";
    }
}
