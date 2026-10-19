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

import java.util.Collections;
import java.util.List;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;

/**
 * @author UnAfraid
 */
public class PartyOther implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		if ((target != null) && (target != creature) && creature.isInParty() && target.isInParty() && (creature.getParty().getLeaderObjectId() == target.getParty().getLeaderObjectId()))
		{
			if (!target.isDead())
			{
				if (target.isPlayer())
				{
					switch (skill.getId())
					{
						// FORCE BUFFS may cancel here but there should be a proper condition.
						case 426:
						{
							if (!target.asPlayer().isMageClass())
							{
								return Collections.singletonList(target);
							}
							
							return Collections.emptyList();
						}
						case 427:
						{
							if (target.asPlayer().isMageClass())
							{
								return Collections.singletonList(target);
							}
							
							return Collections.emptyList();
						}
					}
				}
				
				return Collections.singletonList(target);
			}
			
			return Collections.emptyList();
		}
		
		creature.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
		return Collections.emptyList();
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.PARTY_OTHER;
	}
}
