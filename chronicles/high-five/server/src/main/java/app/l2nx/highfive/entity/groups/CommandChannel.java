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
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCloseMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExMPCCPartyInfoUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOpenMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;

/**
 * @author chris_00
 */
@SingleWriter
public class CommandChannel extends AbstractPlayerGroup {
    // Joining order, which the channel info window lists; a party joins once.
    private final ObjectArrayList<Party> parties = new ObjectArrayList<>();

    private Player leader;

    private int level;

    public CommandChannel(Player leader) {
        this.leader = leader;
        final Party party = leader.getParty();
        parties.add(party);
        level = party.getLevel();
        party.setCommandChannel(this);
        party.broadcastMessage(SystemMessageId.THE_COMMAND_CHANNEL_HAS_BEEN_FORMED);
        party.broadcastPacket(ExOpenMPCC.STATIC_PACKET);
    }

    @Override
    protected void addMembersTo(List<? super Player> target) {
        for (Party party : parties) {
            party.addMembersTo(target);
        }
    }

    @Override
    protected boolean hasMember(Player player) {
        for (Party party : parties) {
            if (party.hasMember(player)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected int memberCount() {
        int count = 0;
        for (Party party : parties) {
            count += party.memberCount();
        }

        return count;
    }

    @Override
    protected Player leader() {
        return leader;
    }

    public List<Party> getParties() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(parties));
    }

    /** Adds the parties to the target in joining order; a caller walking them passes a ScratchBuffers lease. */
    public void collectParties(List<? super Party> target) {
        WorldPartition.checkOwner();
        target.addAll(parties);
    }

    public int getPartyCount() {
        WorldPartition.checkOwner();
        return parties.size();
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return level;
    }

    public void addParty(Party party) {
        WorldPartition.checkOwner();
        if ((party == null) || parties.contains(party)) {
            return;
        }

        broadcastPacket(new ExMPCCPartyInfoUpdate(party, 1));
        parties.add(party);

        if (party.getLevel() > level) {
            level = party.getLevel();
        }

        party.setCommandChannel(this);
        party.broadcastPacket(new SystemMessage(SystemMessageId.YOU_HAVE_JOINED_THE_COMMAND_CHANNEL));
        party.broadcastPacket(ExOpenMPCC.STATIC_PACKET);
    }

    public void removeParty(Party party) {
        WorldPartition.checkOwner();
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
            broadcastPacket(new ExMPCCPartyInfoUpdate(party, 0));
        }
    }

    public void disbandChannel() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Party> walk = ScratchBuffers.snapshot(parties)) {
            for (Party party : walk) {
                // Removing the second last party disbands the channel from inside removeParty, which removes the rest.
                if ((party != null) && parties.contains(party)) {
                    removeParty(party);
                }
            }
        }

        parties.clear();
    }

    @Override
    public void setLeader(Player leader) {
        WorldPartition.checkOwner();
        this.leader = leader;
        if (leader.getLevel() > level) {
            level = leader.getLevel();
        }
    }

    public boolean meetRaidWarCondition(WorldObject obj) {
        WorldPartition.checkOwner();
        if (!(obj.isCreature() && obj.asCreature().isRaid())) {
            return false;
        }

        return (memberCount() >= PlayerConfig.LOOT_RAIDS_PRIVILEGE_CC_SIZE);
    }
}
