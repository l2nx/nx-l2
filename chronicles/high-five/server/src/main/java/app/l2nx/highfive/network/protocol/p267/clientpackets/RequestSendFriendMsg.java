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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.L2FriendSay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestSendFriendMsg extends ClientPacket {

    private static final Logger CHAT_LOG = LoggerFactory.getLogger("chat");

    private String message;
    private String receiver;

    @Override
    protected void readImpl() {
        message = readString();
        receiver = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if ((message == null) || message.isEmpty() || (message.length() > 300)) {
            return;
        }

        final Player targetPlayer = World.getPlayer(receiver);
        if ((targetPlayer == null) || !targetPlayer.isFriend(player.getObjectId())) {
            player.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
            return;
        }

        if (GeneralConfig.LOG_CHAT) {
            final StringBuilder sb = new StringBuilder();
            sb.append("PRIV_MSG [");
            sb.append(player);
            sb.append(" to ");
            sb.append(targetPlayer);
            sb.append("] ");
            sb.append(message);
            CHAT_LOG.info(sb.toString());
        }

        targetPlayer.sendPacket(new L2FriendSay(player.getName(), receiver, message));
    }
}
