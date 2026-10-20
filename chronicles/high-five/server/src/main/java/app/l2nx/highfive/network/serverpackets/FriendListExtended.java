/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
import app.l2nx.highfive.managers.CharacterRelationsManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Support for "Chat with Friends" dialog. <br />
 * This packet is sent only at login.
 * @author mrTJO, UnAfraid
 */
public class FriendListExtended extends ServerPacket {
    private final List<FriendInfo> info;

    private static class FriendInfo {
        int objId;
        String name;
        boolean online;
        int classid;
        int level;

        public FriendInfo(int objId, String name, boolean online, int classid, int level) {
            this.objId = objId;
            this.name = name;
            this.online = online;
            this.classid = classid;
            this.level = level;
        }
    }

    public FriendListExtended(Player player) {
        info = new ArrayList<>(player.getFriendList().size());
        for (int objId : player.getFriendList()) {
            final String name = CharInfoTable.getInstance().getNameById(objId);
            final Player player1 = World.getPlayer(objId);
            boolean online = false;
            int classid = 0;
            int level = 0;
            if (player1 == null) {
                CharacterRelationsManager.getInstance()
                        .findFriendSummary(objId)
                        .ifPresent(friend -> info.add(new FriendInfo(
                                objId,
                                friend.name(),
                                friend.onlineStatus() == OnlineStatus.ONLINE,
                                friend.classId(),
                                friend.level())));
                continue;
            }

            if (player1.isOnline()) {
                online = true;
            }

            classid = player1.getPlayerClass().getId();
            level = player1.getLevel();
            info.add(new FriendInfo(objId, name, online, classid, level));
        }
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.FRIEND_LIST.writeId(this, buffer);
        buffer.writeInt(this.info.size());
        for (FriendInfo info : this.info) {
            buffer.writeInt(info.objId); // character id
            buffer.writeString(info.name);
            buffer.writeInt(info.online); // online
            buffer.writeInt(info.online ? info.objId : 0); // object id if online
            buffer.writeInt(info.classid);
            buffer.writeInt(info.level);
        }
    }
}
