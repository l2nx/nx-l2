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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.List;

/**
 * @author Migi, DS
 */
public class ExShowSentPostList extends ServerPacket {
    private final List<MailMessage> outbox;

    public ExShowSentPostList(int objectId) {
        outbox = MailManager.getInstance().getOutbox(objectId);
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_SHOW_SENT_POST_LIST.writeId(this, buffer);
        buffer.writeInt((int) (ClockBridge.wallMillis() / 1000));
        if ((outbox != null) && !outbox.isEmpty()) {
            buffer.writeInt(outbox.size());
            for (MailMessage msg : outbox) {
                buffer.writeInt(msg.getId());
                buffer.writeString(msg.getSubject());
                buffer.writeString(msg.getReceiverName());
                buffer.writeInt(msg.isLocked());
                buffer.writeInt(msg.getExpirationSeconds());
                buffer.writeInt(msg.isUnread());
                buffer.writeInt(1);
                buffer.writeInt(msg.hasAttachments());
            }
        } else {
            buffer.writeInt(0);
        }
    }
}
