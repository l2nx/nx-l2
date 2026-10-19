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

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.RaidBoss;
import app.l2nx.highfive.managers.RaidBossSpawnManager;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;

/**
 * Typhoon's AI.
 * @author GKR
 */
public class Typhoon extends Script
{
	// NPCs
	private static final int TYPHOON = 25539;
	
	// Skills
	private static final SkillHolder STORM = new SkillHolder(5434, 1); // Gust
	
	public Typhoon()
	{
		addAggroRangeEnterId(TYPHOON);
		addSpawnId(TYPHOON);
		
		final RaidBoss boss = RaidBossSpawnManager.getInstance().getBosses().get(TYPHOON);
		if (boss != null)
		{
			onSpawn(boss);
		}
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if (event.equalsIgnoreCase("CAST") && (npc != null) && !npc.isDead())
		{
			npc.doSimultaneousCast(STORM.getSkill());
			startQuestTimer("CAST", 5000, npc, null);
		}
		
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public void onAggroRangeEnter(Npc npc, Player player, boolean isSummon)
	{
		npc.doSimultaneousCast(STORM.getSkill());
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		startQuestTimer("CAST", 5000, npc, null);
	}
}
