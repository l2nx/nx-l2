/*
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
package app.l2nx.highfive.scripts.ai.others.Dorian;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;

import app.l2nx.highfive.scripts.quests.Q00024_InhabitantsOfTheForestOfTheDead.Q00024_InhabitantsOfTheForestOfTheDead;

/**
 * Dorian (Raid Fighter) - Quest AI
 * @author malyelfik
 */
public class Dorian extends Script
{
	// NPC
	private static final int DORIAN = 25332;
	
	// Items
	private static final int SILVER_CROSS = 7153;
	private static final int BROKEN_SILVER_CROSS = 7154;
	
	private Dorian()
	{
		addCreatureSeeId(DORIAN);
	}
	
	@Override
	public void onCreatureSee(Npc npc, Creature creature)
	{
		if (creature.isPlayer())
		{
			final Player pl = creature.asPlayer();
			final QuestState qs = pl.getQuestState(Q00024_InhabitantsOfTheForestOfTheDead.class.getSimpleName());
			if ((qs != null) && qs.isCond(3))
			{
				takeItems(pl, SILVER_CROSS, -1);
				giveItems(pl, BROKEN_SILVER_CROSS, 1);
				qs.setCond(4, true);
				npc.broadcastSay(ChatType.NPC_GENERAL, NpcStringId.THAT_SIGN);
			}
		}
	}
	
	public static void main(String[] args)
	{
		new Dorian();
	}
}
