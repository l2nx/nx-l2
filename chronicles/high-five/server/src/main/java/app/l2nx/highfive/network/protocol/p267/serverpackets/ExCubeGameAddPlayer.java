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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author mrTJO
 */
public class ExCubeGameAddPlayer extends ServerPacket {
    private final Player player;
    private final boolean redTeam;

    /**
     * Add Player To Minigame Waiting List
     * @param player Player Instance
     * @param isRedTeam Is Player from Red Team?
     */
    public ExCubeGameAddPlayer(Player player, boolean isRedTeam) {
        this.player = player;
        redTeam = isRedTeam;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_BLOCK_UP_SET_LIST.writeId(this, buffer);
        buffer.writeInt(1);
        buffer.writeInt(0xffffffff);
        buffer.writeInt(redTeam);
        buffer.writeInt(player.getObjectId());
        buffer.writeString(player.getName());
    }
}
