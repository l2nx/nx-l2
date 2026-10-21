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
import app.l2nx.highfive.network.protocol.highfive.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExSubPledgeSkillAdd;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeReceiveSubPledgeCreated;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowMemberListAll;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowMemberListDeleteAll;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeSkillList;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeSkillList.SubPledgeSkill;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeSkillListAdd;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.clan.model.ClanSkill;
import app.l2nx.highfive.repository.clan.model.RankPrivilege;
import app.l2nx.highfive.repository.clan.model.SubPledgeRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class Clan {

    // Ally Penalty Types
    /** Clan leaved ally */
    public static final int PENALTY_TYPE_CLAN_LEAVED = 1;
    /** Clan was dismissed from ally */
    public static final int PENALTY_TYPE_CLAN_DISMISSED = 2;
    /** Leader clan dismiss clan from ally */
    public static final int PENALTY_TYPE_DISMISS_CLAN = 3;
    /** Leader clan dissolve ally */
    public static final int PENALTY_TYPE_DISSOLVE_ALLY = 4;

    // Sub-unit types
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

    @Getter
    @Setter
    private String name;

    @Getter
    private int id;

    @Getter
    private ClanMember leader;

    private final Map<Integer, ClanMember> members = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private String allyName;

    @Getter
    @Setter
    private int allyId = 0;

    @Getter
    private int level;

    @Getter
    @Setter
    private int castleId;

    @Getter
    @Setter
    private int fortId;

    @Getter
    @Setter
    private int hideoutId;

    @Getter
    private int hiredGuards;

    @Getter
    @Setter
    private int crestId;

    @Getter
    @Setter
    private int crestLargeId;

    @Getter
    @Setter
    private int allyCrestId;

    @Getter
    private int auctionBiddedAt = 0;

    @Getter
    private long allyPenaltyExpiryTime;

    @Getter
    private int allyPenaltyType;

    @Getter
    @Setter
    private long charPenaltyExpiryTime;

    @Getter
    @Setter
    private long dissolvingExpiryTime;

    @Getter
    private int bloodAllianceCount;

    @Getter
    private int bloodOathCount;

    @Getter
    private final ItemContainer warehouse = new ClanWarehouse(this);

    @Getter
    private final Set<Integer> warList = ConcurrentHashMap.newKeySet();

    @Getter
    private final Set<Integer> attackerList = ConcurrentHashMap.newKeySet();

    private Forum forum;

    /** Map(Integer, Skill) containing all skills of the Clan */
    @Getter
    private final Map<Integer, Skill> skills = new ConcurrentHashMap<>();

    private final Map<Integer, RankPrivs> privs = new ConcurrentHashMap<>();
    private final Map<Integer, SubPledge> subPledges = new ConcurrentHashMap<>();
    private final Map<Integer, Skill> subPledgeSkills = new ConcurrentHashMap<>();

    @Getter
    private int reputationScore = 0;

    @Getter
    private int rank = 0;

    private String notice;

    @Getter
    private boolean noticeEnabled = false;

    private static final int MAX_NOTICE_LENGTH = 8192;
    private static final int MIN_POWER_GRADE = 1;
    private static final int MAX_POWER_GRADE = 9;

    @Getter
    private int newLeaderId;

    private final AtomicInteger siegeKills = new AtomicInteger();
    private final AtomicInteger siegeDeaths = new AtomicInteger();

    /**
     * Called if a clan is referenced only by id. In this case all other data needs to be fetched from db
     * @param clanId A valid clan Id to create and restore
     */
    public Clan(int clanId) {
        id = clanId;
        initializePrivs();
        restore();
        warehouse.restore();
    }

    /**
     * Called only if a new clan is created
     * @param clanId A valid clan Id to create
     * @param clanName A valid clan name
     */
    public Clan(int clanId, String clanName) {
        id = clanId;
        name = clanName;
        initializePrivs();
    }

    /**
     * @param clanId The clanId to set.
     */
    public void setClanId(int clanId) {
        id = clanId;
    }

    /**
     * @return Returns the leaderId.
     */
    public int getLeaderId() {
        return leader != null ? leader.getObjectId() : 0;
    }

    /**
     * @param leader the leader to set.
     */
    public void setLeader(ClanMember leader) {
        this.leader = leader;
        members.put(leader.getObjectId(), leader);
    }

    public void setNewLeader(ClanMember member) {
        final Player newLeader = member.getPlayer();
        final ClanMember exMember = leader;
        final Player exLeader = exMember.getPlayer();

        // Notify to scripts.
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
     * @return the clan leader's name.
     */
    public String getLeaderName() {
        if (leader == null) {
            log.warn("{}: Clan {} without clan leader!", Clan.class.getName(), getName());
            return "";
        }

        return leader.getName();
    }

    /**
     * Adds a clan member to the clan.
     * @param member the clan member.
     */
    private void addClanMember(ClanMember member) {
        members.put(member.getObjectId(), member);
    }

    /**
     * Adds a clan member to the clan.<br>
     * Using a different constructor, to make it easier to read.
     * @param player the clan member
     */
    public void addClanMember(Player player) {
        final ClanMember member = new ClanMember(this, player);
        member.setPlayer(player);
        addClanMember(member);

        player.setClan(this);
        player.setPledgeClass(ClanMember.calculatePledgeClass(player));
        player.sendPacket(new PledgeShowMemberListUpdate(player));
        player.sendPacket(new PledgeSkillList(this));
        addSkillEffects(player);

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_JOIN)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanJoin(member, this));
        }
    }

    /**
     * Updates player status in clan.
     * @param player the player to be updated.
     */
    public void updateClanMember(Player player) {
        final ClanMember member = new ClanMember(player.getClan(), player);
        if (player.isClanLeader()) {
            setLeader(member);
        }

        addClanMember(member);
    }

    /**
     * @param name the name of the required clan member.
     * @return the clan member for a given name.
     */
    public ClanMember getClanMember(String name) {
        for (ClanMember temp : members.values()) {
            if (temp.getName().equals(name)) {
                return temp;
            }
        }

        return null;
    }

    /**
     * @param objectId the required clan member object Id.
     * @return the clan member for a given {@code objectId}.
     */
    public ClanMember getClanMember(int objectId) {
        return members.get(objectId);
    }

    /**
     * @param objectId the object Id of the member that will be removed.
     * @param clanJoinExpiryTime time penalty to join a clan.
     */
    public void removeClanMember(int objectId, long clanJoinExpiryTime) {
        final ClanMember exMember = members.remove(objectId);
        if (exMember == null) {
            log.warn("Member Object ID: {} not found in clan while trying to remove", objectId);
            return;
        }

        final int leadssubpledge = getLeaderSubPledge(objectId);
        if (leadssubpledge != 0) {
            // Sub-unit leader withdraws, position becomes vacant and leader
            // should appoint new via NPC.
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

            // Remove Clan skills from Player.
            removeSkillEffects(player);

            // Remove Residential skills.
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

            // Disable clan tab.
            player.sendPacket(PledgeShowMemberListDeleteAll.STATIC_PACKET);
        } else {
            removeMemberInDatabase(
                    exMember.getObjectId(),
                    clanJoinExpiryTime,
                    getLeaderId() == objectId
                            ? ClockBridge.wallMillis() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_CLAN_CREATE_DAYS)
                            : 0);
        }

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEFT)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLeft(exMember, this));
        }
    }

    public Collection<ClanMember> getMembers() {
        return members.values();
    }

    public int getMembersCount() {
        return members.size();
    }

    public int getSubPledgeMembersCount(int subpl) {
        int result = 0;
        for (ClanMember temp : members.values()) {
            if (temp.getPledgeType() == subpl) {
                result++;
            }
        }

        return result;
    }

    /**
     * @param pledgeType the Id of the pledge type.
     * @return the maximum number of members allowed for a given {@code pledgeType}.
     */
    public int getMaxNrOfMembers(int pledgeType) {
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

    /**
     * @param exclude the object Id to exclude from list.
     * @return all online members excluding the one with object id {code exclude}.
     */
    public List<Player> getOnlineMembers(int exclude) {
        final List<Player> onlineMembers = new ArrayList<>();
        for (ClanMember temp : members.values()) {
            if ((temp != null) && temp.isOnline() && (temp.getObjectId() != exclude)) {
                onlineMembers.add(temp.getPlayer());
            }
        }

        return onlineMembers;
    }

    /**
     * @return the online clan member count.
     */
    public int getOnlineMembersCount() {
        int count = 0;
        for (ClanMember temp : members.values()) {
            if ((temp == null) || !temp.isOnline()) {
                continue;
            }

            count++;
        }

        return count;
    }

    /**
     * Sets the clan level and updates the clan forum if it's needed.
     * @param level the clan level to be set.
     */
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

    /**
     * @param id the Id of the player to be verified.
     * @return {code true} if the player belongs to the clan.
     */
    public boolean isMember(int id) {
        return (id != 0) && members.containsKey(id);
    }

    /**
     * Increase Blood Alliance count by config predefined count and updates the database.
     */
    public void increaseBloodAllianceCount() {
        bloodAllianceCount += SiegeManager.getInstance().getBloodAllianceReward();
        updateBloodAllianceCountInDB();
    }

    /**
     * Reset the Blood Alliance count to zero and updates the database.
     */
    public void resetBloodAllianceCount() {
        bloodAllianceCount = 0;
        updateBloodAllianceCountInDB();
    }

    /**
     * Store current Bloood Alliances count in database.
     */
    public void updateBloodAllianceCountInDB() {
        ClanTable.getInstance().updateBloodAllianceCount(id, bloodAllianceCount);
    }

    /**
     * Increase Blood Oath count by config predefined count and updates the database.
     */
    public void increaseBloodOathCount() {
        bloodOathCount += FeatureConfig.FS_BLOOD_OATH_COUNT;
        updateBloodOathCountInDB();
    }

    /**
     * Reset the Blood Oath count to zero and updates the database.
     */
    public void resetBloodOathCount() {
        bloodOathCount = 0;
        updateBloodOathCountInDB();
    }

    /**
     * Store current Bloood Alliances count in database.
     */
    public void updateBloodOathCountInDB() {
        ClanTable.getInstance().updateBloodOathCount(id, bloodOathCount);
    }

    /**
     * Updates in database clan information:
     * <ul>
     * <li>Clan leader Id</li>
     * <li>Alliance Id</li>
     * <li>Alliance name</li>
     * <li>Clan's reputation</li>
     * <li>Alliance's penalty expiration time</li>
     * <li>Alliance's penalty type</li>
     * <li>Character's penalty expiration time</li>
     * <li>Dissolving expiration time</li>
     * <li>Clan's id</li>
     * </ul>
     */
    public void updateClanInDB() {
        ClanTable.getInstance().updateClan(this);
    }

    /**
     * Stores in database clan information:
     * <ul>
     * <li>Clan Id</li>
     * <li>Clan name</li>
     * <li>Clan level</li>
     * <li>Alliance Id</li>
     * <li>Alliance name</li>
     * <li>Clan leader Id</li>
     * <li>Clan crest Id</li>
     * <li>Clan large crest Id</li>
     * <li>Alliance crest Id</li>
     * </ul>
     */
    public void store() {
        ClanTable.getInstance().insertClan(this);
    }

    /**
     * Removes a clan member from this clan.
     * @param playerId the clan member object ID to be removed
     * @param clanJoinExpiryTime the time penalty for the player to join a new clan
     * @param clanCreateExpiryTime the time penalty for the player to create a new clan
     */
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
        if ((charPenaltyExpiryTime + (PlayerConfig.ALT_CLAN_JOIN_DAYS * 86400000))
                < ClockBridge.wallMillis()) // 24*60*60*1000 = 86400000
        {
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
        storeNotice(getNotice(), enabled);
    }

    public void setNotice(String notice) {
        storeNotice(notice, noticeEnabled);
    }

    public String getNotice() {
        if (notice == null) {
            return "";
        }

        // Bypass exploit check.
        final String text = notice.toLowerCase();
        if (text.contains("action") && text.contains("bypass")) {
            return "";
        }

        // Returns text without tags.
        return notice.replaceAll("<.*?>", "");
    }

    private void restoreSkills() {
        for (ClanSkill row : ClanTable.getInstance().findSkills(id)) {
            final Skill skill = SkillData.getInstance().getSkill(row.skillId(), row.skillLevel());
            if (skill == null) {
                log.warn(
                        "Missing skill {} level {} for clan {}, skill skipped.", row.skillId(), row.skillLevel(), this);
                continue;
            }

            // Add the Skill object to the Clan skills.
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

    /**
     * @return all the clan skills.
     */
    public Collection<Skill> getAllSkills() {
        return skills.values();
    }

    /**
     * Used to add a skill to skill list of this Pledge
     * @param newSkill
     * @return
     */
    public Skill addSkill(Skill newSkill) {
        return newSkill != null ? skills.put(newSkill.getId(), newSkill) : null;
    }

    public Skill addNewSkill(Skill newSkill) {
        return addNewSkill(newSkill, -2);
    }

    /**
     * Used to add a new skill to the list, send a packet to all online clan members, update their stats and store it in db
     * @param newSkill
     * @param subType
     * @return
     */
    public Skill addNewSkill(Skill newSkill, int subType) {
        Skill oldSkill = null;
        if (newSkill != null) {
            if (subType == -2) {
                oldSkill = skills.put(newSkill.getId(), newSkill);
            } else if (subType == 0) {
                oldSkill = subPledgeSkills.put(newSkill.getId(), newSkill);
            } else {
                final SubPledge subunit = getSubPledge(subType);
                if (subunit == null) {
                    log.warn("Subpledge {} does not exist for clan {}", subType, this);
                    return oldSkill;
                }

                oldSkill = subunit.addNewSkill(newSkill);
            }

            ClanTable.getInstance().storeSkill(id, newSkill.getId(), newSkill.getLevel(), subType);

            final SystemMessage sm = new SystemMessage(SystemMessageId.THE_CLAN_SKILL_S1_HAS_BEEN_ADDED);
            sm.addSkillName(newSkill.getId());

            for (ClanMember temp : members.values()) {
                if ((temp != null) && (temp.getPlayer() != null) && temp.isOnline()) {
                    if (subType == -2) {
                        if (newSkill.getMinPledgeClass() <= temp.getPlayer().getPledgeClass()) {
                            temp.getPlayer().addSkill(newSkill, false); // Skill is not saved to player DB.
                            temp.getPlayer().sendPacket(new PledgeSkillListAdd(newSkill.getId(), newSkill.getLevel()));
                            temp.getPlayer().sendPacket(sm);
                            temp.getPlayer().sendSkillList();
                        }
                    } else if (temp.getPledgeType() == subType) {
                        temp.getPlayer().addSkill(newSkill, false); // Skill is not saved to player DB.
                        temp.getPlayer()
                                .sendPacket(new ExSubPledgeSkillAdd(subType, newSkill.getId(), newSkill.getLevel()));
                        temp.getPlayer().sendPacket(sm);
                        temp.getPlayer().sendSkillList();
                    }
                }
            }
        }

        return oldSkill;
    }

    public void addSkillEffects() {
        for (Skill skill : skills.values()) {
            for (ClanMember temp : members.values()) {
                try {
                    if ((temp != null)
                            && temp.isOnline()
                            && (skill.getMinPledgeClass() <= temp.getPlayer().getPledgeClass())) {
                        temp.getPlayer().addSkill(skill, false);
                    }
                } catch (NullPointerException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    public void addSkillEffects(Player player) {
        if (player == null) {
            return;
        }

        final int playerSocialClass = player.getPledgeClass() + 1;
        for (Skill skill : skills.values()) {
            final SkillLearn skillLearn = SkillTreeData.getInstance().getPledgeSkill(skill.getId(), skill.getLevel());
            if ((skillLearn == null)
                    || (skillLearn.getSocialClass() == null)
                    || (playerSocialClass >= skillLearn.getSocialClass().ordinal())) {
                player.addSkill(skill, false); // Skill is not saved to player DB.
            }
        }

        if (player.getPledgeType() == 0) {
            for (Skill skill : subPledgeSkills.values()) {
                final SkillLearn skillLearn =
                        SkillTreeData.getInstance().getSubPledgeSkill(skill.getId(), skill.getLevel());
                if ((skillLearn == null)
                        || (skillLearn.getSocialClass() == null)
                        || (playerSocialClass >= skillLearn.getSocialClass().ordinal())) {
                    player.addSkill(skill, false); // Skill is not saved to player DB.
                }
            }
        } else {
            final SubPledge subunit = getSubPledge(player.getPledgeType());
            if (subunit == null) {
                return;
            }

            for (Skill skill : subunit.getSkills()) {
                player.addSkill(skill, false); // Skill is not saved to player DB.
            }
        }

        if (reputationScore < 0) {
            skillsStatus(player, true);
        }
    }

    public void removeSkillEffects(Player player) {
        if (player == null) {
            return;
        }

        for (Skill skill : skills.values()) {
            player.removeSkill(skill, false); // Skill is not saved to player DB.
        }

        if (player.getPledgeType() == 0) {
            for (Skill skill : subPledgeSkills.values()) {
                player.removeSkill(skill, false); // Skill is not saved to player DB.
            }
        } else {
            final SubPledge subunit = getSubPledge(player.getPledgeType());
            if (subunit == null) {
                return;
            }

            for (Skill skill : subunit.getSkills()) {
                player.removeSkill(skill, false); // Skill is not saved to player DB.
            }
        }
    }

    public void skillsStatus(Player player, boolean disable) {
        if (player == null) {
            return;
        }

        for (Skill skill : skills.values()) {
            if (disable) {
                player.disableSkill(skill, -1);
            } else {
                player.enableSkill(skill);
            }
        }

        if (player.getPledgeType() == 0) {
            for (Skill skill : subPledgeSkills.values()) {
                if (disable) {
                    player.disableSkill(skill, -1);
                } else {
                    player.enableSkill(skill);
                }
            }
        } else {
            final SubPledge subunit = getSubPledge(player.getPledgeType());
            if (subunit != null) {
                for (Skill skill : subunit.getSkills()) {
                    if (disable) {
                        player.disableSkill(skill, -1);
                    } else {
                        player.enableSkill(skill);
                    }
                }
            }
        }
    }

    public void broadcastToOnlineAllyMembers(ServerPacket packet) {
        for (Clan clan : ClanTable.getInstance().getClanAllies(getAllyId())) {
            clan.broadcastToOnlineMembers(packet);
        }
    }

    public void broadcastToOnlineMembers(ServerPacket packet) {
        for (ClanMember member : members.values()) {
            if ((member != null) && member.isOnline()) {
                member.getPlayer().sendPacket(packet);
            }
        }
    }

    public void broadcastCSToOnlineMembers(CreatureSay packet, Player broadcaster) {
        for (ClanMember member : members.values()) {
            if ((member != null) && member.isOnline() && !BlockList.isBlocked(member.getPlayer(), broadcaster)) {
                member.getPlayer().sendPacket(packet);
            }
        }
    }

    public void broadcastToOtherOnlineMembers(ServerPacket packet, Player player) {
        for (ClanMember member : members.values()) {
            if ((member != null) && member.isOnline() && (member.getPlayer() != player)) {
                member.getPlayer().sendPacket(packet);
            }
        }
    }

    @Override
    public String toString() {
        return name + "[" + id + "]";
    }

    public boolean isAtWarWith(int id) {
        return warList.contains(id);
    }

    public boolean isAtWarWith(Clan clan) {
        return warList.contains(clan.getId());
    }

    public boolean isAtWarAttacker(int id) {
        return attackerList.contains(id);
    }

    public void setEnemyClan(Clan clan) {
        warList.add(clan.getId());
    }

    public void setEnemyClan(int id) {
        warList.add(id);
    }

    public void setAttackerClan(Clan clan) {
        attackerList.add(clan.getId());
    }

    public void setAttackerClan(int clan) {
        attackerList.add(clan);
    }

    public void deleteEnemyClan(Clan clan) {
        warList.remove(clan.getId());
    }

    public void deleteAttackerClan(Clan clan) {
        attackerList.remove(clan.getId());
    }

    public void incrementHiredGuards() {
        hiredGuards++;
    }

    public boolean isAtWar() {
        return (warList != null) && !warList.isEmpty();
    }

    public void broadcastClanStatus() {
        for (Player member : getOnlineMembers(0)) {
            member.sendPacket(PledgeShowMemberListDeleteAll.STATIC_PACKET);
            member.sendPacket(new PledgeShowMemberListAll(this, member));
        }
    }

    public static class SubPledge {
        @Getter
        private final int id;

        @Getter
        @Setter
        private String name;

        @Getter
        @Setter
        private int leaderId;

        private final Map<Integer, Skill> subPledgeSkills = new HashMap<>();

        public SubPledge(int id, String name, int leaderId) {
            this.id = id;
            this.name = name;
            this.leaderId = leaderId;
        }

        public Skill addNewSkill(Skill skill) {
            return subPledgeSkills.put(skill.getId(), skill);
        }

        public Collection<Skill> getSkills() {
            return subPledgeSkills.values();
        }

        public Skill getSkill(int id) {
            return subPledgeSkills.get(id);
        }
    }

    public static class RankPrivs {
        @Getter
        private final int rank;

        @Getter
        private final int party; // TODO: Find out what this stuff means and implement it.

        @Getter
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

        public void setPrivs(int privs) {
            this.privs.setMask(privs);
        }
    }

    private void restoreSubPledges() {
        for (SubPledgeRow row : ClanTable.getInstance().findSubPledges(id)) {
            subPledges.put(row.pledgeType(), new SubPledge(row.pledgeType(), row.name(), row.leaderId()));
        }
    }

    /**
     * used to retrieve subPledge by type
     * @param pledgeType
     * @return
     */
    public SubPledge getSubPledge(int pledgeType) {
        return subPledges.get(pledgeType);
    }

    /**
     * Used to retrieve subPledge by type
     * @param pledgeName
     * @return
     */
    public SubPledge getSubPledge(String pledgeName) {
        for (SubPledge sp : subPledges.values()) {
            if (sp.getName().equalsIgnoreCase(pledgeName)) {
                return sp;
            }
        }

        return null;
    }

    /**
     * Used to retrieve all subPledges
     * @return
     */
    public Collection<SubPledge> getAllSubPledges() {
        return subPledges.values();
    }

    public SubPledge createSubPledge(Player player, int pledgeTypeValue, int leaderId, String subPledgeName) {
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

        // Royal Guard 5000 points per each
        // Order of Knights 10000 points per each
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
            // Royal Guard 5000 points per each
            // Order of Knights 10000 points per each
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
        if (subPledges.get(pledgeType) != null) {
            // LOGGER.warning("found sub-unit with id: "+pledgeType);
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
        final SubPledge subPledge = getSubPledge(pledgeType);
        ClanTable.getInstance().updateSubPledge(id, pledgeType, subPledge.getName(), subPledge.getLeaderId());
    }

    private void restoreRankPrivs() {
        for (RankPrivilege row : ClanTable.getInstance().findRankPrivileges(id)) {
            privs.get(row.powerGrade()).setPrivs(row.privilegeMask());
        }
    }

    public void initializePrivs() {
        for (int i = MIN_POWER_GRADE; i <= MAX_POWER_GRADE; i++) {
            privs.put(i, new RankPrivs(i, 0, new ClanPrivileges()));
        }
    }

    public ClanPrivileges getRankPrivs(int rank) {
        return privs.get(rank) != null ? privs.get(rank).getPrivs() : new ClanPrivileges();
    }

    /**
     * @param rank the power grade from the client; any value outside 1 to 9 is ignored
     */
    public void setRankPrivs(int rank, int privs) {
        if ((rank < MIN_POWER_GRADE) || (rank > MAX_POWER_GRADE)) {
            return;
        }

        this.privs.get(rank).setPrivs(privs);
        ClanTable.getInstance().storeRankPrivileges(id, rank, privs);

        for (ClanMember cm : getMembers()) {
            if (cm.isOnline() && (cm.getPowerGrade() == rank) && (cm.getPlayer() != null)) {
                cm.getPlayer().getClanPrivileges().setMask(privs);
                cm.getPlayer().updateUserInfo();
            }
        }

        broadcastClanStatus();
    }

    /**
     * @return all RankPrivs.
     */
    public Collection<RankPrivs> getAllRankPrivs() {
        return privs.values();
    }

    public int getLeaderSubPledge(int leaderId) {
        int id = 0;
        for (SubPledge sp : subPledges.values()) {
            if (sp.getLeaderId() == 0) {
                continue;
            }

            if (sp.getLeaderId() == leaderId) {
                id = sp.getId();
            }
        }

        return id;
    }

    public synchronized void addReputationScore(int value) {
        setReputationScore(reputationScore + value);
    }

    public synchronized void takeReputationScore(int value) {
        setReputationScore(reputationScore - value);
    }

    private void setReputationScore(int value) {
        if ((reputationScore >= 0) && (value < 0)) {
            broadcastToOnlineMembers(
                    new SystemMessage(
                            SystemMessageId
                                    .SINCE_THE_CLAN_REPUTATION_SCORE_HAS_DROPPED_TO_0_OR_LOWER_YOUR_CLAN_SKILL_S_WILL_BE_DE_ACTIVATED));
            for (ClanMember member : members.values()) {
                if (member.isOnline() && (member.getPlayer() != null)) {
                    skillsStatus(member.getPlayer(), true);
                }
            }
        } else if ((reputationScore < 0) && (value >= 0)) {
            broadcastToOnlineMembers(new SystemMessage(
                    SystemMessageId
                            .CLAN_SKILLS_WILL_NOW_BE_ACTIVATED_SINCE_THE_CLAN_S_REPUTATION_SCORE_IS_0_OR_HIGHER));
            for (ClanMember member : members.values()) {
                if (member.isOnline() && (member.getPlayer() != null)) {
                    skillsStatus(member.getPlayer(), false);
                }
            }
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

    public synchronized void setRank(int rank) {
        this.rank = rank;
    }

    public void setAuctionBiddedAt(int id, boolean storeInDb) {
        auctionBiddedAt = id;
        if (storeInDb) {
            ClanTable.getInstance().updateBidClanHall(this.id, id);
        }
    }

    /**
     * @param player the clan inviting player.
     * @param target the invited player.
     * @param pledgeType the pledge type to join.
     * @return {core true} if player and target meet various conditions to join a clan.
     */
    public boolean checkClanJoinCondition(Player player, Player target, int pledgeType) {
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

    /**
     * @param player the clan inviting player.
     * @param target the invited player.
     * @return {core true} if player and target meet various conditions to join a clan.
     */
    public boolean checkAllyJoinCondition(Player player, Player target) {
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
        allyPenaltyExpiryTime = expiryTime;
        allyPenaltyType = penaltyType;
    }

    public void createAlly(Player player, String allyName) {
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
                PENALTY_TYPE_DISSOLVE_ALLY); // 24*60*60*1000 = 86400000
        updateClanInDB();
    }

    public boolean levelUpClan(Player player) {
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
                // Upgrade to 1
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
                // Upgrade to 2
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
                // Upgrade to 3
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
                // Upgrade to 4 (itemId 3874 = Alliance Manifesto).
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
                // Upgrade to 5 (itemId 3870 = Seal of Aspiration).
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
                // Upgrade to 6
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
                // Upgrade to 7
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
                // Upgrade to 8
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
                // Upgrade to 9 (itemId 9910 = Blood Oath).
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
                // Upgrade to 10
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
                // Upgrade to 11
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

        // the player should know that he has less sp now :p
        final StatusUpdate su = new StatusUpdate(player);
        su.addAttribute(StatusUpdate.SP, (int) player.getSp());
        player.sendPacket(su);

        player.sendItemList(false);
        changeLevel(level + 1);

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEVELUP)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLvlUp(player, this));
        }

        return true;
    }

    public void changeLevel(int level) {
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

        // Notify all the members about it.
        broadcastToOnlineMembers(new SystemMessage(SystemMessageId.YOUR_CLAN_S_LEVEL_HAS_INCREASED));
        broadcastToOnlineMembers(new PledgeShowInfoUpdate(this));
    }

    /**
     * Change the clan crest. If crest id is 0, crest is removed. New crest id is saved to database.
     * @param crestId if 0, crest is removed, else new crest id is set and saved to database
     */
    public void changeClanCrest(int crestId) {
        if (this.crestId != 0) {
            CrestTable.getInstance().removeCrest(getCrestId());
        }

        setCrestId(crestId);

        ClanTable.getInstance().updateCrest(id, crestId);

        for (Player member : getOnlineMembers(0)) {
            member.broadcastUserInfo();
        }
    }

    /**
     * Change the ally crest. If crest id is 0, crest is removed. New crest id is saved to database.
     * @param crestId if 0, crest is removed, else new crest id is set and saved to database
     * @param onlyThisClan
     */
    public void changeAllyCrest(int crestId, boolean onlyThisClan) {
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
            for (Player member : getOnlineMembers(0)) {
                member.broadcastUserInfo();
            }
        } else {
            for (Clan clan : ClanTable.getInstance().getClanAllies(getAllyId())) {
                clan.setAllyCrestId(crestId);
                for (Player member : clan.getOnlineMembers(0)) {
                    member.broadcastUserInfo();
                }
            }
        }
    }

    /**
     * Change the large crest. If crest id is 0, crest is removed. New crest id is saved to database.
     * @param crestId if 0, crest is removed, else new crest id is set and saved to database
     */
    public void changeLargeCrest(int crestId) {
        if (crestLargeId != 0) {
            CrestTable.getInstance().removeCrest(getCrestLargeId());
        }

        setCrestLargeId(crestId);

        ClanTable.getInstance().updateLargeCrest(id, crestId);

        for (Player member : getOnlineMembers(0)) {
            member.broadcastUserInfo();
        }
    }

    /**
     * Check if this clan can learn the skill for the given skill ID, level.
     * @param skillId
     * @param skillLevel
     * @return {@code true} if skill can be learned.
     */
    public boolean isLearnableSubSkill(int skillId, int skillLevel) {
        Skill current = subPledgeSkills.get(skillId);

        // is next level?
        if ((current != null) && ((current.getLevel() + 1) == skillLevel)) {
            return true;
        }

        // is first level?
        if ((current == null) && (skillLevel == 1)) {
            return true;
        }

        // other sub-pledges
        for (SubPledge subunit : subPledges.values()) {
            // disable academy
            if (subunit.getId() == -1) {
                continue;
            }

            current = subunit.getSkill(skillId);

            // is next level?
            if ((current != null) && ((current.getLevel() + 1) == skillLevel)) {
                return true;
            }

            // is first level?
            if ((current == null) && (skillLevel == 1)) {
                return true;
            }
        }

        return false;
    }

    public boolean isLearnableSubPledgeSkill(Skill skill, int subType) {
        // academy
        if (subType == -1) {
            return false;
        }

        final int id = skill.getId();
        Skill current;
        if (subType == 0) {
            current = subPledgeSkills.get(id);
        } else {
            current = subPledges.get(subType).getSkill(id);
        }

        // is next level?
        if ((current != null) && ((current.getLevel() + 1) == skill.getLevel())) {
            return true;
        }

        // is first level?
        if ((current == null) && (skill.getLevel() == 1)) {
            return true;
        }

        return false;
    }

    public Collection<SubPledgeSkill> getAllSubSkills() {
        final List<SubPledgeSkill> list = new ArrayList<>();
        for (Skill skill : subPledgeSkills.values()) {
            list.add(new SubPledgeSkill(0, skill.getId(), skill.getLevel()));
        }

        for (SubPledge subunit : subPledges.values()) {
            for (Skill skill : subunit.getSkills()) {
                list.add(new SubPledgeSkill(subunit.getId(), skill.getId(), skill.getLevel()));
            }
        }

        return list;
    }

    public void setNewLeaderId(int objectId, boolean storeInDb) {
        newLeaderId = objectId;
        if (storeInDb) {
            updateClanInDB();
        }
    }

    public Player getNewLeader() {
        return World.getPlayer(newLeaderId);
    }

    public String getNewLeaderName() {
        return CharInfoTable.getInstance().getNameById(newLeaderId);
    }

    public int getSiegeKills() {
        return siegeKills.get();
    }

    public int getSiegeDeaths() {
        return siegeDeaths.get();
    }

    public int addSiegeKill() {
        return siegeKills.incrementAndGet();
    }

    public int addSiegeDeath() {
        return siegeDeaths.incrementAndGet();
    }

    public void clearSiegeKills() {
        siegeKills.set(0);
    }

    public void clearSiegeDeaths() {
        siegeDeaths.set(0);
    }
}
