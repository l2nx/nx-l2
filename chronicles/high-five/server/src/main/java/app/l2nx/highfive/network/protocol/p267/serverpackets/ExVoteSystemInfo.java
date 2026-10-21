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
 * ExVoteSystemInfo packet implementation.
 * @author Gnacik
 */
public class ExVoteSystemInfo extends ServerPacket {
    private final int recomLeft;
    private final int recomHave;
    private final int bonusTime;
    private final int bonusVal;
    private final int bonusType;

    public ExVoteSystemInfo(Player player) {
        recomLeft = player.getRecomLeft();
        recomHave = player.getRecomHave();
        bonusTime = player.getNevitHourglassTime();
        bonusVal = player.getNevitHourglassBonus();
        bonusType = player.getNevitHourglassStatus();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_VOTE_SYSTEM_INFO.writeId(this, buffer);
        buffer.writeInt(recomLeft);
        buffer.writeInt(recomHave);
        buffer.writeInt(bonusTime);
        buffer.writeInt(bonusVal);
        buffer.writeInt(bonusType);
    }
}
