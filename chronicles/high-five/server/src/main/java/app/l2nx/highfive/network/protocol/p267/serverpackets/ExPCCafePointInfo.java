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

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author KenM
 */
public class ExPCCafePointInfo extends ServerPacket {
    private final int points;
    private final int mAddPoint;
    private final int mPeriodType;
    private final int remainTime;
    private final int pointType;
    private final int time;

    public ExPCCafePointInfo() {
        points = 0;
        mAddPoint = 0;
        remainTime = 0;
        mPeriodType = 0;
        pointType = 0;
        time = 0;
    }

    public ExPCCafePointInfo(int points, int pointsToAdd, int time) {
        this.points = points;
        mAddPoint = pointsToAdd;
        mPeriodType = 1;
        remainTime = 42; // No idea why but retail sends 42..
        pointType = pointsToAdd < 0 ? 3 : 0; // When using points is 3
        this.time = time;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_PC_CAFE_POINT_INFO.writeId(this, buffer);
        buffer.writeInt(points); // num points
        buffer.writeInt(mAddPoint); // points inc display
        buffer.writeByte(mPeriodType); // period(0=don't show window,1=acquisition,2=use points)
        buffer.writeInt(remainTime); // period hours left
        buffer.writeByte(pointType); // points inc display color(0=yellow, 1=cyan-blue, 2=red, all other black)
        buffer.writeInt(time * 3); // value is in seconds * 3
    }
}
