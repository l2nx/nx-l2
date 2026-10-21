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
import app.l2nx.highfive.entity.groups.matching.PartyMatchWaitingList;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gnacik
 */
public class ExListPartyMatchingWaitingRoom extends ServerPacket {
    private final Player player;
    // private final int _page;
    private final int minLevel;
    private final int maxLevel;
    private final int mode;
    private final List<Player> members;

    public ExListPartyMatchingWaitingRoom(Player player, int page, int minLevel, int maxLevel, int mode) {
        this.player = player;
        // _page = page;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.mode = mode;
        members = new ArrayList<>();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_LIST_PARTY_MATCHING_WAITING_ROOM.writeId(this, buffer);
        if (mode == 0) {
            buffer.writeInt(0);
            buffer.writeInt(0);
            return;
        }

        for (Player cha : PartyMatchWaitingList.getInstance().getPlayers()) {
            if ((cha == null) || (cha == player)) {
                continue;
            }

            if (!cha.isPartyWaiting()) {
                PartyMatchWaitingList.getInstance().removePlayer(cha);
                continue;
            } else if ((cha.getLevel() < minLevel) || (cha.getLevel() > maxLevel)) {
                continue;
            }

            members.add(cha);
        }

        buffer.writeInt(1); // Page?
        buffer.writeInt(members.size());
        for (Player member : members) {
            buffer.writeString(member.getName());
            buffer.writeInt(member.getActiveClass());
            buffer.writeInt(member.getLevel());
        }
    }
}
