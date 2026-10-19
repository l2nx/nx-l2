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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import java.util.Collection;

import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Sweeper effect implementation.
 * @author Zoey76
 */
public class Sweeper extends AbstractEffect
{
	public Sweeper(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if ((effector == null) || (effected == null) || !effector.isPlayer() || !effected.isAttackable())
		{
			return;
		}
		
		final Player player = effector.asPlayer();
		final Attackable monster = effected.asAttackable();
		if (!monster.checkSpoilOwner(player, false))
		{
			return;
		}
		
		if (!player.getInventory().checkInventorySlotsAndWeight(monster.getSpoilLootItems(), false, false))
		{
			return;
		}
		
		final Collection<ItemHolder> items = monster.takeSweep();
		if (items != null)
		{
			for (ItemHolder item : items)
			{
				if (player.isInParty())
				{
					player.getParty().distributeItem(player, item, true, monster);
				}
				else
				{
					player.addItem(ItemProcessType.SWEEP, item, effected, true);
				}
			}
		}
	}
}
