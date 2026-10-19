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
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;

/**
 * @author UnAfraid
 */
public class PcBody implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		final List<WorldObject> targetList = new ArrayList<>();
		if ((target != null) && target.isDead())
		{
			final Player player;
			if (creature.isPlayer())
			{
				player = creature.asPlayer();
			}
			else
			{
				player = null;
			}
			
			final Player targetPlayer;
			if (target.isPlayer())
			{
				targetPlayer = target.asPlayer();
			}
			else
			{
				targetPlayer = null;
			}
			
			final Pet targetPet;
			if (target.isPet())
			{
				targetPet = target.asPet();
			}
			else
			{
				targetPet = null;
			}
			
			if ((player != null) && ((targetPlayer != null) || (targetPet != null)))
			{
				boolean condGood = true;
				if (skill.hasEffectType(EffectType.RESURRECTION) && (targetPlayer != null))
				{
					// check target is not in a active siege zone
					if (targetPlayer.isInsideZone(ZoneId.SIEGE) && !targetPlayer.isInSiege())
					{
						condGood = false;
						creature.sendPacket(SystemMessageId.IT_IS_NOT_POSSIBLE_TO_RESURRECT_IN_BATTLEFIELDS_WHERE_A_SIEGE_WAR_IS_TAKING_PLACE);
					}
					
					if (targetPlayer.isFestivalParticipant()) // Check to see if the current player target is in a festival.
					{
						condGood = false;
						creature.sendMessage("You may not resurrect participants in a festival.");
					}
				}
				
				if (condGood)
				{
					targetList.add(target);
					return targetList;
				}
			}
		}
		
		creature.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
		return targetList;
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.PC_BODY;
	}
}
