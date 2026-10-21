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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author mrTJO
 */
public class ExCubeGameExtendedChangePoints extends ServerPacket {
    private final int timeLeft;
    private final int bluePoints;
    private final int redPoints;
    private final boolean redTeam;
    private final Player player;
    private final int playerPoints;

    /**
     * Update a Secret Point Counter (used by client when receive ExCubeGameEnd)
     * @param timeLeft Time Left before Minigame's End
     * @param bluePoints Current Blue Team Points
     * @param redPoints Current Blue Team points
     * @param isRedTeam Is Player from Red Team?
     * @param player Player Instance
     * @param playerPoints Current Player Points
     */
    public ExCubeGameExtendedChangePoints(
            int timeLeft, int bluePoints, int redPoints, boolean isRedTeam, Player player, int playerPoints) {
        this.timeLeft = timeLeft;
        this.bluePoints = bluePoints;
        this.redPoints = redPoints;
        redTeam = isRedTeam;
        this.player = player;
        this.playerPoints = playerPoints;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_BLOCK_UP_SET_STATE.writeId(this, buffer);
        buffer.writeInt(0);
        buffer.writeInt(timeLeft);
        buffer.writeInt(bluePoints);
        buffer.writeInt(redPoints);
        buffer.writeInt(redTeam);
        buffer.writeInt(player.getObjectId());
        buffer.writeInt(playerPoints);
    }
}
