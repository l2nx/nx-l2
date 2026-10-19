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

import app.l2nx.commons.network.buffer.WriteBuffer;
import app.l2nx.highfive.entity.actor.instance.Boat;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;

/**
 * @author Maktakien
 */
public class VehicleInfo extends ServerPacket
{
	private final int _objId;
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _heading;
	
	public VehicleInfo(Boat boat)
	{
		_objId = boat.getObjectId();
		_x = boat.getX();
		_y = boat.getY();
		_z = boat.getZ();
		_heading = boat.getHeading();
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.VEHICLE_INFO.writeId(this, buffer);
		buffer.writeInt(_objId);
		buffer.writeInt(_x);
		buffer.writeInt(_y);
		buffer.writeInt(_z);
		buffer.writeInt(_heading);
	}
}
