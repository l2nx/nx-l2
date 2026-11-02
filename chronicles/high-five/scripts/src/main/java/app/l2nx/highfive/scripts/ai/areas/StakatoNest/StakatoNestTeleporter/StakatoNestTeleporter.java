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
package app.l2nx.highfive.scripts.ai.areas.StakatoNest.StakatoNestTeleporter;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.scripts.quests.Q00240_ImTheOnlyOneYouCanTrust.Q00240_ImTheOnlyOneYouCanTrust;

/**
 * Stakato Nest Teleport AI.
 * @author Charus
 */
public class StakatoNestTeleporter extends Script {
    // Locations
    private static final Location[] LOCS = {
        new Location(80456, -52322, -5640),
        new Location(88718, -46214, -4640),
        new Location(87464, -54221, -5120),
        new Location(80848, -49426, -5128),
        new Location(87682, -43291, -4128)
    };

    // NPC
    private static final int KINTAIJIN = 32640;

    private StakatoNestTeleporter() {
        addStartNpc(KINTAIJIN);
        addTalkId(KINTAIJIN);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final int index = Integer.parseInt(event) - 1;
        if (LOCS.length > index) {
            final Location loc = LOCS[index];
            final Party party = player.getParty();
            if (party != null) {
                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    party.collectMembers(members);
                    for (Player partyMember : members) {
                        if (partyMember.isInsideRadius3D(player, 1000)) {
                            partyMember.teleToLocation(loc, true);
                        }
                    }
                }
            }

            player.teleToLocation(loc, false);
        }

        return super.onEvent(event, npc, player);
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        final QuestState accessQuest = player.getQuestState(Q00240_ImTheOnlyOneYouCanTrust.class.getSimpleName());
        return (((accessQuest != null) && accessQuest.isCompleted()) ? "32640.htm" : "32640-no.htm");
    }

    public static void main(String[] args) {
        new StakatoNestTeleporter();
    }
}
