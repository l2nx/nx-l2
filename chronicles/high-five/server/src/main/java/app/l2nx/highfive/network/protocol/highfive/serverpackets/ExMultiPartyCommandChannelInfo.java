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

import app.l2nx.highfive.entity.groups.CommandChannel;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author chris_00
 */
public class ExMultiPartyCommandChannelInfo extends ServerPacket {
    private final CommandChannel channel;

    public ExMultiPartyCommandChannelInfo(CommandChannel channel) {
        this.channel = channel;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        if (channel == null) {
            return;
        }

        ServerPackets.EX_MULTI_PARTY_COMMAND_CHANNEL_INFO.writeId(this, buffer);
        buffer.writeString(channel.getLeader().getName());
        buffer.writeInt(0); // Channel loot 0 or 1
        buffer.writeInt(channel.getMemberCount());
        buffer.writeInt(channel.getParties().size());
        for (Party p : channel.getParties()) {
            buffer.writeString(p.getLeader().getName());
            buffer.writeInt(p.getLeaderObjectId());
            buffer.writeInt(p.getMemberCount());
        }
    }
}
