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
package app.l2nx.highfive.scripts.ai.areas.Hellbound.AI.NPC.Deltuva;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;

import app.l2nx.highfive.scripts.quests.Q00132_MatrasCuriosity.Q00132_MatrasCuriosity;

/**
 * Deltuva AI.
 * @author GKR
 */
public class Deltuva extends Script
{
	// NPCs
	private static final int DELTUVA = 32313;
	
	// Location
	private static final Location TELEPORT = new Location(17934, 283189, -9701);
	
	public Deltuva()
	{
		addStartNpc(DELTUVA);
		addTalkId(DELTUVA);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (event.equalsIgnoreCase("teleport"))
		{
			final QuestState hostQuest = player.getQuestState(Q00132_MatrasCuriosity.class.getSimpleName());
			if ((hostQuest == null) || !hostQuest.isCompleted())
			{
				return "32313-02.htm";
			}
			
			player.teleToLocation(TELEPORT);
		}
		
		return super.onEvent(event, npc, player);
	}
}
