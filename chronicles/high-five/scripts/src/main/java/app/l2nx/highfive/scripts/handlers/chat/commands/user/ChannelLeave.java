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
package app.l2nx.highfive.scripts.handlers.chat.commands.user;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.CommandChannel;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.handler.IUserCommandHandler;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;

/**
 * Channel Leave user command.
 * @author Chris, Zoey76
 */
public class ChannelLeave implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		96
	};
	
	@Override
	public boolean onCommand(int id, Player player)
	{
		if (id != COMMAND_IDS[0])
		{
			return false;
		}
		
		if (!player.isInParty() || !player.getParty().isLeader(player))
		{
			player.sendPacket(SystemMessageId.ONLY_A_PARTY_LEADER_CAN_LEAVE_A_COMMAND_CHANNEL);
			return false;
		}
		
		if (player.getParty().isInCommandChannel())
		{
			final CommandChannel channel = player.getParty().getCommandChannel();
			final Party party = player.getParty();
			channel.removeParty(party);
			party.getLeader().sendPacket(SystemMessageId.YOU_HAVE_QUIT_THE_COMMAND_CHANNEL);
			
			final SystemMessage sm = new SystemMessage(SystemMessageId.C1_S_PARTY_HAS_LEFT_THE_COMMAND_CHANNEL);
			sm.addPcName(party.getLeader());
			channel.broadcastPacket(sm);
			return true;
		}
		
		return false;
	}
	
	@Override
	public int[] getCommandList()
	{
		return COMMAND_IDS;
	}
}
