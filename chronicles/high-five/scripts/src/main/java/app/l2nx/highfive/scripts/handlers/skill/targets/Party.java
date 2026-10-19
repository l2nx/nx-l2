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
package app.l2nx.highfive.scripts.handlers.skill.targets;

import java.util.ArrayList;
import java.util.List;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;

/**
 * @author UnAfraid
 */
public class Party implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		final List<WorldObject> targetList = new ArrayList<>();
		targetList.add(creature);
		if (onlyFirst)
		{
			return targetList;
		}
		
		final int radius = skill.getAffectRange();
		final Player player = creature.asPlayer();
		if (creature.isSummon())
		{
			if (Skill.addCharacter(creature, player, radius, false))
			{
				targetList.add(player);
			}
		}
		else if (creature.isPlayer())
		{
			if (Skill.addSummon(creature, player, radius, false))
			{
				targetList.add(player.getSummon());
			}
		}
		
		if (creature.isInParty())
		{
			// Get a list of Party Members
			for (Player partyMember : creature.getParty().getMembers())
			{
				if ((partyMember == null) || (partyMember == player))
				{
					continue;
				}
				
				if (Skill.addCharacter(creature, partyMember, radius, false))
				{
					targetList.add(partyMember);
				}
				
				if (Skill.addSummon(creature, partyMember, radius, false))
				{
					targetList.add(partyMember.getSummon());
				}
			}
		}
		
		return targetList;
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.PARTY;
	}
}
