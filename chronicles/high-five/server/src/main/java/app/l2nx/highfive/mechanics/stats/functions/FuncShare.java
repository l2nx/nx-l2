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
package app.l2nx.highfive.mechanics.stats.functions;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;

/**
 * @author UnAfraid
 */
public class FuncShare extends AbstractFunction
{
	public FuncShare(Stat stat, int order, Object owner, double value, Condition applyCond)
	{
		super(stat, order, owner, value, applyCond);
	}
	
	@Override
	public double calc(Creature effector, Creature effected, Skill skill, double initVal)
	{
		if (((getApplyCond() == null) || getApplyCond().test(effector, effected, skill)) && (effector != null) && effector.isServitor())
		{
			final Summon summon = effector.asSummon();
			final Player player = summon.getOwner();
			if (player != null)
			{
				return initVal + (getBaseValue(getStat(), player) * getValue());
			}
		}
		
		return initVal;
	}
	
	public static double getBaseValue(Stat stat, Player player)
	{
		switch (stat)
		{
			case MAX_HP:
			{
				return player.getMaxHp();
			}
			case MAX_MP:
			{
				return player.getMaxMp();
			}
			case POWER_ATTACK:
			{
				return player.getPAtk(null);
			}
			case MAGIC_ATTACK:
			{
				return player.getMAtk(null, null);
			}
			case POWER_DEFENCE:
			{
				return player.getPDef(null);
			}
			case MAGIC_DEFENCE:
			{
				return player.getMDef(null, null);
			}
			case CRITICAL_RATE:
			{
				return player.getCriticalHit(null, null);
			}
			case POWER_ATTACK_SPEED:
			{
				return player.getPAtkSpd();
			}
			case MAGIC_ATTACK_SPEED:
			{
				return player.getMAtkSpd();
			}
			default:
			{
				return player.calcStat(stat, 0, null, null);
			}
		}
	}
}
