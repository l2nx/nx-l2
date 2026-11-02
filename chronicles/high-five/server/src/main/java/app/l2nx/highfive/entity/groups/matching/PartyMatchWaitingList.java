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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;

/**
 * @author Gnacik
 */
@SingleWriter
public class PartyMatchWaitingList {
    // Joining order, the order of the waiting list window.
    private final ObjectLinkedOpenHashSet<Player> players = new ObjectLinkedOpenHashSet<>();

    protected PartyMatchWaitingList() {}

    public List<Player> getPlayers() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(players));
    }

    public boolean contains(Player player) {
        WorldPartition.checkOwner();
        return players.contains(player);
    }

    public void addPlayer(Player player) {
        WorldPartition.checkOwner();
        players.add(player);
    }

    public void removePlayer(Player player) {
        WorldPartition.checkOwner();
        players.remove(player);
    }

    public static PartyMatchWaitingList getInstance() {
        WorldPartition.checkOwner();
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final PartyMatchWaitingList INSTANCE = new PartyMatchWaitingList();
    }
}
