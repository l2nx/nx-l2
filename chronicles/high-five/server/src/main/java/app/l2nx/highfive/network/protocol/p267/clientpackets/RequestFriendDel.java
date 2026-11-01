/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CharacterRelationsManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.FriendPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class RequestFriendDel extends ClientPacket {
    private String name;

    @Override
    protected void readImpl() {
        name = readString();
    }

    @Override
    protected void runImpl() {
        SystemMessage sm;

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final int id = CharInfoTable.getInstance().getIdByName(name);
        if (id == -1) {
            sm = new SystemMessage(SystemMessageId.C1_IS_NOT_ON_YOUR_FRIEND_LIST);
            sm.addString(name);
            player.sendPacket(sm);
            return;
        }

        if (!player.isFriend(id)) {
            sm = new SystemMessage(SystemMessageId.C1_IS_NOT_ON_YOUR_FRIEND_LIST);
            sm.addString(name);
            player.sendPacket(sm);
            return;
        }

        try {
            CharacterRelationsManager.getInstance().removeFriendship(player.getObjectId(), id);

            sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DELETED_FROM_YOUR_FRIENDS_LIST);
            sm.addString(name);
            player.sendPacket(sm);

            player.removeFriend(id);
            player.sendPacket(new FriendPacket(false, id));

            final Player target = World.getPlayer(name);
            if (target != null) {
                target.removeFriend(player.getObjectId());
                target.sendPacket(new FriendPacket(false, player.getObjectId()));
            }
        } catch (DataAccessException e) {
            log.warn("Could not delete friend objectId: {}", e.getMessage());
        }
    }
}
