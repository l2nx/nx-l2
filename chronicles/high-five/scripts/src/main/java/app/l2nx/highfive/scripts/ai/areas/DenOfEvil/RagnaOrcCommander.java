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
package app.l2nx.highfive.scripts.ai.areas.DenOfEvil;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.holders.npc.MinionHolder;
import app.l2nx.highfive.mechanics.script.Script;

/**
 * Ragna Orc Commander AI.
 * @author Zealar, Mobius
 */
public class RagnaOrcCommander extends Script
{
	private static final int RAGNA_ORC_COMMANDER = 22694;
	
	private RagnaOrcCommander()
	{
		addSpawnId(RAGNA_ORC_COMMANDER);
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		for (MinionHolder minionHolder : npc.getTemplate().getParameters().getMinionList("Privates1"))
		{
			addMinion(npc.asMonster(), minionHolder.getId());
		}
		
		for (MinionHolder minionHolder : npc.getTemplate().getParameters().getMinionList(getRandomBoolean() ? "Privates2" : "Privates3"))
		{
			addMinion(npc.asMonster(), minionHolder.getId());
		}
	}
	
	public static void main(String[] args)
	{
		new RagnaOrcCommander();
	}
}
