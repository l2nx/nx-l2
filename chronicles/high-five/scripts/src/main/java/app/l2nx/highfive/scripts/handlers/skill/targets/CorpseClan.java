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

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;

/**
 * @author UnAfraid
 */
public class CorpseClan implements ITargetTypeHandler
{
	@Override
	public List<WorldObject> getTargetList(Skill skill, Creature creature, boolean onlyFirst, Creature target)
	{
		final List<WorldObject> targetList = new ArrayList<>();
		if (creature.isPlayable())
		{
			final Player player = creature.asPlayer();
			if (player == null)
			{
				return targetList;
			}
			
			if (player.isInOlympiadMode())
			{
				targetList.add(player);
				return targetList;
			}
			
			final Clan clan = player.getClan();
			if (clan != null)
			{
				final int radius = skill.getAffectRange();
				final int maxTargets = skill.getAffectLimit();
				for (ClanMember member : clan.getMembers())
				{
					final Player obj = member.getPlayer();
					if ((obj == null) || (obj == player))
					{
						continue;
					}
					
					if (player.isInDuel())
					{
						if (player.getDuelId() != obj.getDuelId())
						{
							continue;
						}
						
						if (player.isInParty() && obj.isInParty() && (player.getParty().getLeaderObjectId() != obj.getParty().getLeaderObjectId()))
						{
							continue;
						}
					}
					
					// Don't add this target if this is a Pc->Pc pvp casting and pvp condition not met
					if (!player.checkPvpSkill(obj, skill))
					{
						continue;
					}
					
					if (player.isOnEvent() && !player.isOnSoloEvent() && obj.isOnEvent() && (player.getTeam() != obj.getTeam()))
					{
						continue;
					}
					
					if (!Skill.addCharacter(creature, obj, radius, true))
					{
						continue;
					}
					
					// check target is not in a active siege zone
					if (obj.isInsideZone(ZoneId.SIEGE) && !obj.isInSiege())
					{
						continue;
					}
					
					targetList.add(obj);
					
					if (onlyFirst)
					{
						return targetList;
					}
					
					if ((maxTargets > 0) && (targetList.size() >= maxTargets))
					{
						break;
					}
				}
			}
		}
		else if (creature.isNpc())
		{
			// for buff purposes, returns friendly mobs nearby and mob itself
			targetList.add(creature);
			final Npc npc = creature.asNpc();
			if ((npc.getTemplate().getClans() == null) || npc.getTemplate().getClans().isEmpty())
			{
				return targetList;
			}
			
			for (Npc newTarget : World.getVisibleObjectsInRange(creature, Npc.class, skill.getCastRange()))
			{
				if (npc.isInMyClan(newTarget))
				{
					if (targetList.size() >= skill.getAffectLimit())
					{
						break;
					}
					
					targetList.add(newTarget);
				}
			}
		}
		
		return targetList;
	}
	
	@Override
	public Enum<TargetType> getTargetType()
	{
		return TargetType.CORPSE_CLAN;
	}
}
