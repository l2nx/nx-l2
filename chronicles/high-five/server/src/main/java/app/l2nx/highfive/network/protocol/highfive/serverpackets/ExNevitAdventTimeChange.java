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
 * @author mochitto
 */
public class ExNevitAdventTimeChange extends ServerPacket {
    private final boolean paused;
    private final int time;

    public ExNevitAdventTimeChange(int time, boolean paused) {
        this.time = (time > 14400) ? 14400 : time;
        this.paused = paused;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_NAVIT_ADVENT_TIME_CHANGE.writeId(this, buffer);

        // state 0 - pause 1 - started
        buffer.writeByte(!paused);

        // left time in ms max is 16000 its 4m and state is automatically changed to quit
        buffer.writeInt(time);
    }
}
