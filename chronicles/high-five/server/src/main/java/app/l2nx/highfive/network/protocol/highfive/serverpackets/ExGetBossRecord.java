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
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author KenM
 */
public class ExGetBossRecord extends ServerPacket {
    private final Map<Integer, Integer> bossRecordInfo;
    private final int ranking;
    private final int totalPoints;

    public ExGetBossRecord(int ranking, int totalScore, Map<Integer, Integer> list) {
        this.ranking = ranking;
        totalPoints = totalScore;
        bossRecordInfo = list;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_GET_BOSS_RECORD.writeId(this, buffer);
        buffer.writeInt(ranking);
        buffer.writeInt(totalPoints);
        if (bossRecordInfo == null) {
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
        } else {
            buffer.writeInt(bossRecordInfo.size()); // list size
            for (Entry<Integer, Integer> entry : bossRecordInfo.entrySet()) {
                buffer.writeInt(entry.getKey());
                buffer.writeInt(entry.getValue());
                buffer.writeInt(0); // ??
            }
        }
    }
}
