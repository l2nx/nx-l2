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
package app.l2nx.highfive.entity.groups;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.chat.ChatService;
import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The public methods check the owner and reach the members through unchecked internals the subclasses implement, so
 * a command channel walks all its parties under one check.
 * @author Battlecruiser
 */
@SingleWriter
public abstract class AbstractPlayerGroup {
    /** Adds the members to the target in the order the client shows them, the leader first. */
    protected abstract void addMembersTo(List<? super Player> target);

    protected abstract boolean hasMember(Player player);

    protected abstract int memberCount();

    /** @return the leader, {@code null} for a group with no members */
    protected abstract Player leader();

    /** @return a read-only snapshot of the members, the leader first */
    public final List<Player> getMembers() {
        WorldPartition.checkOwner();
        final ObjectArrayList<Player> members = new ObjectArrayList<>(memberCount());
        addMembersTo(members);
        return ObjectLists.unmodifiable(members);
    }

    /**
     * Adds the members to the target, the leader first. A caller that walks the members passes a
     * {@link ScratchBuffers} lease, which spares the copy {@link #getMembers()} allocates.
     */
    public final void collectMembers(List<? super Player> target) {
        WorldPartition.checkOwner();
        addMembersTo(target);
    }

    public final List<Integer> getMembersObjectId() {
        WorldPartition.checkOwner();
        final List<Integer> ids = new ArrayList<>(memberCount());
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            addMembersTo(members);
            for (Player member : members) {
                ids.add(member.getObjectId());
            }
        }

        return ids;
    }

    public final Player getLeader() {
        WorldPartition.checkOwner();
        return leader();
    }

    public abstract void setLeader(Player leader);

    public final int getLeaderObjectId() {
        WorldPartition.checkOwner();
        final Player leader = leader();
        if (leader == null) {
            return 0;
        }

        return leader.getObjectId();
    }

    public final boolean isLeader(Player player) {
        WorldPartition.checkOwner();
        if (player == null) {
            return false;
        }

        final Player leader = leader();
        if (leader == null) {
            return false;
        }

        return leader.getObjectId() == player.getObjectId();
    }

    public final int getMemberCount() {
        WorldPartition.checkOwner();
        return memberCount();
    }

    public abstract int getLevel();

    public final void broadcastPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            addMembersTo(members);
            for (Player member : members) {
                if (member != null) {
                    member.sendPacket(packet);
                }
            }
        }
    }

    public final void broadcastMessage(SystemMessageId message) {
        WorldPartition.checkOwner();
        broadcastPacket(new SystemMessage(message));
    }

    public final void broadcastString(String text) {
        WorldPartition.checkOwner();
        broadcastPacket(new SystemMessage(text));
    }

    public final void broadcastChat(ChatMessage message, Player sender) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty();
                ScratchBuffers.Snapshot<Player> receivers = ScratchBuffers.empty()) {
            addMembersTo(members);
            for (Player member : members) {
                if ((member != null) && !BlockList.isBlocked(member, sender)) {
                    receivers.add(member);
                }
            }

            ChatService.getInstance().deliverAll(receivers, message);
        }
    }

    public final boolean containsPlayer(Player player) {
        WorldPartition.checkOwner();
        return hasMember(player);
    }

    public final Player getRandomPlayer() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            addMembersTo(members);
            return members.get(ClockBridge.random().nextInt(members.size()));
        }
    }

    /** The procedure returns {@code false} to stop the walk; the result is {@code false} when it did. */
    public final boolean forEachMember(Function<Player, Boolean> procedure) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            addMembersTo(members);
            for (Player member : members) {
                if (!procedure.apply(member)) {
                    return false;
                }
            }
        }

        return true;
    }
}
