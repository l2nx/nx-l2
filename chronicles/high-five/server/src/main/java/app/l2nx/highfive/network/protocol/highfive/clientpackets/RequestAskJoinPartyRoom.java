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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExAskJoinPartyRoom;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;

/**
 * Format: (ch) S
 * @author -Wooden-, Tryskell
 */
public class RequestAskJoinPartyRoom extends ClientPacket {
    private String name;

    @Override
    protected void readImpl() {
        name = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Send PartyRoom invite request (with activeChar) name to the target.
        final Player target = World.getPlayer(name);
        if (target != null) {
            if (!target.isProcessingRequest()) {
                player.onTransactionRequest(target);
                target.sendPacket(new ExAskJoinPartyRoom(player.getName()));
            } else {
                player.sendPacket(new SystemMessage(SystemMessageId.C1_IS_ON_ANOTHER_TASK_PLEASE_TRY_AGAIN_LATER)
                        .addPcName(target));
            }
        } else {
            player.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
        }
    }
}
