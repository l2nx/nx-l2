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

import app.l2nx.highfive.mechanics.olympiad.OlympiadInfo;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.List;

/**
 * @author JIV
 */
public class ExOlympiadMatchResult extends ServerPacket {
    private final boolean tie;
    private int winTeam; // 1,2
    private int loseTeam = 2;
    private final List<OlympiadInfo> winnerList;
    private final List<OlympiadInfo> loserList;

    public ExOlympiadMatchResult(
            boolean tie, int winTeam, List<OlympiadInfo> winnerList, List<OlympiadInfo> loserList) {
        this.tie = tie;
        this.winTeam = winTeam;
        this.winnerList = winnerList;
        this.loserList = loserList;
        if (this.winTeam == 2) {
            loseTeam = 1;
        } else if (this.winTeam == 0) {
            this.winTeam = 1;
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_RECEIVE_OLYMPIAD.writeId(this, buffer);
        buffer.writeInt(1); // Type 0 = Match List, 1 = Match Result
        buffer.writeInt(tie); // 0 - win, 1 - tie
        buffer.writeString(winnerList.get(0).name());
        buffer.writeInt(winTeam);
        buffer.writeInt(winnerList.size());
        for (OlympiadInfo info : winnerList) {
            buffer.writeString(info.name());
            buffer.writeString(info.clanName());
            buffer.writeInt(info.clanId());
            buffer.writeInt(info.classId());
            buffer.writeInt(info.damage());
            buffer.writeInt(info.currentPoints());
            buffer.writeInt(info.diffPoints());
        }

        buffer.writeInt(loseTeam);
        buffer.writeInt(loserList.size());
        for (OlympiadInfo info : loserList) {
            buffer.writeString(info.name());
            buffer.writeString(info.clanName());
            buffer.writeInt(info.clanId());
            buffer.writeInt(info.classId());
            buffer.writeInt(info.damage());
            buffer.writeInt(info.currentPoints());
            buffer.writeInt(info.diffPoints());
        }
    }
}
