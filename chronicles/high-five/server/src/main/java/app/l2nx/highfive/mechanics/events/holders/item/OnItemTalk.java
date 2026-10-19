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
package app.l2nx.highfive.mechanics.events.holders.item;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;

/**
 * @author UnAfraid
 */
public class OnItemTalk implements IBaseEvent
{
	private final Item _item;
	private final Player _player;
	
	public OnItemTalk(Item item, Player player)
	{
		_item = item;
		_player = player;
	}
	
	public Item getItem()
	{
		return _item;
	}
	
	public Player getPlayer()
	{
		return _player;
	}
	
	@Override
	public EventType getType()
	{
		return EventType.ON_ITEM_TALK;
	}
}
