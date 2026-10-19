/*
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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;

/**
 * @author chris_00
 */
public class ExMPCCShowPartyMemberInfo extends ServerPacket
{
	private final Party _party;
	
	public ExMPCCShowPartyMemberInfo(Party party)
	{
		_party = party;
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.EX_MPCC_SHOW_PARTY_MEMBER_INFO.writeId(this, buffer);
		buffer.writeInt(_party.getMemberCount());
		for (Player pc : _party.getMembers())
		{
			buffer.writeString(pc.getName());
			buffer.writeInt(pc.getObjectId());
			buffer.writeInt(pc.getPlayerClass().getId());
		}
	}
}
