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
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.util.StatSet;

/**
 * Open Door effect implementation.
 * @author Adry_85
 */
public class OpenDoor extends AbstractEffect
{
	private final int _chance;
	private final boolean _isItem;
	
	public OpenDoor(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_chance = params.getInt("chance", 0);
		_isItem = params.getBoolean("isItem", false);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (!effected.isDoor())
		{
			return;
		}
		
		Door door = effected.asDoor();
		
		// Check if door in the different instance
		if (effector.getInstanceId() != door.getInstanceId())
		{
			// Search for the instance
			final Instance inst = InstanceManager.getInstance().getInstance(effector.getInstanceId());
			if (inst == null)
			{
				// Instance not found
				return;
			}
			
			final Door instanceDoor = inst.getDoor(door.getId());
			if (instanceDoor != null)
			{
				// Door found
				door = instanceDoor;
			}
			
			// Checking instance again
			if (effector.getInstanceId() != door.getInstanceId())
			{
				return;
			}
		}
		
		if ((!door.isOpenableBySkill() && !_isItem) || (door.getFort() != null))
		{
			effector.sendPacket(SystemMessageId.THIS_DOOR_CANNOT_BE_UNLOCKED);
			return;
		}
		
		if ((Rnd.get(100) < _chance) && !door.isOpen())
		{
			door.openMe();
		}
		else
		{
			effector.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_UNLOCK_THE_DOOR);
		}
	}
}
