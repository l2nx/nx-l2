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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.network.serverpackets.ExChangePostState;
import app.l2nx.highfive.network.serverpackets.ExReplyReceivedPost;

/**
 * @author Migi, DS
 */
public class RequestReceivedPost extends ClientPacket
{
	private int _msgId;
	
	@Override
	protected void readImpl()
	{
		_msgId = readInt();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if ((player == null) || !GeneralConfig.ALLOW_MAIL)
		{
			return;
		}
		
		final MailMessage msg = MailManager.getInstance().getMessage(_msgId);
		if (msg == null)
		{
			return;
		}
		
		if (!player.isInsideZone(ZoneId.PEACE) && msg.hasAttachments())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_RECEIVE_OR_SEND_MAIL_WITH_ATTACHED_ITEMS_IN_NON_PEACE_ZONE_REGIONS);
			return;
		}
		
		if (msg.getReceiverId() != player.getObjectId())
		{
			PunishmentManager.handleIllegalPlayerAction(player, player + " tried to receive not own post!", GeneralConfig.DEFAULT_PUNISH);
			return;
		}
		
		if (msg.isDeletedByReceiver())
		{
			return;
		}
		
		player.sendPacket(new ExReplyReceivedPost(msg));
		player.sendPacket(new ExChangePostState(true, _msgId, MailMessage.READED));
		msg.markAsRead();
	}
}
