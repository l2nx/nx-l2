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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.network.codec.PacketWriter;
import java.util.List;

/**
 * @author Migi, DS
 */
public class ExShowReceivedPostList extends ServerPacket {
    private final List<MailMessage> inbox;

    public ExShowReceivedPostList(int objectId) {
        inbox = MailManager.getInstance().getInbox(objectId);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_RECEIVED_POST_LIST.writeId(this, buffer);
        buffer.writeInt((int) (ClockBridge.wallMillis() / 1000));
        if ((inbox != null) && !inbox.isEmpty()) {
            buffer.writeInt(inbox.size());
            for (MailMessage msg : inbox) {
                buffer.writeInt(msg.getId());
                buffer.writeString(msg.getSubject());
                buffer.writeString(msg.getSenderName());
                buffer.writeInt(msg.isLocked());
                buffer.writeInt(msg.getExpirationSeconds());
                buffer.writeInt(msg.isUnread());
                buffer.writeInt(1);
                buffer.writeInt(msg.hasAttachments());
                buffer.writeInt(msg.isReturned());
                buffer.writeInt(msg.getSendBySystem());
                buffer.writeInt(0);
            }
        } else {
            buffer.writeInt(0);
        }
    }
}
