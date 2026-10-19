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

import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Support for "Chat with Friends" dialog. <br />
 * This packet is sent only at login.
 * @author Tempy
 */
public class FriendList extends ServerPacket {
    private final List<FriendInfo> info;

    private static class FriendInfo {
        int objId;
        String name;
        boolean online;

        public FriendInfo(int objId, String name, boolean online) {
            this.objId = objId;
            this.name = name;
            this.online = online;
        }
    }

    public FriendList(Player player) {
        info = new ArrayList<>(player.getFriendList().size());
        for (int objId : player.getFriendList()) {
            final String name = CharInfoTable.getInstance().getNameById(objId);
            final Player player1 = World.getPlayer(objId);
            boolean online = false;
            if ((player1 != null) && player1.isOnline()) {
                online = true;
            }

            info.add(new FriendInfo(objId, name, online));
        }
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.L2_FRIEND_LIST.writeId(this, buffer);
        buffer.writeInt(this.info.size());
        for (FriendInfo info : this.info) {
            buffer.writeInt(info.objId); // character id
            buffer.writeString(info.name);
            buffer.writeInt(info.online); // online
            buffer.writeInt(info.online ? info.objId : 0); // object id if online
        }
    }
}
