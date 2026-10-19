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
package app.l2nx.highfive.mechanics.stats.functions.formulas;

import java.util.EnumMap;
import java.util.Map;

import app.l2nx.highfive.data.holders.ArmorSet;
import app.l2nx.highfive.data.xml.ArmorSetData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;

/**
 * @author UnAfraid
 */
public class FuncArmorSet extends AbstractFunction
{
	private static final Map<Stat, FuncArmorSet> _fh_instance = new EnumMap<>(Stat.class);
	
	public static AbstractFunction getInstance(Stat st)
	{
		_fh_instance.computeIfAbsent(st, k -> new FuncArmorSet(st));
		
		return _fh_instance.get(st);
	}
	
	private FuncArmorSet(Stat stat)
	{
		super(stat, 1, null, 0, null);
	}
	
	@Override
	public double calc(Creature effector, Creature effected, Skill skill, double initVal)
	{
		double value = initVal;
		
		// Should not apply armor set bonus to summons.
		if (effector.isPlayer())
		{
			final Player player = effector.asPlayer();
			final Item chest = player.getChestArmorInstance();
			if (chest != null)
			{
				final ArmorSet set = ArmorSetData.getInstance().getSet(chest.getId());
				if ((set != null) && set.containAll(player))
				{
					switch (getStat())
					{
						case STAT_STR:
						{
							value += set.getSTR();
							break;
						}
						case STAT_DEX:
						{
							value += set.getDEX();
							break;
						}
						case STAT_INT:
						{
							value += set.getINT();
							break;
						}
						case STAT_MEN:
						{
							value += set.getMEN();
							break;
						}
						case STAT_CON:
						{
							value += set.getCON();
							break;
						}
						case STAT_WIT:
						{
							value += set.getWIT();
							break;
						}
					}
				}
			}
		}
		
		return value;
	}
}
