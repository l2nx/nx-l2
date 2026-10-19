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

import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.mechanics.script.Script;

import app.l2nx.highfive.scripts.ai.areas.Hellbound.HellboundEngine;

/**
 * Outpost Captain's AI.
 * @author DS
 */
public class OutpostCaptain extends Script
{
	// NPCs
	private static final int CAPTAIN = 18466;
	private static final int[] DEFENDERS =
	{
		22357, // Enceinte Defender
		22358, // Enceinte Defender
	};
	
	public OutpostCaptain()
	{
		addKillId(CAPTAIN);
		addSpawnId(CAPTAIN);
		addSpawnId(DEFENDERS);
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		final Door door = DoorData.getInstance().getDoor(20250001);
		if (door != null)
		{
			door.openMe();
		}
		
		if (HellboundEngine.getInstance().getLevel() == 8)
		{
			HellboundEngine.getInstance().setLevel(9);
		}
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		npc.setRandomWalking(false);
		
		if (npc.getId() == CAPTAIN)
		{
			final int hellboundLevel = HellboundEngine.getInstance().getLevel();
			if ((hellboundLevel < 7) || (hellboundLevel > 8))
			{
				npc.deleteMe();
				npc.getSpawn().stopRespawn();
			}
			else
			{
				final Door door = DoorData.getInstance().getDoor(20250001);
				if (door != null)
				{
					door.closeMe();
				}
			}
		}
	}
}
