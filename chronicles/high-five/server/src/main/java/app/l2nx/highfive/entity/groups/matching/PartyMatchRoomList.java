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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ExClosePartyRoom;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

/**
 * @author Gnacik
 */
public class PartyMatchRoomList {
    @Getter
    private int maxId = 1;

    private final Map<Integer, PartyMatchRoom> rooms;

    protected PartyMatchRoomList() {
        rooms = new ConcurrentHashMap<>();
    }

    public synchronized void addPartyMatchRoom(int id, PartyMatchRoom room) {
        rooms.put(id, room);
        maxId++;
    }

    public void deleteRoom(int id) {
        for (Player member : getRoom(id).getPartyMembers()) {
            if (member == null) {
                continue;
            }

            member.sendPacket(new ExClosePartyRoom());
            member.sendPacket(SystemMessageId.THE_PARTY_ROOM_HAS_BEEN_DISBANDED);

            member.setPartyRoom(0);
            // _member.setPartyMatching(0);
            member.broadcastUserInfo();
        }

        rooms.remove(id);
    }

    public PartyMatchRoom getRoom(int id) {
        return rooms.get(id);
    }

    public Collection<PartyMatchRoom> getRooms() {
        return rooms.values();
    }

    public int getPartyMatchRoomCount() {
        return rooms.size();
    }

    public PartyMatchRoom getPlayerRoom(Player player) {
        for (PartyMatchRoom room : rooms.values()) {
            for (Player member : room.getPartyMembers()) {
                if (member.equals(player)) {
                    return room;
                }
            }
        }

        return null;
    }

    public int getPlayerRoomId(Player player) {
        for (PartyMatchRoom room : rooms.values()) {
            for (Player member : room.getPartyMembers()) {
                if (member.equals(player)) {
                    return room.getId();
                }
            }
        }

        return -1;
    }

    public static PartyMatchRoomList getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final PartyMatchRoomList INSTANCE = new PartyMatchRoomList();
    }
}
