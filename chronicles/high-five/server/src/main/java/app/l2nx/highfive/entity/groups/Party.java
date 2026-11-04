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
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.config.custom.ClassBalanceConfig;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoom;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoomList;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.managers.DuelManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.PcCafePointsManager;
import app.l2nx.highfive.mechanics.sevensigns.DimensionalRift;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExAskModifyPartyLooting;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCloseMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOpenMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPartyPetWindowAdd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPartyPetWindowDelete;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExSetPartyLooting;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartyMemberPosition;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartySmallWindowAdd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartySmallWindowAll;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartySmallWindowDelete;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartySmallWindowDeleteAll;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.util.LocationUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author nuocnam
 */
@Slf4j
@SingleWriter
public class Party extends AbstractPlayerGroup {

    // @formatter:off
    private static final double[] BONUS_EXP_SP = {1.0, 1.10, 1.20, 1.30, 1.40, 1.50, 2.0, 2.10, 2.20};
    // @formatter:on

    private static final Duration PARTY_POSITION_BROADCAST_INTERVAL = Duration.ofSeconds(12);
    private static final Duration PARTY_DISTRIBUTION_TYPE_REQUEST_TIMEOUT = Duration.ofSeconds(15);

    // The order is the client's party window: the leader first, then the members in joining order.
    private final ObjectArrayList<Player> members = new ObjectArrayList<>();

    private boolean pendingInvitation = false;
    private long pendingInviteTimeout;

    private int level = 0;

    private PartyDistributionType distributionType = PartyDistributionType.FINDERS_KEEPERS;

    private PartyDistributionType changeRequestDistributionType;
    private ScheduledFuture<?> changeDistributionTypeRequestTask = null;
    private IntSet changeDistributionTypeAnswers = null;
    private int itemLastLoot = 0;

    private CommandChannel commandChannel = null;

    private DimensionalRift dimensionalRift;

    private ScheduledFuture<?> positionBroadcastTask = null;
    protected PartyMemberPosition positionPacket;
    private boolean disbanding = false;
    // Taken when the disband starts: its leader may leave before the last member, who resolves the channel.
    private boolean ledChannelWhenDisbanding;
    private Object ucState = null;

    public Party(Player leader, PartyDistributionType partyDistributionType) {
        members.add(leader);
        level = leader.getLevel();
        distributionType = partyDistributionType;
    }

    @Override
    protected void addMembersTo(List<? super Player> target) {
        target.addAll(members);
    }

    @Override
    protected boolean hasMember(Player player) {
        return members.contains(player);
    }

    @Override
    protected int memberCount() {
        return members.size();
    }

    @Override
    protected Player leader() {
        return members.isEmpty() ? null : members.get(0);
    }

    public Player getMember(int index) {
        WorldPartition.checkOwner();
        return members.get(index);
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return level;
    }

    public PartyDistributionType getDistributionType() {
        WorldPartition.checkOwner();
        return distributionType;
    }

    public CommandChannel getCommandChannel() {
        WorldPartition.checkOwner();
        return commandChannel;
    }

    public void setCommandChannel(CommandChannel commandChannel) {
        WorldPartition.checkOwner();
        this.commandChannel = commandChannel;
    }

    public DimensionalRift getDimensionalRift() {
        WorldPartition.checkOwner();
        return dimensionalRift;
    }

    public void setDimensionalRift(DimensionalRift dimensionalRift) {
        WorldPartition.checkOwner();
        this.dimensionalRift = dimensionalRift;
    }

    public boolean getPendingInvitation() {
        WorldPartition.checkOwner();
        return pendingInvitation;
    }

    public void setPendingInvitation(boolean value) {
        WorldPartition.checkOwner();
        pendingInvitation = value;
        pendingInviteTimeout = GameTimeTaskManager.getInstance().getGameTicks()
                + (Player.REQUEST_TIMEOUT * GameTimeTaskManager.TICKS_PER_SECOND);
    }

    public boolean isInvitationRequestExpired() {
        WorldPartition.checkOwner();
        return pendingInviteTimeout <= GameTimeTaskManager.getInstance().getGameTicks();
    }

    private Player getCheckedRandomMember(int itemId, Creature target) {
        try (ScratchBuffers.Snapshot<Player> availableMembers = ScratchBuffers.empty()) {
            for (Player member : members) {
                if (member.getInventory().validateCapacityByItemId(itemId)
                        && LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, target, member, true)) {
                    availableMembers.add(member);
                }
            }

            return !availableMembers.isEmpty()
                    ? availableMembers.get(ClockBridge.random().nextInt(availableMembers.size()))
                    : null;
        }
    }

    private Player getCheckedNextLooter(int itemId, Creature target) {
        for (int i = 0; i < members.size(); i++) {
            if (++itemLastLoot >= members.size()) {
                itemLastLoot = 0;
            }

            final Player member = members.get(itemLastLoot);
            if (member.getInventory().validateCapacityByItemId(itemId)
                    && LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, target, member, true)) {
                return member;
            }
        }

        return null;
    }

    private Player getActualLooter(Player player, int itemId, boolean spoil, Creature target) {
        Player looter = null;

        switch (distributionType) {
            case RANDOM: {
                if (!spoil) {
                    looter = getCheckedRandomMember(itemId, target);
                }
                break;
            }
            case RANDOM_INCLUDING_SPOIL: {
                looter = getCheckedRandomMember(itemId, target);
                break;
            }
            case BY_TURN: {
                if (!spoil) {
                    looter = getCheckedNextLooter(itemId, target);
                }
                break;
            }
            case BY_TURN_INCLUDING_SPOIL: {
                looter = getCheckedNextLooter(itemId, target);
                break;
            }
        }

        return looter != null ? looter : player;
    }

    public void broadcastToPartyMembersNewLeader() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(members)) {
            for (Player member : walk) {
                if (member != null) {
                    member.sendPacket(PartySmallWindowDeleteAll.STATIC_PACKET);
                    member.sendPacket(new PartySmallWindowAll(member, this));
                    member.broadcastUserInfo();
                }
            }
        }
    }

    public void broadcastToPartyMembers(Player player, ServerPacket packet) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(members)) {
            for (Player member : walk) {
                if ((member != null) && (member.getObjectId() != player.getObjectId())) {
                    member.sendPacket(packet);
                }
            }
        }
    }

    public void addPartyMember(Player player) {
        WorldPartition.checkOwner();
        if (members.contains(player)) {
            return;
        }

        if (changeRequestDistributionType != null) {
            finishLootRequest(false);
        }

        members.add(player);

        player.sendPacket(new PartySmallWindowAll(player, this));

        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(members)) {
            for (Player pMember : walk) {
                if ((pMember != null) && pMember.hasSummon()) {
                    player.sendPacket(new ExPartyPetWindowAdd(pMember.getSummon()));
                }
            }
        }

        SystemMessage msg = new SystemMessage(SystemMessageId.YOU_HAVE_JOINED_S1_S_PARTY);
        msg.addString(leader().getName());
        player.sendPacket(msg);

        msg = new SystemMessage(SystemMessageId.C1_HAS_JOINED_THE_PARTY);
        msg.addString(player.getName());
        broadcastToPartyMembers(player, msg);

        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(members)) {
            for (Player member : walk) {
                if (member != player) {
                    member.sendPacket(new PartySmallWindowAdd(player, this));
                }
            }
        }

        if (player.hasSummon()) {
            broadcastPacket(new ExPartyPetWindowAdd(player.getSummon()));
        }

        if (player.getLevel() > level) {
            level = player.getLevel();
        }

        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(members)) {
            for (Player member : walk) {
                if (member != null) {
                    member.updateEffectIcons(true);
                    final Summon summon = member.getSummon();
                    member.broadcastUserInfo();
                    if (summon != null) {
                        summon.updateEffectIcons();
                    }
                }
            }
        }

        if (dimensionalRift != null) {
            dimensionalRift.partyMemberInvited();
        }

        if (commandChannel != null) {
            player.sendPacket(ExOpenMPCC.STATIC_PACKET);
        }

        if (positionBroadcastTask == null) {
            positionBroadcastTask = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            () -> {
                                if (positionPacket == null) {
                                    positionPacket = new PartyMemberPosition(this);
                                } else {
                                    positionPacket.reuse(this);
                                }

                                broadcastPacket(positionPacket);
                            },
                            PARTY_POSITION_BROADCAST_INTERVAL.toMillis() / 2,
                            PARTY_POSITION_BROADCAST_INTERVAL.toMillis(),
                            TimeUnit.MILLISECONDS);
        }
    }

    public void removePartyMember(String name, PartyMessageType type) {
        WorldPartition.checkOwner();
        removePartyMember(getPlayerByName(name), type);
    }

    public void removePartyMember(Player player, PartyMessageType type) {
        WorldPartition.checkOwner();
        if (members.contains(player)) {
            final boolean isLeader = isLeader(player);
            if (!disbanding
                    && ((members.size() == 2)
                            || (isLeader
                                    && !PlayerConfig.ALT_LEAVE_PARTY_LEADER
                                    && (type != PartyMessageType.DISCONNECTED)))) {
                disbandParty();
                return;
            }

            members.remove(player);
            recalculatePartyLevel();

            if (player.isFestivalParticipant()) {
                SevenSignsFestival.getInstance().updateParticipants(player, this);
            }

            if (player.isInDuel()) {
                DuelManager.getInstance().onRemoveFromParty(player);
            }

            try {
                if (player.isChanneling() && (player.getSkillChannelizer().hasChannelized())) {
                    player.abortCast();
                } else if (player.isChannelized()) {
                    player.getSkillChannelized().abortChannelization();
                }
            } catch (Exception e) {
                log.warn("removePartyMember: ", e);
            }

            SystemMessage msg;
            if (type == PartyMessageType.EXPELLED) {
                player.sendPacket(SystemMessageId.YOU_HAVE_BEEN_EXPELLED_FROM_THE_PARTY);
                msg = new SystemMessage(SystemMessageId.C1_WAS_EXPELLED_FROM_THE_PARTY);
                msg.addString(player.getName());
                broadcastPacket(msg);
            } else if ((type == PartyMessageType.LEFT) || (type == PartyMessageType.DISCONNECTED)) {
                player.sendPacket(SystemMessageId.YOU_HAVE_WITHDRAWN_FROM_THE_PARTY);
                msg = new SystemMessage(SystemMessageId.C1_HAS_LEFT_THE_PARTY);
                msg.addString(player.getName());
                broadcastPacket(msg);
            }

            player.sendPacket(PartySmallWindowDeleteAll.STATIC_PACKET);
            player.setParty(null);
            broadcastPacket(new PartySmallWindowDelete(player));
            if (player.hasSummon()) {
                broadcastPacket(new ExPartyPetWindowDelete(player.getSummon()));
            }

            if (isInDimensionalRift()) {
                dimensionalRift.partyMemberExited(player);
            }

            if (isInCommandChannel()) {
                player.sendPacket(new ExCloseMPCC());
            }

            if (isLeader
                    && (members.size() > 1)
                    && (PlayerConfig.ALT_LEAVE_PARTY_LEADER || (type == PartyMessageType.DISCONNECTED))) {
                msg = new SystemMessage(SystemMessageId.C1_HAS_BECOME_THE_PARTY_LEADER);
                msg.addString(getLeader().getName());
                broadcastPacket(msg);
                broadcastToPartyMembersNewLeader();
                if (!disbanding) {
                    handOverChannelLeadership(player);
                }
            } else if (members.size() == 1) {
                if (commandChannel != null) {
                    final boolean ledChannel =
                            disbanding ? ledChannelWhenDisbanding : commandChannel.isLeader(leader());
                    if (ledChannel) {
                        commandChannel.disbandChannel();
                    } else {
                        commandChannel.removeParty(this);
                    }
                }

                final Player leader = getLeader();
                if (leader != null) {
                    leader.setParty(null);
                    if (leader.isInDuel()) {
                        DuelManager.getInstance().onRemoveFromParty(leader);
                    }
                }

                if (changeDistributionTypeRequestTask != null) {
                    changeDistributionTypeRequestTask.cancel(true);
                    changeDistributionTypeRequestTask = null;
                }

                if (positionBroadcastTask != null) {
                    positionBroadcastTask.cancel(false);
                    positionBroadcastTask = null;
                }

                members.clear();
            }
        }
    }

    public void disbandParty() {
        WorldPartition.checkOwner();
        disbanding = true;
        ledChannelWhenDisbanding = (commandChannel != null) && members.contains(commandChannel.getLeader());
        broadcastPacket(new SystemMessage(SystemMessageId.THE_PARTY_HAS_DISPERSED));
        try (ScratchBuffers.Snapshot<Player> walk = ScratchBuffers.snapshot(members)) {
            for (Player member : walk) {
                if (member != null) {
                    removePartyMember(member, PartyMessageType.NONE);
                }
            }
        }
    }

    public void changePartyLeader(String name) {
        WorldPartition.checkOwner();
        setLeader(getPlayerByName(name));
    }

    @Override
    public void setLeader(Player player) {
        WorldPartition.checkOwner();
        if ((player != null) && !player.isInDuel()) {
            if (members.contains(player)) {
                if (isLeader(player)) {
                    player.sendPacket(SystemMessageId.SLOW_DOWN_YOU_ARE_ALREADY_THE_PARTY_LEADER);
                } else {
                    final Player temp = getLeader();
                    final int p1 = members.indexOf(player);
                    members.set(0, player);
                    members.set(p1, temp);
                    SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_BECOME_THE_PARTY_LEADER);
                    msg.addString(getLeader().getName());
                    broadcastPacket(msg);
                    broadcastToPartyMembersNewLeader();
                    handOverChannelLeadership(temp);

                    if (player.isInPartyMatchRoom()) {
                        // The room number on the player outlives a room deleted without clearing it.
                        final PartyMatchRoom room =
                                PartyMatchRoomList.getInstance().getPlayerRoom(player);
                        if (room != null) {
                            room.changeLeader(player);
                        }
                    }
                }
            } else {
                player.sendPacket(
                        SystemMessageId.YOU_MAY_ONLY_TRANSFER_PARTY_LEADERSHIP_TO_ANOTHER_MEMBER_OF_THE_PARTY);
            }
        }
    }

    /** Passes the command channel to this party's new leader when the old one led the channel. */
    private void handOverChannelLeadership(Player oldLeader) {
        if ((commandChannel == null) || !commandChannel.isLeader(oldLeader)) {
            return;
        }

        final Player newLeader = leader();
        commandChannel.setLeader(newLeader);
        final SystemMessage msg =
                new SystemMessage(SystemMessageId.COMMAND_CHANNEL_AUTHORITY_HAS_BEEN_TRANSFERRED_TO_C1);
        msg.addString(newLeader.getName());
        commandChannel.broadcastPacket(msg);
    }

    public Player getPlayerByName(String name) {
        WorldPartition.checkOwner();
        for (Player member : members) {
            if (member.getName().equalsIgnoreCase(name)) {
                return member;
            }
        }

        return null;
    }

    public void distributeItem(Player player, Item item) {
        WorldPartition.checkOwner();
        if (item.getId() == Inventory.ADENA_ID) {
            distributeAdena(player, item.getCount(), player);
            ItemManager.destroyItem(ItemProcessType.LOOT, item, player, null);
            return;
        }

        final Player target = getActualLooter(player, item.getId(), false, player);
        target.addItem(ItemProcessType.LOOT, item, player, true);

        if (item.getCount() > 1) {
            final SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_OBTAINED_S3_S2);
            msg.addString(target.getName());
            msg.addItemName(item);
            msg.addLong(item.getCount());
            broadcastToPartyMembers(target, msg);
        } else {
            final SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_OBTAINED_S2);
            msg.addString(target.getName());
            msg.addItemName(item);
            broadcastToPartyMembers(target, msg);
        }
    }

    public void distributeItem(Player player, int itemId, long itemCount, boolean spoil, Attackable target) {
        WorldPartition.checkOwner();
        if (itemId == Inventory.ADENA_ID) {
            distributeAdena(player, itemCount, target);
            return;
        }

        final Player looter = getActualLooter(player, itemId, spoil, target);
        looter.addItem(spoil ? ItemProcessType.SWEEP : ItemProcessType.LOOT, itemId, itemCount, target, true);

        if (itemCount > 1) {
            final SystemMessage msg = spoil
                    ? new SystemMessage(SystemMessageId.C1_HAS_OBTAINED_S3_S2_BY_USING_SWEEPER)
                    : new SystemMessage(SystemMessageId.C1_HAS_OBTAINED_S3_S2);
            msg.addString(looter.getName());
            msg.addItemName(itemId);
            msg.addLong(itemCount);
            broadcastToPartyMembers(looter, msg);
        } else {
            final SystemMessage msg = spoil
                    ? new SystemMessage(SystemMessageId.C1_HAS_OBTAINED_S2_BY_USING_SWEEPER)
                    : new SystemMessage(SystemMessageId.C1_HAS_OBTAINED_S2);
            msg.addString(looter.getName());
            msg.addItemName(itemId);
            broadcastToPartyMembers(looter, msg);
        }
    }

    public void distributeItem(Player player, ItemHolder item, boolean spoil, Attackable target) {
        WorldPartition.checkOwner();
        distributeItem(player, item.getId(), item.getCount(), spoil, target);
    }

    public void distributeAdena(Player player, long adena, Creature target) {
        WorldPartition.checkOwner();
        if (adena <= 0) {
            return;
        }

        try (ScratchBuffers.Snapshot<Player> toReward = ScratchBuffers.empty()) {
            for (Player member : members) {
                if (LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, target, member, true)) {
                    toReward.add(member);
                }
            }

            if (toReward.isEmpty()) {
                return;
            }

            final int count = toReward.size();
            final long baseShare = adena / count;
            final long remainder = adena % count;
            // The remainder goes one adena each to the members from a random index on, wrapping around the list.
            final int randomIndex = remainder > 0 ? ClockBridge.random().nextInt(count) : 0;
            for (int i = 0; i < count; i++) {
                final long amount = baseShare + ((Math.floorMod(i - randomIndex, count) < remainder) ? 1 : 0);
                if (amount > 0) {
                    toReward.get(i).addAdena(ItemProcessType.LOOT, amount, player, true);
                }
            }
        }
    }

    public void distributeXpAndSp(
            double xpRewardValue,
            double spRewardValue,
            List<Player> rewardedMembers,
            int topLvl,
            long partyDmg,
            Attackable target,
            int targetMaxHp,
            boolean useVitalityRate) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> validMembers = ScratchBuffers.empty()) {
            collectValidMembers(rewardedMembers, topLvl, target, validMembers);
            distributeXpAndSp(
                    xpRewardValue,
                    spRewardValue,
                    rewardedMembers,
                    validMembers,
                    topLvl,
                    partyDmg,
                    target,
                    targetMaxHp,
                    useVitalityRate);
        }
    }

    private void distributeXpAndSp(
            double xpRewardValue,
            double spRewardValue,
            List<Player> rewardedMembers,
            List<Player> validMembers,
            int topLvl,
            long partyDmg,
            Attackable target,
            int targetMaxHp,
            boolean useVitalityRate) {
        double xpReward = xpRewardValue * getExpBonus(validMembers.size());
        double spReward = spRewardValue * getSpBonus(validMembers.size());
        int sqLevelSum = 0;
        int averagePartyMemberLevel = 0;
        for (Player member : validMembers) {
            sqLevelSum += member.getLevel() * member.getLevel();
            averagePartyMemberLevel += member.getLevel();
        }

        averagePartyMemberLevel /= Math.max(1, validMembers.size());

        final float vitalityPoints =
                (target.getVitalityPoints(averagePartyMemberLevel, partyDmg, targetMaxHp) * RatesConfig.RATE_PARTY_XP)
                        / validMembers.size();
        for (Player member : rewardedMembers) {
            if (member.isDead()) {
                continue;
            }

            if (validMembers.contains(member)) {
                final float penalty =
                        member.hasServitor() ? member.getSummon().asServitor().getExpMultiplier() : 1;
                final double sqLevel = member.getLevel() * member.getLevel();
                final double preCalculation = (sqLevel / sqLevelSum) * penalty;

                double addexp = Math.round(member.calcStat(Stat.EXPSP_RATE, xpReward * preCalculation, null, null));
                final double addsp = member.calcStat(Stat.EXPSP_RATE, spReward * preCalculation, null, null);
                addexp = calculateExpSpPartyCutoff(member.asPlayer(), topLvl, addexp, addsp, useVitalityRate);
                if (addexp > 0) {
                    member.updateVitalityPoints(vitalityPoints, true, false);
                    PcCafePointsManager.getInstance().givePcCafePoint(member, addexp);
                }
            } else {
                member.addExpAndSp(0, 0);
            }
        }
    }

    private double calculateExpSpPartyCutoff(
            Player player, int topLvl, double addExpValue, double addSpValue, boolean vit) {
        double addExp = addExpValue
                * ClassBalanceConfig.EXP_AMOUNT_MULTIPLIERS[
                        player.getPlayerClass().getId()];
        double addSp = addSpValue
                * ClassBalanceConfig.SP_AMOUNT_MULTIPLIERS[
                        player.getPlayerClass().getId()];

        if (player.hasPremiumStatus()) {
            addExp *= PremiumSystemConfig.PREMIUM_RATE_XP;
            addSp *= PremiumSystemConfig.PREMIUM_RATE_SP;
        }

        double xp = addExp;
        double sp = addSp;
        if (PlayerConfig.PARTY_XP_CUTOFF_METHOD == PartyExpType.HIGHFIVE) {
            int i = 0;
            final int levelDiff = topLvl - player.getLevel();
            for (int[] gap : PlayerConfig.PARTY_XP_CUTOFF_GAPS) {
                if ((levelDiff >= gap[0]) && (levelDiff <= gap[1])) {
                    xp = (addExp * PlayerConfig.PARTY_XP_CUTOFF_GAP_PERCENTS[i]) / 100;
                    sp = (addSp * PlayerConfig.PARTY_XP_CUTOFF_GAP_PERCENTS[i]) / 100;
                    player.addExpAndSp(xp, sp, vit);
                    break;
                }

                i++;
            }
        } else {
            player.addExpAndSp(addExp, addSp, vit);
        }

        return xp;
    }

    public void recalculatePartyLevel() {
        WorldPartition.checkOwner();
        int newLevel = 0;
        for (Player member : members) {
            if (member.getLevel() > newLevel) {
                newLevel = member.getLevel();
            }
        }

        level = newLevel;
    }

    private void collectValidMembers(List<Player> members, int topLvl, Attackable target, List<Player> validMembers) {
        switch (PlayerConfig.PARTY_XP_CUTOFF_METHOD) {
            case LEVEL: {
                for (Player member : members) {
                    if ((target.getInstanceId() == member.getInstanceId())
                            && ((topLvl - member.getLevel()) <= PlayerConfig.PARTY_XP_CUTOFF_LEVEL)) {
                        validMembers.add(member);
                    }
                }
                break;
            }
            case PERCENTAGE: {
                int sqLevelSum = 0;
                for (Player member : members) {
                    if (target.getInstanceId() == member.getInstanceId()) {
                        sqLevelSum += (member.getLevel() * member.getLevel());
                    }
                }

                for (Player member : members) {
                    final int sqLevel = member.getLevel() * member.getLevel();
                    if ((target.getInstanceId() == member.getInstanceId())
                            && ((sqLevel * 100) >= (sqLevelSum * PlayerConfig.PARTY_XP_CUTOFF_PERCENT))) {
                        validMembers.add(member);
                    }
                }
                break;
            }
            case AUTO: {
                int sqLevelSum = 0;
                for (Player member : members) {
                    sqLevelSum += (member.getLevel() * member.getLevel());
                }

                int i = members.size() - 1;
                if (i < 1) {
                    validMembers.addAll(members);
                    return;
                }

                if (i >= BONUS_EXP_SP.length) {
                    i = BONUS_EXP_SP.length - 1;
                }

                for (Player member : members) {
                    final int sqLevel = member.getLevel() * member.getLevel();
                    if ((target.getInstanceId() == member.getInstanceId())
                            && (sqLevel >= (sqLevelSum / (members.size() * members.size())))) {
                        validMembers.add(member);
                    }
                }
                break;
            }
            case HIGHFIVE: {
                for (Player member : members) {
                    if (target.getInstanceId() == member.getInstanceId()) {
                        validMembers.add(member);
                    }
                }
                break;
            }
            case NONE: {
                for (Player member : members) {
                    if (target.getInstanceId() == member.getInstanceId()) {
                        validMembers.add(member);
                    }
                }
                break;
            }
        }
    }

    private double getBaseExpSpBonus(int membersCount) {
        int i = membersCount - 1;
        if (i < 1) {
            return 1;
        }

        if (i >= BONUS_EXP_SP.length) {
            i = BONUS_EXP_SP.length - 1;
        }

        return BONUS_EXP_SP[i];
    }

    private double getExpBonus(int membersCount) {
        return (membersCount < 2)
                ? getBaseExpSpBonus(membersCount)
                : (getBaseExpSpBonus(membersCount) * RatesConfig.RATE_PARTY_XP);
    }

    private double getSpBonus(int membersCount) {
        return (membersCount < 2)
                ? getBaseExpSpBonus(membersCount)
                : (getBaseExpSpBonus(membersCount) * RatesConfig.RATE_PARTY_SP);
    }

    public boolean isInCommandChannel() {
        WorldPartition.checkOwner();
        return commandChannel != null;
    }

    public boolean isInDimensionalRift() {
        WorldPartition.checkOwner();
        return dimensionalRift != null;
    }

    public void requestLootChange(PartyDistributionType partyDistributionType) {
        WorldPartition.checkOwner();
        if (changeRequestDistributionType != null) {
            return;
        }

        changeRequestDistributionType = partyDistributionType;
        changeDistributionTypeAnswers = new IntOpenHashSet();
        changeDistributionTypeRequestTask = WorldPartition.loop()
                .schedule(
                        () -> finishLootRequest(false),
                        PARTY_DISTRIBUTION_TYPE_REQUEST_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS);
        final Player leader = leader();
        broadcastToPartyMembers(leader, new ExAskModifyPartyLooting(leader.getName(), partyDistributionType));

        final SystemMessage sm = new SystemMessage(SystemMessageId.REQUESTING_APPROVAL_FOR_CHANGING_PARTY_LOOT_TO_S1);
        sm.addSystemString(partyDistributionType.getSysStringId());
        leader.sendPacket(sm);
    }

    public void answerLootChangeRequest(Player member, boolean answer) {
        WorldPartition.checkOwner();
        if ((changeRequestDistributionType == null) || changeDistributionTypeAnswers.contains(member.getObjectId())) {
            return;
        }

        if (!answer) {
            finishLootRequest(false);
            return;
        }

        changeDistributionTypeAnswers.add(member.getObjectId());
        if (changeDistributionTypeAnswers.size() >= (members.size() - 1)) {
            finishLootRequest(true);
        }
    }

    protected void finishLootRequest(boolean success) {
        if (changeRequestDistributionType == null) {
            return;
        }

        if (changeDistributionTypeRequestTask != null) {
            changeDistributionTypeRequestTask.cancel(false);
            changeDistributionTypeRequestTask = null;
        }

        if (success) {
            broadcastPacket(new ExSetPartyLooting(1, changeRequestDistributionType));
            distributionType = changeRequestDistributionType;
            final SystemMessage sm = new SystemMessage(SystemMessageId.PARTY_LOOT_WAS_CHANGED_TO_S1);
            sm.addSystemString(changeRequestDistributionType.getSysStringId());
            broadcastPacket(sm);
        } else {
            broadcastPacket(new ExSetPartyLooting(0, distributionType));
            broadcastPacket(new SystemMessage(SystemMessageId.PARTY_LOOT_CHANGE_WAS_CANCELLED));
        }

        changeRequestDistributionType = null;
        changeDistributionTypeAnswers = null;
    }

    public Object getUCState() {
        WorldPartition.checkOwner();
        return ucState;
    }

    public void setUCState(Object uc) {
        WorldPartition.checkOwner();
        ucState = uc;
    }
}
