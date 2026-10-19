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

import java.util.Collection;

import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.siege.TerritoryWard;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author Gigiikun
 */
public class ExShowOwnthingPos extends ServerPacket
{
	public static final ExShowOwnthingPos STATIC_PACKET = new ExShowOwnthingPos();
	
	private ExShowOwnthingPos()
	{
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.EX_SHOW_OWNTHING_POS.writeId(this, buffer);
		if (TerritoryWarManager.getInstance().isTWInProgress())
		{
			final Collection<TerritoryWard> territoryWardList = TerritoryWarManager.getInstance().getAllTerritoryWards();
			buffer.writeInt(territoryWardList.size());
			for (TerritoryWard ward : territoryWardList)
			{
				buffer.writeInt(ward.getTerritoryId());
				if (ward.getNpc() != null)
				{
					buffer.writeInt(ward.getNpc().getX());
					buffer.writeInt(ward.getNpc().getY());
					buffer.writeInt(ward.getNpc().getZ());
				}
				else if (ward.getPlayer() != null)
				{
					buffer.writeInt(ward.getPlayer().getX());
					buffer.writeInt(ward.getPlayer().getY());
					buffer.writeInt(ward.getPlayer().getZ());
				}
				else
				{
					buffer.writeInt(0);
					buffer.writeInt(0);
					buffer.writeInt(0);
				}
			}
		}
		else
		{
			buffer.writeInt(0);
		}
	}
}
