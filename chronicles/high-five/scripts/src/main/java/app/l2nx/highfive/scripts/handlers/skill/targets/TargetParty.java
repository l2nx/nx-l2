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
package app.l2nx.highfive.scripts.handlers.skill.targets;

import java.util.ArrayList;
import java.util.List;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;

/**
 * @author St3eT
 */
public class TargetParty implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		final List<WorldObject> targetList = new ArrayList<>();
		
		// Check for null target or any other invalid target
		if ((target == null) || target.isDead() || (target == creature))
		{
			creature.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
			return targetList;
		}
		
		final int radius = skill.getAffectRange();
		final Player player = creature.getTarget().asPlayer();
		if (player.isInParty())
		{
			for (Player partyMember : player.getParty().getMembers())
			{
				if (partyMember == null)
				{
					continue;
				}
				
				if (Skill.addCharacter(player, partyMember, radius, false))
				{
					targetList.add(partyMember);
				}
				
				if (Skill.addSummon(player, partyMember, radius, false))
				{
					targetList.add(partyMember.getSummon());
				}
			}
		}
		else
		{
			targetList.add(target);
		}
		
		return targetList;
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.TARGET_PARTY;
	}
}
