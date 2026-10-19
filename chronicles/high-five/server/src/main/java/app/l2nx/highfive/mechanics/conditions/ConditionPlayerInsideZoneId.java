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
package app.l2nx.highfive.mechanics.conditions;

import java.util.Set;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.skill.Skill;

/**
 * @author UnAfraid
 */
public class ConditionPlayerInsideZoneId extends Condition
{
	private final Set<Integer> _zones;
	
	public ConditionPlayerInsideZoneId(Set<Integer> zones)
	{
		_zones = zones;
	}
	
	@Override
	public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item)
	{
		if (effector.asPlayer() == null)
		{
			return false;
		}
		
		for (ZoneType zone : ZoneManager.getInstance().getZones(effector))
		{
			if (_zones.contains(zone.getId()))
			{
				return true;
			}
		}
		
		return false;
	}
}
