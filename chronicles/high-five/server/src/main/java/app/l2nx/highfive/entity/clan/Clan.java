/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.clan;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.chat.ChatService;
import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.communitybbs.BB.Forum;
import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.sql.CrestTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.ClanWarehouse;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.TerritoryWarManager.Territory;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clan.OnPlayerClanJoin;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clan.OnPlayerClanLeaderChange;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clan.OnPlayerClanLeft;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clan.OnPlayerClanLvlUp;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExSubPledgeSkillAdd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeReceiveSubPledgeCreated;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListAll;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListDeleteAll;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeSkillList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeSkillList.SubPledgeSkill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeSkillListAdd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.clan.model.ClanSkill;
import app.l2nx.highfive.repository.clan.model.RankPrivilege;
import app.l2nx.highfive.repository.clan.model.SubPledgeRow;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@SingleWriter
public class Clan {

    /** Clan leaved ally */
    public static final int PENALTY_TYPE_CLAN_LEAVED = 1;
    /** Clan was dismissed from ally */
    public static final int PENALTY_TYPE_CLAN_DISMISSED = 2;
    /** Leader clan dismiss clan from ally */
    public static final int PENALTY_TYPE_DISMISS_CLAN = 3;
    /** Leader clan dissolve ally */
    public static final int PENALTY_TYPE_DISSOLVE_ALLY = 4;

    /** Clan subunit type of Academy */
    public static final int SUBUNIT_ACADEMY = -1;
    /** Clan subunit type of Royal Guard A */
    public static final int SUBUNIT_ROYAL1 = 100;
    /** Clan subunit type of Royal Guard B */
    public static final int SUBUNIT_ROYAL2 = 200;
    /** Clan subunit type of Order of Knights A-1 */
    public static final int SUBUNIT_KNIGHT1 = 1001;
    /** Clan subunit type of Order of Knights A-2 */
    public static final int SUBUNIT_KNIGHT2 = 1002;
    /** Clan subunit type of Order of Knights B-1 */
    public static final int SUBUNIT_KNIGHT3 = 2001;
    /** Clan subunit type of Order of Knights B-2 */
    public static final int SUBUNIT_KNIGHT4 = 2002;

    private String name;

    private int id;

    private ClanMember leader;

    /** Members by object id, in the order they were restored or joined. */
    private final Int2ObjectLinkedOpenHashMap<ClanMember> members = new Int2ObjectLinkedOpenHashMap<>();

    private String allyName;

    private int allyId = 0;

    private int level;

    private int castleId;

    private int fortId;

    private int hideoutId;

    private int hiredGuards;

    private int crestId;

    private int crestLargeId;

    private int allyCrestId;

    private int auctionBiddedAt = 0;

    private long allyPenaltyExpiryTime;

    private int allyPenaltyType;

    private long charPenaltyExpiryTime;

    private long dissolvingExpiryTime;

    private int bloodAllianceCount;

    private int bloodOathCount;

    private final ItemContainer warehouse = new ClanWarehouse(this);

    /** Ids of the clans this clan declared war on, in declaration order. */
    private final IntLinkedOpenHashSet warList = new IntLinkedOpenHashSet();

    /** Ids of the clans that declared war on this clan, in declaration order. */
    private final IntLinkedOpenHashSet attackerList = new IntLinkedOpenHashSet();

    private Forum forum;

    private final Int2ObjectLinkedOpenHashMap<Skill> skills = new Int2ObjectLinkedOpenHashMap<>();

    private final Int2ObjectLinkedOpenHashMap<RankPrivs> privs = new Int2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectLinkedOpenHashMap<SubPledge> subPledges = new Int2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectLinkedOpenHashMap<Skill> subPledgeSkills = new Int2ObjectLinkedOpenHashMap<>();

    private int reputationScore = 0;

    private int rank = 0;

    private String notice;

    private boolean noticeEnabled = false;

    private static final int MAX_NOTICE_LENGTH = 8192;
    private static final int MIN_POWER_GRADE = 1;
    private static final int MAX_POWER_GRADE = 9;

    private int newLeaderId;

    private int siegeKills;
    private int siegeDeaths;

    public Clan(int clanId) {
        id = clanId;
        initializePrivs();
        restore();
        warehouse.restore();
    }

    public Clan(int clanId, String clanName) {
        id = clanId;
        name = clanName;
        initializePrivs();
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void setName(String name) {
        WorldPartition.checkOwner();
        this.name = name;
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public void setClanId(int clanId) {
        WorldPartition.checkOwner();
        id = clanId;
    }

    public ClanMember getLeader() {
        WorldPartition.checkOwner();
        return leader;
    }

    public String getAllyName() {
        WorldPartition.checkOwner();
        return allyName;
    }

    public void setAllyName(String allyName) {
        WorldPartition.checkOwner();
        this.allyName = allyName;
    }

    public int getAllyId() {
        WorldPartition.checkOwner();
        return allyId;
    }

    public void setAllyId(int allyId) {
        WorldPartition.checkOwner();
        this.allyId = allyId;
    }

    public int getLevel() {
        WorldPartition.checkOwner();
        return level;
    }

    public int getCastleId() {
        WorldPartition.checkOwner();
        return castleId;
    }

    public void setCastleId(int castleId) {
        WorldPartition.checkOwner();
        this.castleId = castleId;
    }

    public int getFortId() {
        WorldPartition.checkOwner();
        return fortId;
    }

    public void setFortId(int fortId) {
        WorldPartition.checkOwner();
        this.fortId = fortId;
    }

    public int getHideoutId() {
        WorldPartition.checkOwner();
        return hideoutId;
    }

    public void setHideoutId(int hideoutId) {
        WorldPartition.checkOwner();
        this.hideoutId = hideoutId;
    }

    public int getHiredGuards() {
        WorldPartition.checkOwner();
        return hiredGuards;
    }

    public int getCrestId() {
        WorldPartition.checkOwner();
        return crestId;
    }

    public void setCrestId(int crestId) {
        WorldPartition.checkOwner();
        this.crestId = crestId;
    }

    public int getCrestLargeId() {
        WorldPartition.checkOwner();
        return crestLargeId;
    }

    public void setCrestLargeId(int crestLargeId) {
        WorldPartition.checkOwner();
        this.crestLargeId = crestLargeId;
    }

    public int getAllyCrestId() {
        WorldPartition.checkOwner();
        return allyCrestId;
    }

    public void setAllyCrestId(int allyCrestId) {
        WorldPartition.checkOwner();
        this.allyCrestId = allyCrestId;
    }

    public int getAuctionBiddedAt() {
        WorldPartition.checkOwner();
        return auctionBiddedAt;
    }

    public long getAllyPenaltyExpiryTime() {
        WorldPartition.checkOwner();
        return allyPenaltyExpiryTime;
    }

    public int getAllyPenaltyType() {
        WorldPartition.checkOwner();
        return allyPenaltyType;
    }

    public long getCharPenaltyExpiryTime() {
        WorldPartition.checkOwner();
        return charPenaltyExpiryTime;
    }

    public void setCharPenaltyExpiryTime(long charPenaltyExpiryTime) {
        WorldPartition.checkOwner();
        this.charPenaltyExpiryTime = charPenaltyExpiryTime;
    }

    public long getDissolvingExpiryTime() {
        WorldPartition.checkOwner();
        return dissolvingExpiryTime;
    }

    public void setDissolvingExpiryTime(long dissolvingExpiryTime) {
        WorldPartition.checkOwner();
        this.dissolvingExpiryTime = dissolvingExpiryTime;
    }

    public int getBloodAllianceCount() {
        WorldPartition.checkOwner();
        return bloodAllianceCount;
    }

    public int getBloodOathCount() {
        WorldPartition.checkOwner();
        return bloodOathCount;
    }

    public ItemContainer getWarehouse() {
        WorldPartition.checkOwner();
        return warehouse;
    }

    /**
     * Adds the ids of the clans this clan declared war on to the target, in declaration order. A caller passes a
     * {@link ScratchBuffers#intEmpty()} lease.
     */
    public void collectWarList(IntCollection target) {
        WorldPartition.checkOwner();
        target.addAll(warList);
    }

    /**
     * Adds the ids of the clans that declared war on this clan to the target, in declaration order. A caller passes a
     * {@link ScratchBuffers#intEmpty()} lease.
     */
    public void collectAttackerList(IntCollection target) {
        WorldPartition.checkOwner();
        target.addAll(attackerList);
    }

    public int getReputationScore() {
        WorldPartition.checkOwner();
        return reputationScore;
    }

    public int getRank() {
        WorldPartition.checkOwner();
        return rank;
    }

    public boolean isNoticeEnabled() {
        WorldPartition.checkOwner();
        return noticeEnabled;
    }

    public int getNewLeaderId() {
        WorldPartition.checkOwner();
        return newLeaderId;
    }

    public int getLeaderId() {
        WorldPartition.checkOwner();
        return leader != null ? leader.getObjectId() : 0;
    }

    public void setLeader(ClanMember leader) {
        WorldPartition.checkOwner();
        this.leader = leader;
        members.put(leader.getObjectId(), leader);
    }

    public void setNewLeader(ClanMember member) {
        WorldPartition.checkOwner();
        final Player newLeader = member.getPlayer();
        final ClanMember exMember = leader;
        final Player exLeader = exMember.getPlayer();

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEADER_CHANGE)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(OnPlayerClanLeaderChange.builder()
                            .oldLeader(exMember)
                            .newLeader(member)
                            .clan(this)
                            .build());
        }

        if (exLeader != null) {
            if (exLeader.isFlying()) {
                exLeader.dismount();
            }

            if (getLevel() >= SiegeManager.getInstance().getSiegeClanMinLevel()) {
                SiegeManager.getInstance().removeSiegeSkills(exLeader);
            }

            exLeader.getClanPrivileges().disableAll();
            exLeader.broadcastUserInfo();
        } else {
            ClanTable.getInstance().updateMemberPrivileges(getLeaderId(), 0);
        }

        setLeader(member);
        if (newLeaderId != 0) {
            setNewLeaderId(0, true);
        }

        updateClanInDB();

        if (exLeader != null) {
            exLeader.setPledgeClass(ClanMember.calculatePledgeClass(exLeader));
            exLeader.broadcastUserInfo();
            exLeader.checkItemRestriction();
        }

        if (newLeader != null) {
            newLeader.setPledgeClass(ClanMember.calculatePledgeClass(newLeader));
            newLeader.getClanPrivileges().enableAll();

            if (getLevel() >= SiegeManager.getInstance().getSiegeClanMinLevel()) {
                SiegeManager.getInstance().addSiegeSkills(newLeader);
            }

            newLeader.broadcastUserInfo();
        } else {
            ClanTable.getInstance().updateMemberPrivileges(getLeaderId(), ClanPrivileges.getCompleteMask());
        }

        broadcastClanStatus();
        broadcastToOnlineMembers(new SystemMessage(SystemMessageId.CLAN_LORD_PRIVILEGES_HAVE_BEEN_TRANSFERRED_TO_C1)
                .addString(member.getName()));

        log.info("Leader of Clan: {} changed to: {} ex leader: {}", getName(), member.getName(), exMember.getName());
    }

    /**
     * @return the clan leader's name, or {@code null} for a clan restored without its leader.
     */
    public String getLeaderName() {
        WorldPartition.checkOwner();
        if (leader == null) {
            log.warn("Clan {} has no leader", this);
            return null;
        }

        return leader.getName();
    }

    private void addClanMember(ClanMember member) {
        members.put(member.getObjectId(), member);
    }

    public void addClanMember(Player player) {
        WorldPartition.checkOwner();
        final ClanMember member = new ClanMember(this, player);
        member.setPlayer(player);
        addClanMember(member);

        player.setClan(this);
        player.setPledgeClass(ClanMember.calculatePledgeClass(player));
        player.sendPacket(new PledgeShowMemberListUpdate(player));
        player.sendPacket(new PledgeSkillList(this));
        addSkillEffects(player);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_JOIN)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanJoin(member, this));
        }
    }

    public void updateClanMember(Player player) {
        WorldPartition.checkOwner();
        final ClanMember member = new ClanMember(player.getClan(), player);
        if (player.isClanLeader()) {
            setLeader(member);
        }

        addClanMember(member);
    }

    public ClanMember getClanMember(String name) {
        WorldPartition.checkOwner();
        for (ClanMember temp : members.values()) {
            if (temp.getName().equals(name)) {
                return temp;
            }
        }

        return null;
    }

    public ClanMember getClanMember(int objectId) {
        WorldPartition.checkOwner();
        return members.get(objectId);
    }

    public void removeClanMember(int objectId, long clanJoinExpiryTime) {
        WorldPartition.checkOwner();
        final ClanMember exMember = members.remove(objectId);
        if (exMember == null) {
            log.warn("Member Object ID: {} not found in clan while trying to remove", objectId);
            return;
        }

        final int leadssubpledge = getLeaderSubPledge(objectId);
        if (leadssubpledge != 0) {
            getSubPledge(leadssubpledge).setLeaderId(0);
            updateSubPledgeInDB(leadssubpledge);
        }

        if (exMember.getApprentice() != 0) {
            final ClanMember apprentice = getClanMember(exMember.getApprentice());
            if (apprentice != null) {
                if (apprentice.getPlayer() != null) {
                    apprentice.getPlayer().setSponsor(0);
                } else {
                    apprentice.setApprenticeAndSponsor(0, 0);
                }

                apprentice.saveApprenticeAndSponsor(0, 0);
            }
        }

        if (exMember.getSponsor() != 0) {
            final ClanMember sponsor = getClanMember(exMember.getSponsor());
            if (sponsor != null) {
                if (sponsor.getPlayer() != null) {
                    sponsor.getPlayer().setApprentice(0);
                } else {
                    sponsor.setApprenticeAndSponsor(0, 0);
                }

                sponsor.saveApprenticeAndSponsor(0, 0);
            }
        }

        exMember.saveApprenticeAndSponsor(0, 0);
        if (PlayerConfig.REMOVE_CASTLE_CIRCLETS) {
            CastleManager.getInstance().removeCirclet(exMember, getCastleId());
        }

        final Player player = exMember.getPlayer();
        if (player != null) {
            if (!player.isNoble()) {
                player.setTitle("");
            }

            player.setApprentice(0);
            player.setSponsor(0);

            if (player.isClanLeader()) {
                SiegeManager.getInstance().removeSiegeSkills(player);
                player.setClanCreateExpiryTime(
                        ClockBridge.wallMillis() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_CLAN_CREATE_DAYS));
            }

            removeSkillEffects(player);

            if (getCastleId() > 0) {
                final Castle castle = CastleManager.getInstance().getCastleByOwner(this);
                if (castle != null) {
                    castle.removeResidentialSkills(player);
                }
            }

            if (getFortId() > 0) {
                final Fort fort = FortManager.getInstance().getFortByOwner(this);
                if (fort != null) {
                    fort.removeResidentialSkills(player);
                }
            }

            player.sendSkillList();
            player.setClan(null);

            // Players leaving from clan academy have no penalty.
            if (exMember.getPledgeType() != -1) {
                player.setClanJoinExpiryTime(clanJoinExpiryTime);
            }

            player.setPledgeClass(ClanMember.calculatePledgeClass(player));
            player.broadcastUserInfo();

            player.sendPacket(PledgeShowMemberListDeleteAll.STATIC_PACKET);
        } else {
            removeMemberInDatabase(
                    exMember.getObjectId(),
                    clanJoinExpiryTime,
                    getLeaderId() == objectId
                            ? ClockBridge.wallMillis() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_CLAN_CREATE_DAYS)
                            : 0);
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEFT)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLeft(exMember, this));
        }
    }

    /** A read-only snapshot in member order; {@link #getClanMember(int)} and the counts copy nothing. */
    public List<ClanMember> getMembers() {
        WorldPartition.checkOwner();
        return members.isEmpty()
                ? ObjectLists.emptyList()
                : ObjectLists.unmodifiable(new ObjectArrayList<>(members.values()));
    }

    /**
     * Adds the members to the target in member order. A caller that walks the members passes a {@link ScratchBuffers}
     * lease, which spares the copy {@link #getMembers()} allocates.
     */
    public void collectMembers(List<? super ClanMember> target) {
        WorldPartition.checkOwner();
        target.addAll(members.values());
    }

    public int getMembersCount() {
        WorldPartition.checkOwner();
        return members.size();
    }

    public int getSubPledgeMembersCount(int subpl) {
        WorldPartition.checkOwner();
        int result = 0;
        for (ClanMember temp : members.values()) {
            if (temp.getPledgeType() == subpl) {
                result++;
            }
        }

        return result;
    }

    public int getMaxNrOfMembers(int pledgeType) {
        WorldPartition.checkOwner();
        int limit = 0;

        switch (pledgeType) {
            case 0: {
                switch (level) {
                    case 3: {
                        limit = 30;
                        break;
                    }
                    case 2: {
                        limit = 20;
                        break;
                    }
                    case 1: {
                        limit = 15;
                        break;
                    }
                    case 0: {
                        limit = 10;
                        break;
                    }
                    default: {
                        limit = 40;
                        break;
                    }
                }
                break;
            }
            case -1: {
                limit = 20;
                break;
            }
            case 100:
            case 200: {
                switch (level) {
                    case 11: {
                        limit = 30;
                        break;
                    }
                    default: {
                        limit = 20;
                        break;
                    }
                }
                break;
            }
            case 1001:
            case 1002:
            case 2001:
            case 2002: {
                switch (level) {
                    case 9:
                    case 10:
                    case 11: {
                        limit = 25;
                        break;
                    }
                    default: {
                        limit = 10;
                        break;
                    }
                }
                break;
            }
            default: {
                break;
            }
        }

        return limit;
    }

    public List<Player> getOnlineMembers(int exclude) {
        WorldPartition.checkOwner();
        final List<Player> onlineMembers = new ObjectArrayList<>();
        collectOnlineMembers(onlineMembers, exclude);
        return onlineMembers;
    }

    private void collectOnlineMembers(List<? super Player> into, int exclude) {
        for (ClanMember member : members.values()) {
            if (member.isOnline() && (member.getObjectId() != exclude)) {
                into.add(member.getPlayer());
            }
        }
    }

    public int getOnlineMembersCount() {
        WorldPartition.checkOwner();
        int count = 0;
        for (ClanMember member : members.values()) {
            if (member.isOnline()) {
                count++;
            }
        }

        return count;
    }

    private void setLevel(int level) {
        this.level = level;
        if ((this.level >= 2) && (this.forum == null) && GeneralConfig.ENABLE_COMMUNITY_BOARD) {
            final Forum root = ForumsBBSManager.getInstance().getForumByName("ClanRoot");
            if (root != null) {
                this.forum = root.getChildByOwner(getId());
                if (this.forum == null) {
                    this.forum = ForumsBBSManager.getInstance()
                            .createNewForum(root, Forum.CLAN, Forum.CLANMEMBERONLY, getId());
                }
            }
        }
    }

    public boolean isMember(int id) {
        WorldPartition.checkOwner();
        return (id != 0) && members.containsKey(id);
    }

    public void increaseBloodAllianceCount() {
        WorldPartition.checkOwner();
        bloodAllianceCount += SiegeManager.getInstance().getBloodAllianceReward();
        updateBloodAllianceCountInDB();
    }

    public void resetBloodAllianceCount() {
        WorldPartition.checkOwner();
        bloodAllianceCount = 0;
        updateBloodAllianceCountInDB();
    }

    public void updateBloodAllianceCountInDB() {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateBloodAllianceCount(id, bloodAllianceCount);
    }

    public void increaseBloodOathCount() {
        WorldPartition.checkOwner();
        bloodOathCount += FeatureConfig.FS_BLOOD_OATH_COUNT;
        updateBloodOathCountInDB();
    }

    public void resetBloodOathCount() {
        WorldPartition.checkOwner();
        bloodOathCount = 0;
        updateBloodOathCountInDB();
    }

    public void updateBloodOathCountInDB() {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateBloodOathCount(id, bloodOathCount);
    }

    public void updateClanInDB() {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateClan(this);
    }

    public void store() {
        WorldPartition.checkOwner();
        ClanTable.getInstance().insertClan(this);
    }

    private void removeMemberInDatabase(int playerId, long clanJoinExpiryTime, long clanCreateExpiryTime) {
        ClanTable.getInstance().removeMember(playerId, clanJoinExpiryTime, clanCreateExpiryTime);
    }

    private void restore() {
        final ClanTable table = ClanTable.getInstance();
        final Optional<ClanRow> stored = table.findClan(id);
        if (stored.isEmpty()) {
            log.warn("Clan {} is not stored.", id);
            return;
        }

        final ClanRow clanData = stored.get();
        setName(clanData.name());
        setLevel(clanData.level());
        setCastleId(table.findCastleIdOfOwner(id));
        bloodAllianceCount = clanData.bloodAllianceCount();
        bloodOathCount = clanData.bloodOathCount();
        setAllyId(clanData.allyId());
        setAllyName(clanData.allyName());
        setAllyPenaltyExpiryTime(epochMillisOrZero(clanData.allyPenaltyEndsAt()), clanData.allyPenaltyType());
        if (allyPenaltyExpiryTime < ClockBridge.wallMillis()) {
            setAllyPenaltyExpiryTime(0, 0);
        }

        setCharPenaltyExpiryTime(epochMillisOrZero(clanData.characterPenaltyEndsAt()));
        if ((charPenaltyExpiryTime + (PlayerConfig.ALT_CLAN_JOIN_DAYS * 86400000)) < ClockBridge.wallMillis()) {
            setCharPenaltyExpiryTime(0);
        }

        setDissolvingExpiryTime(epochMillisOrZero(clanData.dissolvingEndsAt()));

        setCrestId(clanData.crestId());
        setCrestLargeId(clanData.crestLargeId());
        setAllyCrestId(clanData.allyCrestId());

        setReputationScore(clanData.reputationScore());
        setAuctionBiddedAt(clanData.bidClanHallId(), false);
        setNewLeaderId(clanData.newLeaderId(), false);

        for (CharacterClanMember clanMember : table.findMembers(id)) {
            final ClanMember member = new ClanMember(this, clanMember);
            if (member.getObjectId() == clanData.leaderId()) {
                setLeader(member);
            } else {
                addClanMember(member);
            }
        }

        restoreSubPledges();
        restoreRankPrivs();
        restoreSkills();
        restoreNotice();
    }

    private void restoreNotice() {
        ClanTable.getInstance().findNotice(id).ifPresent(noticeData -> {
            noticeEnabled = noticeData.enabled();
            notice = noticeData.notice();
        });
    }

    private void storeNotice(String noticeValue, boolean enabled) {
        final String notice =
                StringUtils.isEmpty(noticeValue) ? null : StringUtils.left(noticeValue, MAX_NOTICE_LENGTH);
        ClanTable.getInstance().storeNotice(id, notice, enabled);
        this.notice = notice;
        noticeEnabled = enabled;
    }

    public void setNoticeEnabled(boolean enabled) {
        WorldPartition.checkOwner();
        storeNotice(getNotice(), enabled);
    }

    public void setNotice(String notice) {
        WorldPartition.checkOwner();
        storeNotice(notice, noticeEnabled);
    }

    /**
     * @return the notice without tags, or {@code null} when the clan has none or it carries a bypass action.
     */
    public String getNotice() {
        WorldPartition.checkOwner();
        if (notice == null) {
            return null;
        }

        final String text = notice.toLowerCase();
        if (text.contains("action") && text.contains("bypass")) {
            return null;
        }

        final String withoutTags = notice.replaceAll("<.*?>", "");
        return withoutTags.isEmpty() ? null : withoutTags;
    }

    private void restoreSkills() {
        for (ClanSkill row : ClanTable.getInstance().findSkills(id)) {
            final Skill skill = SkillData.getInstance().getSkill(row.skillId(), row.skillLevel());
            if (skill == null) {
                log.warn(
                        "Missing skill {} level {} for clan {}, skill skipped.", row.skillId(), row.skillLevel(), this);
                continue;
            }

            final int subType = row.pledgeType();
            if (subType == -2) {
                skills.put(skill.getId(), skill);
            } else if (subType == 0) {
                subPledgeSkills.put(skill.getId(), skill);
            } else {
                final SubPledge subunit = subPledges.get(subType);
                if (subunit != null) {
                    subunit.addNewSkill(skill);
                } else {
                    log.info("Missing subpledge {} for clan {}, skill skipped.", subType, this);
                }
            }
        }
    }

    public List<Skill> getAllSkills() {
        WorldPartition.checkOwner();
        return skills.isEmpty()
                ? ObjectLists.emptyList()
                : ObjectLists.unmodifiable(new ObjectArrayList<>(skills.values()));
    }

    /**
     * @return the clan skill of the main clan with the given id, or {@code null} if the clan has none.
     */
    public Skill getSkill(int skillId) {
        WorldPartition.checkOwner();
        return skills.get(skillId);
    }

    public Skill addSkill(Skill newSkill) {
        WorldPartition.checkOwner();
        return newSkill != null ? skills.put(newSkill.getId(), newSkill) : null;
    }

    public Skill addNewSkill(Skill newSkill) {
        WorldPartition.checkOwner();
        return addNewSkill(newSkill, -2);
    }

    /** Persists the skill and sends it to the online members entitled to it; returns the replaced skill, if any. */
    public Skill addNewSkill(Skill newSkill, int subType) {
        WorldPartition.checkOwner();
        Skill oldSkill = null;
        if (newSkill != null) {
            if (subType == -2) {
                oldSkill = skills.put(newSkill.getId(), newSkill);
            } else if (subType == 0) {
                oldSkill = subPledgeSkills.put(newSkill.getId(), newSkill);
            } else {
                final SubPledge subunit = subPledges.get(subType);
                if (subunit == null) {
                    log.warn("Subpledge {} does not exist for clan {}", subType, this);
                    return oldSkill;
                }

                oldSkill = subunit.subPledgeSkills.put(newSkill.getId(), newSkill);
            }

            ClanTable.getInstance().storeSkill(id, newSkill.getId(), newSkill.getLevel(), subType);

            final SystemMessage sm = new SystemMessage(SystemMessageId.THE_CLAN_SKILL_S1_HAS_BEEN_ADDED);
            sm.addSkillName(newSkill.getId());

            try (Snapshot<Player> online = ScratchBuffers.empty()) {
                collectOnlineMembers(online, 0);
                for (Player member : online) {
                    if (subType == -2) {
                        if (newSkill.getMinPledgeClass() <= member.getPledgeClass()) {
                            member.addSkill(newSkill, false); // Skill is not saved to player DB.
                            member.sendPacket(new PledgeSkillListAdd(newSkill.getId(), newSkill.getLevel()));
                            member.sendPacket(sm);
                            member.sendSkillList();
                        }
                    } else if (member.getPledgeType() == subType) {
                        member.addSkill(newSkill, false); // Skill is not saved to player DB.
                        member.sendPacket(new ExSubPledgeSkillAdd(subType, newSkill.getId(), newSkill.getLevel()));
                        member.sendPacket(sm);
                        member.sendSkillList();
                    }
                }
            }
        }

        return oldSkill;
    }

    public void addSkillEffects() {
        WorldPartition.checkOwner();
        try (Snapshot<Skill> clanSkills = ScratchBuffers.snapshot(skills.values());
                Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Skill skill : clanSkills) {
                for (Player member : online) {
                    if (skill.getMinPledgeClass() <= member.getPledgeClass()) {
                        member.addSkill(skill, false);
                    }
                }
            }
        }
    }

    public void addSkillEffects(Player player) {
        WorldPartition.checkOwner();
        if (player == null) {
            return;
        }

        final int playerSocialClass = player.getPledgeClass() + 1;
        try (Snapshot<Skill> clanSkills = ScratchBuffers.snapshot(skills.values())) {
            for (Skill skill : clanSkills) {
                final SkillLearn skillLearn =
                        SkillTreeData.getInstance().getPledgeSkill(skill.getId(), skill.getLevel());
                if ((skillLearn == null)
                        || (skillLearn.getSocialClass() == null)
                        || (playerSocialClass >= skillLearn.getSocialClass().ordinal())) {
                    player.addSkill(skill, false); // Skill is not saved to player DB.
                }
            }
        }

        if (player.getPledgeType() == 0) {
            try (Snapshot<Skill> unitSkills = ScratchBuffers.snapshot(subPledgeSkills.values())) {
                for (Skill skill : unitSkills) {
                    final SkillLearn skillLearn =
                            SkillTreeData.getInstance().getSubPledgeSkill(skill.getId(), skill.getLevel());
                    if ((skillLearn == null)
                            || (skillLearn.getSocialClass() == null)
                            || (playerSocialClass >= skillLearn.getSocialClass().ordinal())) {
                        player.addSkill(skill, false); // Skill is not saved to player DB.
                    }
                }
            }
        } else {
            final SubPledge subunit = subPledges.get(player.getPledgeType());
            if (subunit == null) {
                return;
            }

            try (Snapshot<Skill> unitSkills = ScratchBuffers.snapshot(subunit.subPledgeSkills.values())) {
                for (Skill skill : unitSkills) {
                    player.addSkill(skill, false); // Skill is not saved to player DB.
                }
            }
        }

        if (reputationScore < 0) {
            skillsStatus(player, true);
        }
    }

    public void removeSkillEffects(Player player) {
        WorldPartition.checkOwner();
        if (player == null) {
            return;
        }

        try (Snapshot<Skill> clanSkills = ScratchBuffers.snapshot(skills.values())) {
            for (Skill skill : clanSkills) {
                player.removeSkill(skill, false); // Skill is not saved to player DB.
            }
        }

        final Int2ObjectLinkedOpenHashMap<Skill> unitSkills = unitSkillsOf(player.getPledgeType());
        if (unitSkills == null) {
            return;
        }

        try (Snapshot<Skill> removed = ScratchBuffers.snapshot(unitSkills.values())) {
            for (Skill skill : removed) {
                player.removeSkill(skill, false); // Skill is not saved to player DB.
            }
        }
    }

    public void skillsStatus(Player player, boolean disable) {
        WorldPartition.checkOwner();
        if (player == null) {
            return;
        }

        try (Snapshot<Skill> clanSkills = ScratchBuffers.snapshot(skills.values())) {
            switchSkills(player, clanSkills, disable);
        }

        final Int2ObjectLinkedOpenHashMap<Skill> unitSkills = unitSkillsOf(player.getPledgeType());
        if (unitSkills != null) {
            try (Snapshot<Skill> switched = ScratchBuffers.snapshot(unitSkills.values())) {
                switchSkills(player, switched, disable);
            }
        }
    }

    /** The skills of the main clan's own unit for type 0, else of the sub-unit, or {@code null} for no sub-unit. */
    private Int2ObjectLinkedOpenHashMap<Skill> unitSkillsOf(int pledgeType) {
        if (pledgeType == 0) {
            return subPledgeSkills;
        }

        final SubPledge subunit = subPledges.get(pledgeType);
        return subunit == null ? null : subunit.subPledgeSkills;
    }

    private static void switchSkills(Player player, List<Skill> skills, boolean disable) {
        for (Skill skill : skills) {
            if (disable) {
                player.disableSkill(skill, -1);
            } else {
                player.enableSkill(skill);
            }
        }
    }

    public void broadcastToOnlineAllyMembers(ServerPacket packet) {
        WorldPartition.checkOwner();
        for (Clan clan : ClanTable.getInstance().getClanAllies(allyId)) {
            clan.broadcastToOnlineMembers(packet);
        }
    }

    public void broadcastToOnlineMembers(ServerPacket packet) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Player member : online) {
                member.sendPacket(packet);
            }
        }
    }

    public void broadcastChatToOnlineMembers(ChatMessage message, Player sender) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> receivers = ScratchBuffers.empty()) {
            collectOnlineMembersAccepting(receivers, sender);
            ChatService.getInstance().deliverAll(receivers, message);
        }
    }

    public void broadcastChatToOnlineAllyMembers(ChatMessage message, Player sender) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> receivers = ScratchBuffers.empty()) {
            for (Clan clan : ClanTable.getInstance().getClanAllies(allyId)) {
                clan.collectOnlineMembersAccepting(receivers, sender);
            }

            ChatService.getInstance().deliverAll(receivers, message);
        }
    }

    private void collectOnlineMembersAccepting(List<? super Player> into, Player sender) {
        for (ClanMember member : members.values()) {
            if (member.isOnline() && !BlockList.isBlocked(member.getPlayer(), sender)) {
                into.add(member.getPlayer());
            }
        }
    }

    public void broadcastToOtherOnlineMembers(ServerPacket packet, Player player) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Player member : online) {
                if (member != player) {
                    member.sendPacket(packet);
                }
            }
        }
    }

    @Override
    public String toString() {
        return name + "[" + id + "]";
    }

    public boolean isAtWarWith(int id) {
        WorldPartition.checkOwner();
        return warList.contains(id);
    }

    public boolean isAtWarWith(Clan clan) {
        WorldPartition.checkOwner();
        return warList.contains(clan.id);
    }

    public boolean isAtWarAttacker(int id) {
        WorldPartition.checkOwner();
        return attackerList.contains(id);
    }

    public void setEnemyClan(Clan clan) {
        WorldPartition.checkOwner();
        warList.add(clan.id);
    }

    public void setEnemyClan(int id) {
        WorldPartition.checkOwner();
        warList.add(id);
    }

    public void setAttackerClan(Clan clan) {
        WorldPartition.checkOwner();
        attackerList.add(clan.id);
    }

    public void setAttackerClan(int clan) {
        WorldPartition.checkOwner();
        attackerList.add(clan);
    }

    public void deleteEnemyClan(Clan clan) {
        WorldPartition.checkOwner();
        warList.remove(clan.id);
    }

    public void deleteEnemyClan(int clanId) {
        WorldPartition.checkOwner();
        warList.remove(clanId);
    }

    public void deleteAttackerClan(Clan clan) {
        WorldPartition.checkOwner();
        attackerList.remove(clan.id);
    }

    public void deleteAttackerClan(int clanId) {
        WorldPartition.checkOwner();
        attackerList.remove(clanId);
    }

    public void incrementHiredGuards() {
        WorldPartition.checkOwner();
        hiredGuards++;
    }

    public boolean isAtWar() {
        WorldPartition.checkOwner();
        return !warList.isEmpty();
    }

    public void broadcastClanStatus() {
        WorldPartition.checkOwner();
        try (Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Player member : online) {
                member.sendPacket(PledgeShowMemberListDeleteAll.STATIC_PACKET);
                member.sendPacket(new PledgeShowMemberListAll(this, member));
            }
        }
    }

    private void broadcastOnlineMembersInfo() {
        try (Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Player member : online) {
                member.broadcastUserInfo();
            }
        }
    }

    @SingleWriter
    public static class SubPledge {
        private final int id;

        private String name;

        private int leaderId;

        private final Int2ObjectLinkedOpenHashMap<Skill> subPledgeSkills = new Int2ObjectLinkedOpenHashMap<>();

        public SubPledge(int id, String name, int leaderId) {
            this.id = id;
            this.name = name;
            this.leaderId = leaderId;
        }

        public int getId() {
            WorldPartition.checkOwner();
            return id;
        }

        public String getName() {
            WorldPartition.checkOwner();
            return name;
        }

        public void setName(String name) {
            WorldPartition.checkOwner();
            this.name = name;
        }

        public int getLeaderId() {
            WorldPartition.checkOwner();
            return leaderId;
        }

        public void setLeaderId(int leaderId) {
            WorldPartition.checkOwner();
            this.leaderId = leaderId;
        }

        public Skill addNewSkill(Skill skill) {
            WorldPartition.checkOwner();
            return subPledgeSkills.put(skill.getId(), skill);
        }

        /** A read-only snapshot in the order the skills were learned. */
        public List<Skill> getSkills() {
            WorldPartition.checkOwner();
            return subPledgeSkills.isEmpty()
                    ? ObjectLists.emptyList()
                    : ObjectLists.unmodifiable(new ObjectArrayList<>(subPledgeSkills.values()));
        }

        public Skill getSkill(int id) {
            WorldPartition.checkOwner();
            return subPledgeSkills.get(id);
        }
    }

    @SingleWriter
    public static class RankPrivs {
        private final int rank;

        private final int party; // TODO: Find out what this stuff means and implement it.

        private final ClanPrivileges privs;

        public RankPrivs(int rank, int party, int privs) {
            this.rank = rank;
            this.party = party;
            this.privs = new ClanPrivileges(privs);
        }

        public RankPrivs(int rank, int party, ClanPrivileges rankPrivs) {
            this.rank = rank;
            this.party = party;
            privs = rankPrivs;
        }

        public int getRank() {
            WorldPartition.checkOwner();
            return rank;
        }

        public int getParty() {
            WorldPartition.checkOwner();
            return party;
        }

        public ClanPrivileges getPrivs() {
            WorldPartition.checkOwner();
            return privs;
        }

        public void setPrivs(int privs) {
            WorldPartition.checkOwner();
            this.privs.setMask(privs);
        }
    }

    private void restoreSubPledges() {
        for (SubPledgeRow row : ClanTable.getInstance().findSubPledges(id)) {
            subPledges.put(row.pledgeType(), new SubPledge(row.pledgeType(), row.name(), row.leaderId()));
        }
    }

    public SubPledge getSubPledge(int pledgeType) {
        WorldPartition.checkOwner();
        return subPledges.get(pledgeType);
    }

    public SubPledge getSubPledge(String pledgeName) {
        WorldPartition.checkOwner();
        for (SubPledge sp : subPledges.values()) {
            if (sp.name.equalsIgnoreCase(pledgeName)) {
                return sp;
            }
        }

        return null;
    }

    /** A read-only snapshot in the order the sub-units were created. */
    public List<SubPledge> getAllSubPledges() {
        WorldPartition.checkOwner();
        return subPledges.isEmpty()
                ? ObjectLists.emptyList()
                : ObjectLists.unmodifiable(new ObjectArrayList<>(subPledges.values()));
    }

    public SubPledge createSubPledge(Player player, int pledgeTypeValue, int leaderId, String subPledgeName) {
        WorldPartition.checkOwner();
        final int pledgeType = getAvailablePledgeTypes(pledgeTypeValue);
        if (pledgeType == 0) {
            if (pledgeTypeValue == SUBUNIT_ACADEMY) {
                player.sendPacket(SystemMessageId.YOUR_CLAN_HAS_ALREADY_ESTABLISHED_A_CLAN_ACADEMY);
            } else {
                player.sendMessage("You can't create any more sub-units of this type");
            }

            return null;
        }

        if (leader.getObjectId() == leaderId) {
            player.sendMessage("Leader is not correct");
            return null;
        }

        if ((pledgeType != SUBUNIT_ACADEMY)
                && (((reputationScore < FeatureConfig.ROYAL_GUARD_COST) && (pledgeType < SUBUNIT_KNIGHT1))
                        || ((reputationScore < FeatureConfig.KNIGHT_UNIT_COST) && (pledgeType > SUBUNIT_ROYAL2)))) {
            player.sendPacket(SystemMessageId.THE_CLAN_REPUTATION_SCORE_IS_TOO_LOW);
            return null;
        }

        // The academy has no captain; 0 means leaderless.
        final int subPledgeLeaderId = pledgeType != SUBUNIT_ACADEMY ? leaderId : 0;
        ClanTable.getInstance().insertSubPledge(id, pledgeType, subPledgeName, subPledgeLeaderId);
        final SubPledge subPledge = new SubPledge(pledgeType, subPledgeName, subPledgeLeaderId);
        subPledges.put(pledgeType, subPledge);

        if (pledgeType != SUBUNIT_ACADEMY) {
            if (pledgeType < SUBUNIT_KNIGHT1) {
                setReputationScore(reputationScore - FeatureConfig.ROYAL_GUARD_COST);
            } else {
                setReputationScore(reputationScore - FeatureConfig.KNIGHT_UNIT_COST);
                // TODO: clan lvl9 or more can reinforce knights cheaper if first knight unit already created, use
                // Config.KNIGHT_REINFORCE_COST
            }
        }

        broadcastToOnlineMembers(new PledgeShowInfoUpdate(leader.getClan()));
        broadcastToOnlineMembers(new PledgeReceiveSubPledgeCreated(subPledge, leader.getClan()));
        return subPledge;
    }

    public int getAvailablePledgeTypes(int pledgeType) {
        WorldPartition.checkOwner();
        if (subPledges.get(pledgeType) != null) {
            switch (pledgeType) {
                case SUBUNIT_ACADEMY: {
                    return 0;
                }
                case SUBUNIT_ROYAL1: {
                    return getAvailablePledgeTypes(SUBUNIT_ROYAL2);
                }
                case SUBUNIT_ROYAL2: {
                    return 0;
                }
                case SUBUNIT_KNIGHT1: {
                    return getAvailablePledgeTypes(SUBUNIT_KNIGHT2);
                }
                case SUBUNIT_KNIGHT2: {
                    return getAvailablePledgeTypes(SUBUNIT_KNIGHT3);
                }
                case SUBUNIT_KNIGHT3: {
                    return getAvailablePledgeTypes(SUBUNIT_KNIGHT4);
                }
                case SUBUNIT_KNIGHT4: {
                    return 0;
                }
            }
        }

        return pledgeType;
    }

    public void updateSubPledgeInDB(int pledgeType) {
        WorldPartition.checkOwner();
        final SubPledge subPledge = subPledges.get(pledgeType);
        ClanTable.getInstance().updateSubPledge(id, pledgeType, subPledge.name, subPledge.leaderId);
    }

    private void restoreRankPrivs() {
        for (RankPrivilege row : ClanTable.getInstance().findRankPrivileges(id)) {
            privs.get(row.powerGrade()).setPrivs(row.privilegeMask());
        }
    }

    private void initializePrivs() {
        for (int i = MIN_POWER_GRADE; i <= MAX_POWER_GRADE; i++) {
            privs.put(i, new RankPrivs(i, 0, new ClanPrivileges()));
        }
    }

    public ClanPrivileges getRankPrivs(int rank) {
        WorldPartition.checkOwner();
        final RankPrivs rankPrivs = privs.get(rank);
        return rankPrivs != null ? rankPrivs.privs : new ClanPrivileges();
    }

    /**
     * @param rank the power grade from the client; any value outside 1 to 9 is ignored
     */
    public void setRankPrivs(int rank, int privs) {
        WorldPartition.checkOwner();
        if ((rank < MIN_POWER_GRADE) || (rank > MAX_POWER_GRADE)) {
            return;
        }

        this.privs.get(rank).setPrivs(privs);
        ClanTable.getInstance().storeRankPrivileges(id, rank, privs);

        try (Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Player member : online) {
                if (member.getPowerGrade() == rank) {
                    member.getClanPrivileges().setMask(privs);
                    member.updateUserInfo();
                }
            }
        }

        broadcastClanStatus();
    }

    /**
     * Adds the nine power grades to the target, in grade order. A caller passes a {@link ScratchBuffers} lease.
     */
    public void collectRankPrivs(List<? super RankPrivs> target) {
        WorldPartition.checkOwner();
        target.addAll(privs.values());
    }

    public int getLeaderSubPledge(int leaderId) {
        WorldPartition.checkOwner();
        int id = 0;
        for (SubPledge sp : subPledges.values()) {
            if (sp.leaderId == 0) {
                continue;
            }

            if (sp.leaderId == leaderId) {
                id = sp.id;
            }
        }

        return id;
    }

    public void addReputationScore(int value) {
        WorldPartition.checkOwner();
        setReputationScore(reputationScore + value);
    }

    public void takeReputationScore(int value) {
        WorldPartition.checkOwner();
        setReputationScore(reputationScore - value);
    }

    private void setReputationScore(int value) {
        if ((reputationScore >= 0) && (value < 0)) {
            broadcastToOnlineMembers(
                    new SystemMessage(
                            SystemMessageId
                                    .SINCE_THE_CLAN_REPUTATION_SCORE_HAS_DROPPED_TO_0_OR_LOWER_YOUR_CLAN_SKILL_S_WILL_BE_DE_ACTIVATED));
            switchOnlineMemberSkills(true);
        } else if ((reputationScore < 0) && (value >= 0)) {
            broadcastToOnlineMembers(new SystemMessage(
                    SystemMessageId
                            .CLAN_SKILLS_WILL_NOW_BE_ACTIVATED_SINCE_THE_CLAN_S_REPUTATION_SCORE_IS_0_OR_HIGHER));
            switchOnlineMemberSkills(false);
        }

        reputationScore = value;
        if (reputationScore > 100000000) {
            reputationScore = 100000000;
        }

        if (reputationScore < -100000000) {
            reputationScore = -100000000;
        }

        broadcastToOnlineMembers(new PledgeShowInfoUpdate(this));
    }

    private void switchOnlineMemberSkills(boolean disable) {
        try (Snapshot<Player> online = ScratchBuffers.empty()) {
            collectOnlineMembers(online, 0);
            for (Player member : online) {
                skillsStatus(member, disable);
            }
        }
    }

    public void setRank(int rank) {
        WorldPartition.checkOwner();
        this.rank = rank;
    }

    public void setAuctionBiddedAt(int id, boolean storeInDb) {
        WorldPartition.checkOwner();
        auctionBiddedAt = id;
        if (storeInDb) {
            ClanTable.getInstance().updateBidClanHall(this.id, id);
        }
    }

    public boolean checkClanJoinCondition(Player player, Player target, int pledgeType) {
        WorldPartition.checkOwner();
        if (player == null) {
            return false;
        }

        if (!player.hasAccess(ClanAccess.INVITE_MEMBER)) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return false;
        }

        if (target == null) {
            player.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
            return false;
        }

        if (player.getObjectId() == target.getObjectId()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_ASK_YOURSELF_TO_APPLY_TO_A_CLAN);
            return false;
        }

        if (charPenaltyExpiryTime > ClockBridge.wallMillis()) {
            player.sendPacket(
                    SystemMessageId
                            .AFTER_A_CLAN_MEMBER_IS_DISMISSED_FROM_A_CLAN_THE_CLAN_MUST_WAIT_AT_LEAST_A_DAY_BEFORE_ACCEPTING_A_NEW_MEMBER);
            return false;
        }

        if (target.getClanId() != 0) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_ALREADY_A_MEMBER_OF_ANOTHER_CLAN);
            sm.addString(target.getName());
            player.sendPacket(sm);
            return false;
        }

        if (target.getClanJoinExpiryTime() > ClockBridge.wallMillis()) {
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .C1_CANNOT_JOIN_THE_CLAN_BECAUSE_ONE_DAY_HAS_NOT_YET_PASSED_SINCE_THEY_LEFT_ANOTHER_CLAN);
            sm.addString(target.getName());
            player.sendPacket(sm);
            return false;
        }

        if (((target.getLevel() > 40) || (target.getPlayerClass().level() >= 2)) && (pledgeType == -1)) {
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.S1_DOES_NOT_MEET_THE_REQUIREMENTS_TO_JOIN_A_CLAN_ACADEMY);
            sm.addString(target.getName());
            player.sendPacket(sm);
            player.sendPacket(
                    SystemMessageId
                            .TO_JOIN_A_CLAN_ACADEMY_CHARACTERS_MUST_BE_LEVEL_40_OR_BELOW_NOT_BELONG_ANOTHER_CLAN_AND_NOT_YET_COMPLETED_THEIR_2ND_CLASS_TRANSFER);
            return false;
        }

        if (getSubPledgeMembersCount(pledgeType) >= getMaxNrOfMembers(pledgeType)) {
            if (pledgeType == 0) {
                final SystemMessage sm = new SystemMessage(
                        SystemMessageId.S1_IS_FULL_AND_CANNOT_ACCEPT_ADDITIONAL_CLAN_MEMBERS_AT_THIS_TIME);
                sm.addString(name);
                player.sendPacket(sm);
            } else {
                player.sendPacket(
                        SystemMessageId
                                .THE_ACADEMY_ROYAL_GUARD_ORDER_OF_KNIGHTS_IS_FULL_AND_CANNOT_ACCEPT_NEW_MEMBERS_AT_THIS_TIME);
            }

            return false;
        }

        return true;
    }

    public boolean checkAllyJoinCondition(Player player, Player target) {
        WorldPartition.checkOwner();
        if (player == null) {
            return false;
        }

        if ((player.getAllyId() == 0) || !player.isClanLeader() || (player.getClanId() != player.getAllyId())) {
            player.sendPacket(SystemMessageId.THIS_FEATURE_IS_ONLY_AVAILABLE_TO_ALLIANCE_LEADERS);
            return false;
        }

        final Clan leaderClan = player.getClan();
        if ((leaderClan.getAllyPenaltyExpiryTime() > ClockBridge.wallMillis())
                && (leaderClan.getAllyPenaltyType() == PENALTY_TYPE_DISMISS_CLAN)) {
            player.sendPacket(SystemMessageId.YOU_MAY_NOT_ACCEPT_ANY_CLAN_WITHIN_A_DAY_AFTER_EXPELLING_ANOTHER_CLAN);
            return false;
        }

        if (target == null) {
            player.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
            return false;
        }

        if (player.getObjectId() == target.getObjectId()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_ASK_YOURSELF_TO_APPLY_TO_A_CLAN);
            return false;
        }

        if (target.getClan() == null) {
            player.sendPacket(SystemMessageId.THE_TARGET_MUST_BE_A_CLAN_MEMBER);
            return false;
        }

        if (!target.isClanLeader()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_NOT_A_CLAN_LEADER);
            sm.addString(target.getName());
            player.sendPacket(sm);
            return false;
        }

        final Clan targetClan = target.getClan();
        if (target.getAllyId() != 0) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CLAN_IS_ALREADY_A_MEMBER_OF_S2_ALLIANCE);
            sm.addString(targetClan.getName());
            sm.addString(targetClan.getAllyName());
            player.sendPacket(sm);
            return false;
        }

        if (targetClan.getAllyPenaltyExpiryTime() > ClockBridge.wallMillis()) {
            if (targetClan.getAllyPenaltyType() == PENALTY_TYPE_CLAN_LEAVED) {
                final SystemMessage sm = new SystemMessage(
                        SystemMessageId
                                .S1_CLAN_CANNOT_JOIN_THE_ALLIANCE_BECAUSE_ONE_DAY_HAS_NOT_YET_PASSED_SINCE_THEY_LEFT_ANOTHER_ALLIANCE);
                sm.addString(target.getClan().getName());
                sm.addString(target.getClan().getAllyName());
                player.sendPacket(sm);
                return false;
            }

            if (targetClan.getAllyPenaltyType() == PENALTY_TYPE_CLAN_DISMISSED) {
                player.sendPacket(
                        SystemMessageId
                                .A_CLAN_THAT_HAS_WITHDRAWN_OR_BEEN_EXPELLED_CANNOT_ENTER_INTO_AN_ALLIANCE_WITHIN_ONE_DAY_OF_WITHDRAWAL_OR_EXPULSION);
                return false;
            }
        }

        if (player.isInsideZone(ZoneId.SIEGE) && target.isInsideZone(ZoneId.SIEGE)) {
            player.sendPacket(SystemMessageId.THE_OPPOSING_CLAN_IS_PARTICIPATING_IN_A_SIEGE_BATTLE);
            return false;
        }

        if (leaderClan.isAtWarWith(targetClan.getId())) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_MAY_NOT_ALLY_WITH_A_CLAN_YOU_ARE_CURRENTLY_AT_WAR_WITH_THAT_WOULD_BE_DIABOLICAL_AND_TREACHEROUS);
            return false;
        }

        if (ClanTable.getInstance().getClanAllies(player.getAllyId()).size()
                >= PlayerConfig.ALT_MAX_NUM_OF_CLANS_IN_ALLY) {
            player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_LIMIT);
            return false;
        }

        return true;
    }

    public void setAllyPenaltyExpiryTime(long expiryTime, int penaltyType) {
        WorldPartition.checkOwner();
        allyPenaltyExpiryTime = expiryTime;
        allyPenaltyType = penaltyType;
    }

    public void createAlly(Player player, String allyName) {
        WorldPartition.checkOwner();
        if (player == null) {
            return;
        }

        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.ONLY_CLAN_LEADERS_MAY_CREATE_ALLIANCES);
            return;
        }

        if (allyId != 0) {
            player.sendPacket(SystemMessageId.YOU_ALREADY_BELONG_TO_ANOTHER_ALLIANCE);
            return;
        }

        if (level < 5) {
            player.sendPacket(SystemMessageId.TO_CREATE_AN_ALLIANCE_YOUR_CLAN_MUST_BE_LEVEL_5_OR_HIGHER);
            return;
        }

        if ((allyPenaltyExpiryTime > ClockBridge.wallMillis()) && (allyPenaltyType == PENALTY_TYPE_DISSOLVE_ALLY)) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_CREATE_A_NEW_ALLIANCE_WITHIN_1_DAY_OF_DISSOLUTION);
            return;
        }

        if (dissolvingExpiryTime > ClockBridge.wallMillis()) {
            player.sendPacket(
                    SystemMessageId.AS_YOU_ARE_CURRENTLY_SCHEDULE_FOR_CLAN_DISSOLUTION_NO_ALLIANCE_CAN_BE_CREATED);
            return;
        }

        if (!StringUtils.isAlphanumeric(allyName)) {
            player.sendPacket(SystemMessageId.INCORRECT_ALLIANCE_NAME_PLEASE_TRY_AGAIN);
            return;
        }

        if ((allyName.length() > 16) || (allyName.length() < 2)) {
            player.sendPacket(SystemMessageId.INCORRECT_LENGTH_FOR_AN_ALLIANCE_NAME);
            return;
        }

        if (ClanTable.getInstance().isAllyExists(allyName)) {
            player.sendPacket(SystemMessageId.THAT_ALLIANCE_NAME_ALREADY_EXISTS);
            return;
        }

        setAllyId(id);
        setAllyName(allyName.trim());
        setAllyPenaltyExpiryTime(0, 0);
        updateClanInDB();

        player.updateUserInfo();

        // TODO: Need correct message id
        player.sendMessage("Alliance " + allyName + " has been created.");
    }

    public void dissolveAlly(Player player) {
        WorldPartition.checkOwner();
        if (allyId == 0) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_CURRENTLY_ALLIED_WITH_ANY_CLANS);
            return;
        }

        if (!player.isClanLeader() || (id != allyId)) {
            player.sendPacket(SystemMessageId.THIS_FEATURE_IS_ONLY_AVAILABLE_TO_ALLIANCE_LEADERS);
            return;
        }

        if (player.isInsideZone(ZoneId.SIEGE)) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_CANNOT_DISSOLVE_AN_ALLIANCE_WHILE_AN_AFFILIATED_CLAN_IS_PARTICIPATING_IN_A_SIEGE_BATTLE);
            return;
        }

        broadcastToOnlineAllyMembers(new SystemMessage(SystemMessageId.THE_ALLIANCE_HAS_BEEN_DISSOLVED));

        final long currentTime = ClockBridge.wallMillis();
        for (Clan clan : ClanTable.getInstance().getClanAllies(getAllyId())) {
            if (clan.getId() != getId()) {
                clan.setAllyId(0);
                clan.setAllyName(null);
                clan.setAllyPenaltyExpiryTime(0, 0);
                clan.updateClanInDB();
            }
        }

        setAllyId(0);
        setAllyName(null);
        changeAllyCrest(0, false);
        setAllyPenaltyExpiryTime(
                currentTime + (PlayerConfig.ALT_CREATE_ALLY_DAYS_WHEN_DISSOLVED * 86400000),
                PENALTY_TYPE_DISSOLVE_ALLY);
        updateClanInDB();
    }

    public boolean levelUpClan(Player player) {
        WorldPartition.checkOwner();
        if (!player.isClanLeader()) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return false;
        }

        if (ClockBridge.wallMillis() < dissolvingExpiryTime) {
            player.sendPacket(
                    SystemMessageId
                            .AS_YOU_ARE_CURRENTLY_SCHEDULE_FOR_CLAN_DISSOLUTION_YOUR_CLAN_LEVEL_CANNOT_BE_INCREASED);
            return false;
        }

        boolean increaseClanLevel = false;

        switch (level) {
            case 0: {
                if ((player.getSp() >= 20000)
                        && (player.getAdena() >= 650000)
                        && player.reduceAdena(ItemProcessType.FEE, 650000, player.getTarget(), true)) {
                    player.setSp(player.getSp() - 20000);
                    final SystemMessage sp = new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1);
                    sp.addInt(20000);
                    player.sendPacket(sp);
                    increaseClanLevel = true;
                }
                break;
            }
            case 1: {
                if ((player.getSp() >= 100000)
                        && (player.getAdena() >= 2500000)
                        && player.reduceAdena(ItemProcessType.FEE, 2500000, player.getTarget(), true)) {
                    player.setSp(player.getSp() - 100000);
                    final SystemMessage sp = new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1);
                    sp.addInt(100000);
                    player.sendPacket(sp);
                    increaseClanLevel = true;
                }
                break;
            }
            case 2: {
                if ((player.getSp() >= 350000)
                        && (player.getInventory().getItemByItemId(1419) != null)
                        && player.destroyItemByItemId(ItemProcessType.FEE, 1419, 1, player.getTarget(), false)) {
                    player.setSp(player.getSp() - 350000);
                    final SystemMessage sp = new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1);
                    sp.addInt(350000);
                    player.sendPacket(sp);
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                    sm.addItemName(1419);
                    player.sendPacket(sm);
                    increaseClanLevel = true;
                }
                break;
            }
            case 3: {
                // itemId 3874 = Alliance Manifesto.
                if ((player.getSp() >= 1000000)
                        && (player.getInventory().getItemByItemId(3874) != null)
                        && player.destroyItemByItemId(ItemProcessType.FEE, 3874, 1, player.getTarget(), false)) {
                    player.setSp(player.getSp() - 1000000);
                    final SystemMessage sp = new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1);
                    sp.addInt(1000000);
                    player.sendPacket(sp);
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                    sm.addItemName(3874);
                    player.sendPacket(sm);
                    increaseClanLevel = true;
                }
                break;
            }
            case 4: {
                // itemId 3870 = Seal of Aspiration.
                if ((player.getSp() >= 2500000)
                        && (player.getInventory().getItemByItemId(3870) != null)
                        && player.destroyItemByItemId(ItemProcessType.FEE, 3870, 1, player.getTarget(), false)) {
                    player.setSp(player.getSp() - 2500000);
                    final SystemMessage sp = new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1);
                    sp.addInt(2500000);
                    player.sendPacket(sp);
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                    sm.addItemName(3870);
                    player.sendPacket(sm);
                    increaseClanLevel = true;
                }
                break;
            }
            case 5: {
                if ((reputationScore >= FeatureConfig.CLAN_LEVEL_6_COST)
                        && (members.size() >= FeatureConfig.CLAN_LEVEL_6_REQUIREMENT)) {
                    setReputationScore(reputationScore - FeatureConfig.CLAN_LEVEL_6_COST);
                    final SystemMessage cr = new SystemMessage(
                            SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE);
                    cr.addInt(FeatureConfig.CLAN_LEVEL_6_COST);
                    player.sendPacket(cr);
                    increaseClanLevel = true;
                }
                break;
            }
            case 6: {
                if ((reputationScore >= FeatureConfig.CLAN_LEVEL_7_COST)
                        && (members.size() >= FeatureConfig.CLAN_LEVEL_7_REQUIREMENT)) {
                    setReputationScore(reputationScore - FeatureConfig.CLAN_LEVEL_7_COST);
                    final SystemMessage cr = new SystemMessage(
                            SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE);
                    cr.addInt(FeatureConfig.CLAN_LEVEL_7_COST);
                    player.sendPacket(cr);
                    increaseClanLevel = true;
                }
                break;
            }
            case 7: {
                if ((reputationScore >= FeatureConfig.CLAN_LEVEL_8_COST)
                        && (members.size() >= FeatureConfig.CLAN_LEVEL_8_REQUIREMENT)) {
                    setReputationScore(reputationScore - FeatureConfig.CLAN_LEVEL_8_COST);
                    final SystemMessage cr = new SystemMessage(
                            SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE);
                    cr.addInt(FeatureConfig.CLAN_LEVEL_8_COST);
                    player.sendPacket(cr);
                    increaseClanLevel = true;
                }
                break;
            }
            case 8: {
                // itemId 9910 = Blood Oath.
                if ((reputationScore >= FeatureConfig.CLAN_LEVEL_9_COST)
                        && (player.getInventory().getItemByItemId(9910) != null)
                        && (members.size() >= FeatureConfig.CLAN_LEVEL_9_REQUIREMENT)
                        && player.destroyItemByItemId(ItemProcessType.FEE, 9910, 150, player.getTarget(), false)) {
                    setReputationScore(reputationScore - FeatureConfig.CLAN_LEVEL_9_COST);
                    final SystemMessage cr = new SystemMessage(
                            SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE);
                    cr.addInt(FeatureConfig.CLAN_LEVEL_9_COST);
                    player.sendPacket(cr);
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                    sm.addItemName(9910);
                    sm.addLong(150);
                    player.sendPacket(sm);
                    increaseClanLevel = true;
                }
                break;
            }
            case 9: {
                if ((reputationScore >= FeatureConfig.CLAN_LEVEL_10_COST)
                        && (player.getInventory().getItemByItemId(9911) != null)
                        && (members.size() >= FeatureConfig.CLAN_LEVEL_10_REQUIREMENT)) {
                    // itemId 9911 == Blood Alliance
                    if (player.destroyItemByItemId(ItemProcessType.FEE, 9911, 5, player.getTarget(), false)) {
                        setReputationScore(reputationScore - FeatureConfig.CLAN_LEVEL_10_COST);
                        final SystemMessage cr = new SystemMessage(
                                SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE);
                        cr.addInt(FeatureConfig.CLAN_LEVEL_10_COST);
                        player.sendPacket(cr);
                        final SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                        sm.addItemName(9911);
                        sm.addLong(5);
                        player.sendPacket(sm);
                        increaseClanLevel = true;
                    }
                }
                break;
            }
            case 10: {
                boolean hasTerritory = false;
                for (Territory terr : TerritoryWarManager.getInstance().getAllTerritories()) {
                    if (terr.getOwnerClan().getId() == getId()) {
                        hasTerritory = true;
                        break;
                    }
                }

                if (hasTerritory
                        && (reputationScore >= FeatureConfig.CLAN_LEVEL_11_COST)
                        && (members.size() >= FeatureConfig.CLAN_LEVEL_11_REQUIREMENT)) {
                    setReputationScore(reputationScore - FeatureConfig.CLAN_LEVEL_11_COST);
                    final SystemMessage cr = new SystemMessage(
                            SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE);
                    cr.addInt(FeatureConfig.CLAN_LEVEL_11_COST);
                    player.sendPacket(cr);
                    increaseClanLevel = true;
                }
                break;
            }
            default: {
                return false;
            }
        }

        if (!increaseClanLevel) {
            player.sendPacket(SystemMessageId.THE_CONDITIONS_NECESSARY_TO_INCREASE_THE_CLAN_S_LEVEL_HAVE_NOT_BEEN_MET);
            return false;
        }

        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.SP, (int) player.getSp());
        player.sendPacket(su);

        player.sendItemList(false);
        changeLevel(level + 1);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEVELUP)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLvlUp(player, this));
        }

        return true;
    }

    public void changeLevel(int level) {
        WorldPartition.checkOwner();
        ClanTable.getInstance().updateLevel(id, level);

        setLevel(level);
        setRank(ClanTable.getInstance().getClanRank(this));

        if (this.leader.isOnline()) {
            final Player leader = this.leader.getPlayer();
            if (level > 4) {
                SiegeManager.getInstance().addSiegeSkills(leader);
                leader.sendPacket(
                        SystemMessageId
                                .NOW_THAT_YOUR_CLAN_LEVEL_IS_ABOVE_LEVEL_5_IT_CAN_ACCUMULATE_CLAN_REPUTATION_POINTS);
            } else if (level < 5) {
                SiegeManager.getInstance().removeSiegeSkills(leader);
            }
        }

        broadcastToOnlineMembers(new SystemMessage(SystemMessageId.YOUR_CLAN_S_LEVEL_HAS_INCREASED));
        broadcastToOnlineMembers(new PledgeShowInfoUpdate(this));
    }

    /** A {@code crestId} of 0 removes the crest. */
    public void changeClanCrest(int crestId) {
        WorldPartition.checkOwner();
        if (this.crestId != 0) {
            CrestTable.getInstance().removeCrest(getCrestId());
        }

        setCrestId(crestId);

        ClanTable.getInstance().updateCrest(id, crestId);
        broadcastOnlineMembersInfo();
    }

    /** A {@code crestId} of 0 removes the crest. */
    public void changeAllyCrest(int crestId, boolean onlyThisClan) {
        WorldPartition.checkOwner();
        if (onlyThisClan) {
            ClanTable.getInstance().updateAllyCrest(id, crestId);
        } else {
            if (allyCrestId != 0) {
                CrestTable.getInstance().removeCrest(getAllyCrestId());
            }

            ClanTable.getInstance().updateAllyCrestOfAlliance(allyId, crestId);
        }

        if (onlyThisClan) {
            setAllyCrestId(crestId);
            broadcastOnlineMembersInfo();
        } else {
            for (Clan clan : ClanTable.getInstance().getClanAllies(allyId)) {
                clan.setAllyCrestId(crestId);
                clan.broadcastOnlineMembersInfo();
            }
        }
    }

    /** A {@code crestId} of 0 removes the crest. */
    public void changeLargeCrest(int crestId) {
        WorldPartition.checkOwner();
        if (crestLargeId != 0) {
            CrestTable.getInstance().removeCrest(getCrestLargeId());
        }

        setCrestLargeId(crestId);

        ClanTable.getInstance().updateLargeCrest(id, crestId);
        broadcastOnlineMembersInfo();
    }

    public boolean isLearnableSubSkill(int skillId, int skillLevel) {
        WorldPartition.checkOwner();
        Skill current = subPledgeSkills.get(skillId);

        if ((current != null) && ((current.getLevel() + 1) == skillLevel)) {
            return true;
        }

        if ((current == null) && (skillLevel == 1)) {
            return true;
        }

        for (SubPledge subunit : subPledges.values()) {
            // The academy cannot learn sub-unit skills.
            if (subunit.getId() == -1) {
                continue;
            }

            current = subunit.subPledgeSkills.get(skillId);

            if ((current != null) && ((current.getLevel() + 1) == skillLevel)) {
                return true;
            }

            if ((current == null) && (skillLevel == 1)) {
                return true;
            }
        }

        return false;
    }

    public boolean isLearnableSubPledgeSkill(Skill skill, int subType) {
        WorldPartition.checkOwner();
        if (subType == -1) {
            return false;
        }

        final int id = skill.getId();
        Skill current;
        if (subType == 0) {
            current = subPledgeSkills.get(id);
        } else {
            // The sub-unit type comes from the client, so a clan may have no such unit.
            final SubPledge subunit = subPledges.get(subType);
            if (subunit == null) {
                return false;
            }

            current = subunit.subPledgeSkills.get(id);
        }

        if ((current != null) && ((current.getLevel() + 1) == skill.getLevel())) {
            return true;
        }

        if ((current == null) && (skill.getLevel() == 1)) {
            return true;
        }

        return false;
    }

    public List<SubPledgeSkill> getAllSubSkills() {
        WorldPartition.checkOwner();
        final ObjectArrayList<SubPledgeSkill> list = new ObjectArrayList<>();
        for (Skill skill : subPledgeSkills.values()) {
            list.add(new SubPledgeSkill(0, skill.getId(), skill.getLevel()));
        }

        for (SubPledge subunit : subPledges.values()) {
            for (Skill skill : subunit.subPledgeSkills.values()) {
                list.add(new SubPledgeSkill(subunit.id, skill.getId(), skill.getLevel()));
            }
        }

        return ObjectLists.unmodifiable(list);
    }

    public void setNewLeaderId(int objectId, boolean storeInDb) {
        WorldPartition.checkOwner();
        newLeaderId = objectId;
        if (storeInDb) {
            updateClanInDB();
        }
    }

    public Player getNewLeader() {
        WorldPartition.checkOwner();
        return World.getPlayer(newLeaderId);
    }

    public String getNewLeaderName() {
        WorldPartition.checkOwner();
        return CharInfoTable.getInstance().getNameById(newLeaderId);
    }

    public int getSiegeKills() {
        WorldPartition.checkOwner();
        return siegeKills;
    }

    public int getSiegeDeaths() {
        WorldPartition.checkOwner();
        return siegeDeaths;
    }

    public int addSiegeKill() {
        WorldPartition.checkOwner();
        return ++siegeKills;
    }

    public int addSiegeDeath() {
        WorldPartition.checkOwner();
        return ++siegeDeaths;
    }

    public void clearSiegeKills() {
        WorldPartition.checkOwner();
        siegeKills = 0;
    }

    public void clearSiegeDeaths() {
        WorldPartition.checkOwner();
        siegeDeaths = 0;
    }
}
