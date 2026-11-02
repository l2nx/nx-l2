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
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExClosePartyRoom;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;

/**
 * @author Gnacik
 */
@SingleWriter
public class PartyMatchRoomList {
    private int maxId = 1;

    // Creation order, the order of the room list window.
    private final Int2ObjectLinkedOpenHashMap<PartyMatchRoom> rooms = new Int2ObjectLinkedOpenHashMap<>();

    protected PartyMatchRoomList() {}

    public int getMaxId() {
        WorldPartition.checkOwner();
        return maxId;
    }

    public void addPartyMatchRoom(int id, PartyMatchRoom room) {
        WorldPartition.checkOwner();
        rooms.put(id, room);
        maxId++;
    }

    public void deleteRoom(int id) {
        WorldPartition.checkOwner();
        final PartyMatchRoom room = rooms.get(id);
        if (room == null) {
            return;
        }

        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            room.collectMembers(members);
            for (Player member : members) {
                // The owner of an abandoned room may have moved on to another room, which stays open for him.
                if ((member == null) || (member.getPartyRoom() != id)) {
                    continue;
                }

                member.sendPacket(new ExClosePartyRoom());
                member.sendPacket(SystemMessageId.THE_PARTY_ROOM_HAS_BEEN_DISBANDED);

                member.setPartyRoom(0);
                member.broadcastUserInfo();
            }
        }

        rooms.remove(id);
    }

    /**
     * Deletes the room when the requester owns it.
     * @return {@code false}, deleting nothing, for an unknown room or a requester who is not its owner
     */
    public boolean dismissRoom(Player requester, int id) {
        WorldPartition.checkOwner();
        final PartyMatchRoom room = rooms.get(id);
        if ((room == null) || (room.getOwner() != requester)) {
            return false;
        }

        deleteRoom(id);
        return true;
    }

    public void deleteAbandonedRooms() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<PartyMatchRoom> walk = ScratchBuffers.snapshot(rooms.values())) {
            for (PartyMatchRoom room : walk) {
                if (room.isAbandoned()) {
                    deleteRoom(room.getId());
                }
            }
        }
    }

    public PartyMatchRoom getRoom(int id) {
        WorldPartition.checkOwner();
        return rooms.get(id);
    }

    public List<PartyMatchRoom> getRooms() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(rooms.values()));
    }

    public int getPartyMatchRoomCount() {
        WorldPartition.checkOwner();
        return rooms.size();
    }

    public PartyMatchRoom getPlayerRoom(Player player) {
        WorldPartition.checkOwner();
        for (PartyMatchRoom room : rooms.values()) {
            if (room.containsMember(player)) {
                return room;
            }
        }

        return null;
    }

    public int getPlayerRoomId(Player player) {
        WorldPartition.checkOwner();
        final PartyMatchRoom room = getPlayerRoom(player);
        return room != null ? room.getId() : -1;
    }

    public static PartyMatchRoomList getInstance() {
        WorldPartition.checkOwner();
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final PartyMatchRoomList INSTANCE = new PartyMatchRoomList();
    }
}
