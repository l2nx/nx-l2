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
package app.l2nx.highfive.scripts.ai.areas.IsleOfPrayer;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.mechanics.script.Script;

/**
 * Eva's Gift Box AI.
 * @author St3eT
 */
public class EvasGiftBox extends Script
{
	// NPC
	private static final int BOX = 32342; // Eva's Gift Box
	
	// Skill
	private static final int BUFF = 1073; // Kiss of Eva
	
	// Items
	private static final ItemHolder CORAL = new ItemHolder(9692, 1); // Red Coral
	private static final ItemHolder CRYSTAL = new ItemHolder(9693, 1); // Crystal Fragment
	
	private EvasGiftBox()
	{
		addKillId(BOX);
		addSpawnId(BOX);
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if (killer.isAffectedBySkill(BUFF))
		{
			if (getRandomBoolean())
			{
				npc.dropItem(killer, CRYSTAL);
			}
			
			if (getRandom(100) < 33)
			{
				npc.dropItem(killer, CORAL);
			}
		}
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		npc.setRandomWalking(false);
		npc.asAttackable().setOnKillDelay(0);
	}
	
	public static void main(String[] args)
	{
		new EvasGiftBox();
	}
}
