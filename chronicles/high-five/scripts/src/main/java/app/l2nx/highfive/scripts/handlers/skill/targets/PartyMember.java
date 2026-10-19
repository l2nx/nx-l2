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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;

/**
 * @author UnAfraid
 */
public class PartyMember implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		if (target == null)
		{
			creature.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
			return Collections.emptyList();
		}
		
		// Check if target is a valid party member.
		if (!target.isDead())
		{
			if (target == creature)
			{
				return Collections.singletonList(target);
			}
			
			if (creature.isInParty() && target.isInParty() && (creature.getParty().getLeaderObjectId() == target.getParty().getLeaderObjectId()))
			{
				return Collections.singletonList(target);
			}
			
			if (creature.isPlayer() && target.isSummon() && (creature.asPlayer().getSummon() == target))
			{
				return Collections.singletonList(target);
			}
			
			if (creature.isSummon() && target.isPlayer() && (creature == target.asPlayer().getSummon()))
			{
				return Collections.singletonList(target);
			}
		}
		
		final Player player = creature.asPlayer();
		if ((player != null) && target.isPlayer())
		{
			final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
			sm.addSkillName(skill);
			player.sendPacket(sm);
			return Collections.emptyList();
		}
		
		return Collections.emptyList();
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.PARTY_MEMBER;
	}
}
