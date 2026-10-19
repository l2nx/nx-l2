/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.scripts.handlers.skill.targets;

import java.util.Collections;
import java.util.List;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;

/**
 * @author UnAfraid, Mobius
 */
public class EnemySummon implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		if ((target != null) && target.isSummon())
		{
			final Summon targetSummon = target.asSummon();
			if (creature.isPlayer())
			{
				final Player player = creature.asPlayer();
				if ((player.getSummon() != targetSummon) && !targetSummon.isDead())
				{
					final Player targetOwner = targetSummon.getOwner();
					if ((targetOwner.getPvpFlag() != 0) || (targetOwner.getKarma() > 0))
					{
						return Collections.singletonList(targetSummon);
					}
					
					if (targetOwner.isInsideZone(ZoneId.PVP) && player.isInsideZone(ZoneId.PVP))
					{
						return Collections.singletonList(targetSummon);
					}
					
					if (targetOwner.isInDuel() && player.isInDuel() && (targetOwner.getDuelId() == player.getDuelId()))
					{
						return Collections.singletonList(targetSummon);
					}
				}
			}
		}
		
		return Collections.emptyList();
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.ENEMY_SUMMON;
	}
}
