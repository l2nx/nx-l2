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
package app.l2nx.highfive.entity.groups.matching;

import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExManagePartyRoomMember;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gnacik
 */
public class PartyMatchRoom {
    @Getter
    private final int id;

    @Getter
    @Setter
    private String title;

    @Getter
    @Setter
    private int lootType;

    @Getter
    @Setter
    private int minLevel;

    @Getter
    @Setter
    private int maxLevel;

    @Getter
    @Setter
    private int maxMembers;

    @Getter
    private final List<Player> partyMembers = new ArrayList<>();

    public PartyMatchRoom(int id, String title, int loot, int minLevel, int maxLevel, int maxmem, Player owner) {
        this.id = id;
        this.title = title;
        lootType = loot;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        maxMembers = maxmem;
        partyMembers.add(owner);
    }

    public void addMember(Player player) {
        partyMembers.add(player);
    }

    public void deleteMember(Player player) {
        if (player != getOwner()) {
            partyMembers.remove(player);
            notifyMembersAboutExit(player);
        } else if (partyMembers.size() == 1) {
            PartyMatchRoomList.getInstance().deleteRoom(id);
        } else {
            changeLeader(partyMembers.get(1));
            deleteMember(player);
        }
    }

    public void notifyMembersAboutExit(Player player) {
        for (Player member : partyMembers) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_LEFT_THE_PARTY_ROOM);
            sm.addString(player.getName());
            member.sendPacket(sm);
            member.sendPacket(new ExManagePartyRoomMember(player, this, 2));
        }
    }

    public void changeLeader(Player newLeader) {
        // Get current leader
        final Player oldLeader = partyMembers.get(0);

        // Remove new leader
        partyMembers.remove(newLeader);

        // Move him to first position.
        partyMembers.set(0, newLeader);

        // Add old leader as normal member.
        partyMembers.add(oldLeader);

        // Broadcast change
        for (Player member : partyMembers) {
            member.sendPacket(new ExManagePartyRoomMember(newLeader, this, 1));
            member.sendPacket(new ExManagePartyRoomMember(oldLeader, this, 1));
            member.sendPacket(SystemMessageId.THE_LEADER_OF_THE_PARTY_ROOM_HAS_CHANGED);
        }
    }

    /**
     * <ul>
     * <li>1 : Talking Island</li>
     * <li>2 : Gludio</li>
     * <li>3 : Dark Elven Ter.</li>
     * <li>4 : Elven Territory</li>
     * <li>5 : Dion</li>
     * <li>6 : Giran</li>
     * <li>7 : Neutral Zone</li>
     * <li>8 : Lyonn</li>
     * <li>9 : Schuttgart</li>
     * <li>10 : Oren</li>
     * <li>11 : Hunters Village</li>
     * <li>12 : Innadril</li>
     * <li>13 : Aden</li>
     * <li>14 : Rune</li>
     * <li>15 : Goddard</li>
     * </ul>
     * @return the id
     */
    public int getLocation() {
        return MapRegionData.getInstance().getBBs(partyMembers.get(0));
    }

    public int getMembers() {
        return partyMembers.size();
    }

    public Player getOwner() {
        return partyMembers.get(0);
    }
}
