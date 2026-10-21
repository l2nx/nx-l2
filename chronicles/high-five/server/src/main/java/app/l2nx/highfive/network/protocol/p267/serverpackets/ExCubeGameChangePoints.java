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

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author mrTJO
 */
public class ExCubeGameChangePoints extends ServerPacket {
    private final int timeLeft;
    private final int bluePoints;
    private final int redPoints;

    /**
     * Change Client Point Counter
     * @param timeLeft Time Left before Minigame's End
     * @param bluePoints Current Blue Team Points
     * @param redPoints Current Red Team Points
     */
    public ExCubeGameChangePoints(int timeLeft, int bluePoints, int redPoints) {
        this.timeLeft = timeLeft;
        this.bluePoints = bluePoints;
        this.redPoints = redPoints;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_BLOCK_UP_SET_STATE.writeId(this, buffer);
        buffer.writeInt(2);
        buffer.writeInt(timeLeft);
        buffer.writeInt(bluePoints);
        buffer.writeInt(redPoints);
    }
}
