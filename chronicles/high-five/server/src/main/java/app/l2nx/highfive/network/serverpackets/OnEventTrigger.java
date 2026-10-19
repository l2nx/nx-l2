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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author Gnacik
 */
public class OnEventTrigger extends ServerPacket
{
	private final int _emitterId;
	private final boolean _enabled;
	
	public OnEventTrigger(Door door, boolean enabled)
	{
		_emitterId = door.getEmitter();
		_enabled = enabled;
	}
	
	public OnEventTrigger(int id, boolean enabled)
	{
		_emitterId = id;
		_enabled = enabled;
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.EVENT_TRIGGER.writeId(this, buffer);
		buffer.writeInt(_emitterId);
		buffer.writeByte(_enabled);
	}
}
