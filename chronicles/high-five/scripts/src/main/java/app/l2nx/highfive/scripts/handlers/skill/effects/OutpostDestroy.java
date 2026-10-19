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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.SiegeFlag;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Outpost Destroy effect implementation.
 * @author UnAfraid
 */
public class OutpostDestroy extends AbstractEffect
{
	public OutpostDestroy(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
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
		final Player player = effector.asPlayer();
		if (!player.isClanLeader())
		{
			return;
		}
		
		if (TerritoryWarManager.getInstance().isTWInProgress())
		{
			final Clan clan = player.getClan();
			final SiegeFlag flag = TerritoryWarManager.getInstance().getHQForClan(clan);
			if (flag != null)
			{
				flag.deleteMe();
			}
			
			TerritoryWarManager.getInstance().setHQForClan(clan, null);
		}
	}
}
