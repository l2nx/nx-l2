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

import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.PacketLogger;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author Migi, DS
 */
public class ExReplyReceivedPost extends AbstractItemPacket
{
	private final MailMessage _msg;
	private Collection<Item> _items = null;
	
	public ExReplyReceivedPost(MailMessage msg)
	{
		_msg = msg;
		if (msg.hasAttachments())
		{
			final ItemContainer attachments = msg.getAttachments();
			if ((attachments != null) && (attachments.getSize() > 0))
			{
				_items = attachments.getItems();
			}
			else
			{
				PacketLogger.warning("Message " + msg.getId() + " has attachments but itemcontainer is empty.");
			}
		}
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.EX_REPLY_RECEIVED_POST.writeId(this, buffer);
		buffer.writeInt(_msg.getId());
		buffer.writeInt(_msg.isLocked());
		buffer.writeInt(0); // Unknown
		buffer.writeString(_msg.getSenderName());
		buffer.writeString(_msg.getSubject());
		buffer.writeString(_msg.getContent());
		if ((_items != null) && !_items.isEmpty())
		{
			buffer.writeInt(_items.size());
			for (Item item : _items)
			{
				writeItem(item, buffer);
				buffer.writeInt(item.getObjectId());
			}
		}
		else
		{
			buffer.writeInt(0);
		}
		
		buffer.writeLong(_msg.getReqAdena());
		buffer.writeInt(_msg.hasAttachments());
		buffer.writeInt(_msg.getSendBySystem());
	}
}
