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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExManagePartyRoomMember;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;

/**
 * @author Gnacik
 */
@SingleWriter
public class PartyMatchRoom {
    private final int id;

    private String title;

    private int lootType;

    private int minLevel;

    private int maxLevel;

    private int maxMembers;

    // The owner first, then the members in joining order, as the room window lists them.
    private final ObjectArrayList<Player> partyMembers = new ObjectArrayList<>();

    public PartyMatchRoom(int id, String title, int loot, int minLevel, int maxLevel, int maxmem, Player owner) {
        this.id = id;
        this.title = title;
        lootType = loot;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        maxMembers = maxmem;
        partyMembers.add(owner);
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public String getTitle() {
        WorldPartition.checkOwner();
        return title;
    }

    public int getLootType() {
        WorldPartition.checkOwner();
        return lootType;
    }

    public int getMinLevel() {
        WorldPartition.checkOwner();
        return minLevel;
    }

    public int getMaxLevel() {
        WorldPartition.checkOwner();
        return maxLevel;
    }

    public int getMaxMembers() {
        WorldPartition.checkOwner();
        return maxMembers;
    }

    /** @return a read-only snapshot of the members, the owner first */
    public List<Player> getPartyMembers() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(partyMembers));
    }

    /**
     * Applies the settings the owner sent.
     * @return {@code false}, changing nothing, when the editor is not the owner of the room
     */
    public boolean changeSettings(
            Player editor, int maxMembers, int minLevel, int maxLevel, int lootType, String title) {
        WorldPartition.checkOwner();
        if (editor != getOwner()) {
            return false;
        }

        this.maxMembers = maxMembers;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.lootType = lootType;
        this.title = title;
        return true;
    }

    /** Adds the members to the target, the owner first; a caller walking them passes a ScratchBuffers lease. */
    public void collectMembers(List<? super Player> target) {
        WorldPartition.checkOwner();
        target.addAll(partyMembers);
    }

    public boolean containsMember(Player player) {
        WorldPartition.checkOwner();
        return partyMembers.contains(player);
    }

    public void addMember(Player player) {
        WorldPartition.checkOwner();
        partyMembers.add(player);
    }

    public void deleteMember(Player player) {
        WorldPartition.checkOwner();
        if (player != partyMembers.get(0)) {
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
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(partyMembers)) {
            for (Player member : walk) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_LEFT_THE_PARTY_ROOM);
                sm.addString(player.getName());
                member.sendPacket(sm);
                member.sendPacket(new ExManagePartyRoomMember(player, this, 2));
            }
        }
    }

    public void changeLeader(Player newLeader) {
        WorldPartition.checkOwner();
        final Player oldLeader = partyMembers.get(0);

        partyMembers.remove(newLeader);

        partyMembers.set(0, newLeader);

        partyMembers.add(oldLeader);

        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(partyMembers)) {
            for (Player member : walk) {
                member.sendPacket(new ExManagePartyRoomMember(newLeader, this, 1));
                member.sendPacket(new ExManagePartyRoomMember(oldLeader, this, 1));
                member.sendPacket(SystemMessageId.THE_LEADER_OF_THE_PARTY_ROOM_HAS_CHANGED);
            }
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
     */
    public int getLocation() {
        WorldPartition.checkOwner();
        return MapRegionData.getInstance().getBBs(partyMembers.get(0));
    }

    /** @return {@code true} for a room whose owner left, logged off or moved to another room */
    public boolean isAbandoned() {
        WorldPartition.checkOwner();
        if (partyMembers.isEmpty()) {
            return true;
        }

        final Player owner = partyMembers.get(0);
        return (owner == null) || !owner.isOnline() || (owner.getPartyRoom() != id);
    }

    public int getMembers() {
        WorldPartition.checkOwner();
        return partyMembers.size();
    }

    public Player getOwner() {
        WorldPartition.checkOwner();
        return partyMembers.get(0);
    }
}
