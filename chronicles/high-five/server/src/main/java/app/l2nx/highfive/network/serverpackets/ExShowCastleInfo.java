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

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.PacketLogger;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author KenM
 */
public class ExShowCastleInfo extends ServerPacket
{
	private final Collection<Castle> _castles;
	
	public ExShowCastleInfo()
	{
		_castles = CastleManager.getInstance().getCastles();
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.EX_SHOW_CASTLE_INFO.writeId(this, buffer);
		buffer.writeInt(_castles.size());
		for (Castle castle : _castles)
		{
			buffer.writeInt(castle.getResidenceId());
			if (castle.getOwnerId() > 0)
			{
				if (ClanTable.getInstance().getClan(castle.getOwnerId()) != null)
				{
					buffer.writeString(ClanTable.getInstance().getClan(castle.getOwnerId()).getName());
				}
				else
				{
					PacketLogger.warning("Castle owner with no name! Castle: " + castle.getName() + " has an OwnerId = " + castle.getOwnerId() + " who does not have a  name!");
					buffer.writeString("");
				}
			}
			else
			{
				buffer.writeString("");
			}
			
			buffer.writeInt(castle.getTaxPercent());
			buffer.writeInt((int) (castle.getSiege().getSiegeDate().getTimeInMillis() / 1000));
		}
	}
}
