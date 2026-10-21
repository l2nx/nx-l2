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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCloseMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExMPCCPartyInfoUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOpenMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.Getter;

/**
 * This class serves as a container for command channels.
 * @author chris_00
 */
public class CommandChannel extends AbstractPlayerGroup {
    @Getter
    private final Collection<Party> parties = ConcurrentHashMap.newKeySet();

    @Getter
    private Player leader;

    @Getter
    private int level;

    /**
     * Create a new command channel and add the leader's party to it.
     * @param leader the leader of this command channel
     */
    public CommandChannel(Player leader) {
        this.leader = leader;
        final Party party = leader.getParty();
        parties.add(party);
        level = party.getLevel();
        party.setCommandChannel(this);
        party.broadcastMessage(SystemMessageId.THE_COMMAND_CHANNEL_HAS_BEEN_FORMED);
        party.broadcastPacket(ExOpenMPCC.STATIC_PACKET);
    }

    /**
     * Add a party to this command channel.
     * @param party the party to add
     */
    public void addParty(Party party) {
        if (party == null) {
            return;
        }

        // Update the CCinfo for existing players.
        broadcastPacket(new ExMPCCPartyInfoUpdate(party, 1));
        parties.add(party);
        if (party.getLevel() > level) {
            level = party.getLevel();
        }

        party.setCommandChannel(this);
        party.broadcastPacket(new SystemMessage(SystemMessageId.YOU_HAVE_JOINED_THE_COMMAND_CHANNEL));
        party.broadcastPacket(ExOpenMPCC.STATIC_PACKET);
    }

    /**
     * Remove a party from this command channel.
     * @param party the party to remove
     */
    public void removeParty(Party party) {
        if (party == null) {
            return;
        }

        parties.remove(party);
        level = 0;
        for (Party pty : parties) {
            if (pty.getLevel() > level) {
                level = pty.getLevel();
            }
        }

        party.setCommandChannel(null);
        party.broadcastPacket(new ExCloseMPCC());
        if (parties.size() < 2) {
            broadcastPacket(new SystemMessage(SystemMessageId.THE_COMMAND_CHANNEL_HAS_BEEN_DISBANDED));
            disbandChannel();
        } else {
            // Update the CCinfo for existing players.
            broadcastPacket(new ExMPCCPartyInfoUpdate(party, 0));
        }
    }

    /**
     * Disband this command channel.
     */
    public void disbandChannel() {
        if (parties != null) {
            for (Party party : parties) {
                if (party != null) {
                    removeParty(party);
                }
            }

            parties.clear();
        }
    }

    /**
     * @return the total count of all members of this command channel
     */
    @Override
    public int getMemberCount() {
        int count = 0;
        for (Party party : parties) {
            if (party != null) {
                count += party.getMemberCount();
            }
        }

        return count;
    }

    /**
     * @return a list of all members in this command channel
     */
    @Override
    public List<Player> getMembers() {
        final List<Player> members = new ArrayList<>();
        for (Party party : parties) {
            members.addAll(party.getMembers());
        }

        return members;
    }

    @Override
    public void setLeader(Player leader) {
        this.leader = leader;
        if (leader.getLevel() > level) {
            level = leader.getLevel();
        }
    }

    /**
     * @param obj
     * @return true if proper condition for RaidWar
     */
    public boolean meetRaidWarCondition(WorldObject obj) {
        if (!(obj.isCreature() && obj.asCreature().isRaid())) {
            return false;
        }

        return (getMemberCount() >= PlayerConfig.LOOT_RAIDS_PRIVILEGE_CC_SIZE);
    }

    /**
     * Check if a given player is in this command channel.
     * @param player the player to check
     * @return {@code true} if he does, {@code false} otherwise
     */
    @Override
    public boolean containsPlayer(Player player) {
        if ((parties != null) && !parties.isEmpty()) {
            for (Party party : parties) {
                if (party.containsPlayer(player)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Iterates over all command channel members without the need to allocate a new list
     * @see app.l2nx.highfive.entity.groups.AbstractPlayerGroup#forEachMember(Function)
     */
    @Override
    public boolean forEachMember(Function<Player, Boolean> function) {
        if ((parties != null) && !parties.isEmpty()) {
            for (Party party : parties) {
                if (!party.forEachMember(function)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Check whether the leader of this command channel is the same as the leader of the specified command channel<br>
     * (which essentially means they're the same group).
     * @param cc the other command channel to check against
     * @return {@code true} if this command channel equals the specified command channel, {@code false} otherwise
     */
    public boolean equals(CommandChannel cc) {
        return (cc != null) && (getLeaderObjectId() == cc.getLeaderObjectId());
    }
}
