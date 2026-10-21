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

import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * Support for "Chat with Friends" dialog. <br />
 * Add new friend or delete.
 * @author JIV
 */
public class FriendPacket extends ServerPacket {
    private final boolean action;
    private final boolean online;
    private final int objid;
    private final String name;

    /**
     * @param action - true for adding, false for remove
     * @param objId
     */
    public FriendPacket(boolean action, int objId) {
        this.action = action;
        objid = objId;
        name = CharInfoTable.getInstance().getNameById(objId);
        online = World.getPlayer(objId) != null;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.L2_FRIEND.writeId(this, buffer);
        buffer.writeInt(action ? 1 : 3); // 1-add 3-remove
        buffer.writeInt(objid);
        buffer.writeString(name);
        buffer.writeInt(online);
        buffer.writeInt(online ? objid : 0);
    }
}
