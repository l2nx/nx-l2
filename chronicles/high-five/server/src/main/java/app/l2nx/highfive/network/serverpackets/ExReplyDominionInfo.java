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

import java.util.List;

import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.TerritoryWarManager.Territory;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author JIV
 */
public class ExReplyDominionInfo extends ServerPacket
{
	public static final ExReplyDominionInfo STATIC_PACKET = new ExReplyDominionInfo();
	
	private ExReplyDominionInfo()
	{
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.EX_REPLY_DOMINION_INFO.writeId(this, buffer);
		final List<Territory> territoryList = TerritoryWarManager.getInstance().getAllTerritories();
		buffer.writeInt(territoryList.size()); // Territory Count
		for (Territory t : territoryList)
		{
			buffer.writeInt(t.getTerritoryId()); // Territory Id
			buffer.writeString(CastleManager.getInstance().getCastleById(t.getCastleId()).getName().toLowerCase() + "_dominion"); // territory name
			buffer.writeString(t.getOwnerClan().getName());
			buffer.writeInt(t.getOwnedWardIds().size()); // Emblem Count
			for (int i : t.getOwnedWardIds())
			{
				buffer.writeInt(i); // Emblem ID - should be in for loop for emblem count
			}
			
			buffer.writeInt((int) (TerritoryWarManager.getInstance().getTWStartTimeInMillis() / 1000));
		}
	}
}
