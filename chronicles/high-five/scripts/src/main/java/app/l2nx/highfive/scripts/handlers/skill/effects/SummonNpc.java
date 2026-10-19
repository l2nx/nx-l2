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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.commons.util.Rnd;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Decoy;
import app.l2nx.highfive.entity.actor.instance.EffectPoint;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.util.StatSet;

/**
 * Summon Npc effect implementation.
 * @author Zoey76
 */
public class SummonNpc extends AbstractEffect
{
	private int _despawnDelay;
	private final int _npcId;
	private final int _npcCount;
	private final boolean _randomOffset;
	private final boolean _isSummonSpawn;
	
	public SummonNpc(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_despawnDelay = params.getInt("despawnDelay", 20000);
		_npcId = params.getInt("npcId", 0);
		_npcCount = params.getInt("npcCount", 1);
		_randomOffset = params.getBoolean("randomOffset", false);
		_isSummonSpawn = params.getBoolean("isSummonSpawn", false);
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.SUMMON_NPC;
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if ((effected == null) || !effected.isPlayer() || effected.isAlikeDead())
		{
			return;
		}
		
		if ((_npcId <= 0) || (_npcCount <= 0))
		{
			LOGGER.warning(SummonNpc.class.getSimpleName() + ": Invalid NPC ID or count skill ID: " + skill.getId());
			return;
		}
		
		final Player player = effected.asPlayer();
		if (player.inObserverMode() || player.isMounted())
		{
			return;
		}
		
		final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(_npcId);
		if (npcTemplate == null)
		{
			LOGGER.warning(SummonNpc.class.getSimpleName() + ": Spawn of the nonexisting NPC ID: " + _npcId + ", skill ID:" + skill.getId());
			return;
		}
		
		switch (npcTemplate.getType())
		{
			case "Decoy":
			{
				final Decoy decoy = new Decoy(npcTemplate, player, _despawnDelay);
				decoy.fullRestore();
				decoy.setHeading(player.getHeading());
				decoy.setInstanceId(player.getInstanceId());
				decoy.setSummoner(player);
				decoy.spawnMe(player.getX(), player.getY(), player.getZ());
				player.setDecoy(decoy);
				break;
			}
			case "EffectPoint": // TODO: Implement proper signet skills.
			{
				final EffectPoint effectPoint = new EffectPoint(npcTemplate, player);
				effectPoint.fullRestore();
				int x = player.getX();
				int y = player.getY();
				int z = player.getZ();
				if (skill.getTargetType() == TargetType.GROUND)
				{
					final Location wordPosition = player.getCurrentSkillWorldPosition();
					if (wordPosition != null)
					{
						x = wordPosition.getX();
						y = wordPosition.getY();
						z = wordPosition.getZ();
					}
				}
				
				effectPoint.setInvul(true);
				effectPoint.setSummoner(player);
				effectPoint.spawnMe(x, y, z);
				_despawnDelay = NpcData.getInstance().getTemplate(_npcId).getParameters().getInt("despawn_time", 20) * 1000;
				if (_despawnDelay > 0)
				{
					effectPoint.scheduleDespawn(_despawnDelay);
				}
				break;
			}
			default:
			{
				Spawn spawn;
				try
				{
					spawn = new Spawn(_npcId);
				}
				catch (Exception e)
				{
					LOGGER.warning(SummonNpc.class.getSimpleName() + ": " + e.getMessage());
					return;
				}
				
				int x = player.getX();
				int y = player.getY();
				if (_randomOffset)
				{
					x += (Rnd.nextBoolean() ? Rnd.get(20, 50) : Rnd.get(-50, -20));
					y += (Rnd.nextBoolean() ? Rnd.get(20, 50) : Rnd.get(-50, -20));
				}
				
				spawn.setXYZ(x, y, player.getZ());
				spawn.setHeading(player.getHeading());
				spawn.stopRespawn();
				
				final Npc npc = spawn.doSpawn(_isSummonSpawn);
				npc.setSummoner(player);
				npc.setName(npcTemplate.getName());
				npc.setTitle(npcTemplate.getName());
				if (_despawnDelay > 0)
				{
					npc.scheduleDespawn(_despawnDelay);
				}
				
				npc.broadcastInfo();
			}
		}
	}
}
