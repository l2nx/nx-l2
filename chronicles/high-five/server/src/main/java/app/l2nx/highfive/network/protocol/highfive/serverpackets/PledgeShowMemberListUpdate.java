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
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author -Wooden-
 */
public class PledgeShowMemberListUpdate extends ServerPacket {
    private final int pledgeType;
    private boolean hasSponsor;
    private final String name;
    private final int level;
    private final int classId;
    private final int objectId;
    private final boolean online;
    private final int race;
    private final boolean female;

    public PledgeShowMemberListUpdate(Player player) {
        pledgeType = player.getPledgeType();
        if (pledgeType == Clan.SUBUNIT_ACADEMY) {
            hasSponsor = player.getSponsor() != 0;
        } else {
            hasSponsor = false;
        }

        name = player.getName();
        level = player.getLevel();
        classId = player.getPlayerClass().getId();
        race = player.getRace().ordinal();
        female = player.getAppearance().isFemale();
        objectId = player.getObjectId();
        online = player.isOnline();
    }

    public PledgeShowMemberListUpdate(ClanMember member) {
        name = member.getName();
        level = member.getLevel();
        classId = member.getClassId();
        objectId = member.getObjectId();
        online = member.isOnline();
        pledgeType = member.getPledgeType();
        race = member.getRaceOrdinal();
        female = member.getSex();
        if (pledgeType == Clan.SUBUNIT_ACADEMY) {
            hasSponsor = member.getSponsor() != 0;
        } else {
            hasSponsor = false;
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PLEDGE_SHOW_MEMBER_LIST_UPDATE.writeId(this, buffer);
        buffer.writeString(name);
        buffer.writeInt(level);
        buffer.writeInt(classId);
        buffer.writeInt(female);
        buffer.writeInt(race);
        if (online) {
            buffer.writeInt(objectId);
            buffer.writeInt(pledgeType);
        } else {
            // When going offline send as 0.
            buffer.writeInt(0);
            buffer.writeInt(0);
        }

        buffer.writeInt(hasSponsor);
    }
}
