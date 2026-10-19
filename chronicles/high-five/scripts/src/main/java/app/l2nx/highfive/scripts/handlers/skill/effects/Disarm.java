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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.util.StatSet;

/**
 * Disarm effect implementation.
 * @author nBd
 */
public class Disarm extends AbstractEffect
{
	private static final Map<Integer, Integer> _disarmedPlayers = new ConcurrentHashMap<>();
	
	public Disarm(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public boolean canStart(Creature effector, Creature effected, Skill skill)
	{
		return effected.isPlayer();
	}
	
	@Override
	public int getEffectFlags()
	{
		return EffectFlag.DISARMED.getMask();
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		final Player player = effected.asPlayer();
		if (player == null)
		{
			return;
		}
		
		final Item itemToDisarm = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		if (itemToDisarm == null)
		{
			return;
		}
		
		final BodyPart bodyPart = BodyPart.fromItem(itemToDisarm);
		player.getInventory().unEquipItemInBodySlot(bodyPart);
		
		final InventoryUpdate iu = new InventoryUpdate();
		iu.addModifiedItem(itemToDisarm);
		player.sendInventoryUpdate(iu);
		player.broadcastUserInfo();
		
		_disarmedPlayers.put(player.getObjectId(), itemToDisarm.getObjectId());
	}
	
	@Override
	public void onExit(Creature effector, Creature effected, Skill skill)
	{
		final Player player = effected.asPlayer();
		if (player == null)
		{
			return;
		}
		
		final Integer itemObjectId = _disarmedPlayers.remove(player.getObjectId());
		if (itemObjectId == null)
		{
			return;
		}
		
		final Item item = player.getInventory().getItemByObjectId(itemObjectId);
		if (item == null)
		{
			return;
		}
		
		player.getInventory().equipItem(item);
		final InventoryUpdate iu = new InventoryUpdate();
		iu.addModifiedItem(item);
		player.sendInventoryUpdate(iu);
	}
}
