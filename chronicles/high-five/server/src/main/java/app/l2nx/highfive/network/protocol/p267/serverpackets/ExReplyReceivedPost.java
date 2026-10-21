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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Migi, DS
 */
@Slf4j
public class ExReplyReceivedPost extends AbstractItemPacket {
    private final MailMessage msg;
    private Collection<Item> items = null;

    public ExReplyReceivedPost(MailMessage msg) {
        this.msg = msg;
        if (msg.hasAttachments()) {
            final ItemContainer attachments = msg.getAttachments();
            if ((attachments != null) && (attachments.getSize() > 0)) {
                items = attachments.getItems();
            } else {
                log.warn("Message {} has attachments but itemcontainer is empty.", msg.getId());
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_REPLY_RECEIVED_POST.writeId(this, buffer);
        buffer.writeInt(msg.getId());
        buffer.writeInt(msg.isLocked());
        buffer.writeInt(0); // Unknown
        buffer.writeString(msg.getSenderName());
        buffer.writeString(msg.getSubject());
        buffer.writeString(msg.getContent());
        if ((items != null) && !items.isEmpty()) {
            buffer.writeInt(items.size());
            for (Item item : items) {
                writeItem(item, buffer);
                buffer.writeInt(item.getObjectId());
            }
        } else {
            buffer.writeInt(0);
        }

        buffer.writeLong(msg.getReqAdena());
        buffer.writeInt(msg.hasAttachments());
        buffer.writeInt(msg.getSendBySystem());
    }
}
