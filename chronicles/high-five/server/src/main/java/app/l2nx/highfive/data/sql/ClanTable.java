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
package app.l2nx.highfive.data.sql;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.entity.clan.ClanPrivileges;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.residences.ClanHallAuction;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clan.OnPlayerClanCreate;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clan.OnPlayerClanDestroy;
import app.l2nx.highfive.mechanics.events.holders.clan.OnClanWarFinish;
import app.l2nx.highfive.mechanics.events.holders.clan.OnClanWarStart;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.FortSiege;
import app.l2nx.highfive.mechanics.siege.Siege;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowMemberListAll;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.PledgeShowMemberListUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.clan.model.ClanNotice;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.clan.model.ClanSkill;
import app.l2nx.highfive.repository.clan.model.ClanUpdate;
import app.l2nx.highfive.repository.clan.model.ClanWar;
import app.l2nx.highfive.repository.clan.model.RankPrivilege;
import app.l2nx.highfive.repository.clan.model.SubPledgeRow;
import app.l2nx.highfive.repository.clan.model.WarOpponent;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class ClanTable {
    private static volatile ClanTable instance;

    private final Map<Integer, Clan> clans = new ConcurrentHashMap<>();

    private final ClanRepository clanRepository;
    private final ClanNoticeRepository noticeRepository;
    private final ClanRankPrivilegeRepository rankPrivilegeRepository;
    private final ClanSkillRepository skillRepository;
    private final ClanSubPledgeRepository subPledgeRepository;
    private final ClanWarRepository warRepository;
    private final CastleStateRepository castleStateRepository;
    private final CharacterRepository characterRepository;
    private final TransactionTemplate transactions;

    public ClanTable(
            ClanRepository clanRepository,
            ClanNoticeRepository noticeRepository,
            ClanRankPrivilegeRepository rankPrivilegeRepository,
            ClanSkillRepository skillRepository,
            ClanSubPledgeRepository subPledgeRepository,
            ClanWarRepository warRepository,
            CastleStateRepository castleStateRepository,
            CharacterRepository characterRepository,
            TransactionTemplate transactions) {
        this.clanRepository = clanRepository;
        this.noticeRepository = noticeRepository;
        this.rankPrivilegeRepository = rankPrivilegeRepository;
        this.skillRepository = skillRepository;
        this.subPledgeRepository = subPledgeRepository;
        this.warRepository = warRepository;
        this.castleStateRepository = castleStateRepository;
        this.characterRepository = characterRepository;
        this.transactions = transactions;
        instance = this;
    }

    public void load() {
        // Forums has to be loaded before clan data, because of last forum id used should have also memo included.
        if (GeneralConfig.ENABLE_COMMUNITY_BOARD) {
            ForumsBBSManager.getInstance().initRoot();
        }

        clanRepository.clearAllianceLeftovers();
        final List<Integer> cids = clanRepository.findAllClanIds();
        for (int cid : cids) {
            final Clan clan = new Clan(cid);
            clans.put(cid, clan);
            if (clan.getDissolvingExpiryTime() != 0) {
                scheduleRemoveClan(clan.getId());
            }
        }

        log.info("Restored {} clans from the database.", cids.size());
        allianceCheck();
        restoreClanWars();

        ThreadPool.scheduleAtFixedRate(this::updateClanRanks, 1000, 1200000); // 20 minutes.
    }

    /**
     * Gets the clans.
     * @return the clans
     */
    public Collection<Clan> getClans() {
        return clans.values();
    }

    /**
     * Gets the clan count.
     * @return the clan count
     */
    public int getClanCount() {
        return clans.size();
    }

    /**
     * @param clanId
     * @return
     */
    public Clan getClan(int clanId) {
        return clans.get(clanId);
    }

    public Clan getClanByName(String clanName) {
        for (Clan clan : clans.values()) {
            if (clan.getName().equalsIgnoreCase(clanName)) {
                return clan;
            }
        }

        return null;
    }

    /**
     * Creates a new clan and store clan info to database
     * @param player
     * @param clanName
     * @return NULL if clan with same name already exists
     */
    public Clan createClan(Player player, String clanName) {
        if (player == null) {
            return null;
        }

        if (10 > player.getLevel()) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_THE_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN);
            return null;
        }

        if (0 != player.getClanId()) {
            player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_CREATE_A_CLAN);
            return null;
        }

        if (ClockBridge.wallMillis() < player.getClanCreateExpiryTime()) {
            player.sendPacket(SystemMessageId.YOU_MUST_WAIT_10_DAYS_BEFORE_CREATING_A_NEW_CLAN);
            return null;
        }

        if (!StringUtils.isAlphanumeric(clanName) || (2 > clanName.length())) {
            player.sendPacket(SystemMessageId.CLAN_NAME_IS_INVALID);
            return null;
        }

        if (16 < clanName.length()) {
            player.sendPacket(SystemMessageId.CLAN_NAME_S_LENGTH_IS_INCORRECT);
            return null;
        }

        if (null != getClanByName(clanName)) {
            // Clan name is already taken.
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_ALREADY_EXISTS);
            sm.addString(clanName);
            player.sendPacket(sm);
            return null;
        }

        final Clan clan = new Clan(IdManager.getInstance().getNextId(), clanName);
        final ClanMember leader = new ClanMember(clan, player);
        clan.setLeader(leader);
        leader.setPlayer(player);
        clan.store();
        player.setClan(clan);
        player.setPledgeClass(ClanMember.calculatePledgeClass(player));

        final ClanPrivileges privileges = new ClanPrivileges();
        privileges.enableAll();
        player.setClanPrivileges(privileges);

        clans.put(clan.getId(), clan);

        // should be update packet only
        player.sendPacket(new PledgeShowInfoUpdate(clan));
        player.sendPacket(new PledgeShowMemberListAll(clan, player));
        player.updateUserInfo();
        player.sendPacket(new PledgeShowMemberListUpdate(player));
        player.sendPacket(SystemMessageId.YOUR_CLAN_HAS_BEEN_CREATED);

        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_CREATE)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanCreate(player, clan));
        }

        return clan;
    }

    public synchronized void destroyClan(int clanId) {
        final Clan clan = getClan(clanId);
        if (clan == null) {
            return;
        }

        clan.broadcastToOnlineMembers(new SystemMessage(SystemMessageId.CLAN_HAS_DISPERSED));
        final ClanHallAuction auction = ClanHallAuctionManager.getInstance().getAuction(clan.getAuctionBiddedAt());
        if (auction != null) {
            auction.cancelBid(clan.getId());
        }

        releaseResidences(clan);
        // After the release: releasing a castle mid-siege runs midVictory, which ends the siege on a lone attacker with
        // no defender, so the owner's entry must still be present.
        for (Siege siege : SiegeManager.getInstance().getSieges()) {
            siege.removeDissolvedClan(clanId);
        }

        for (FortSiege siege : FortSiegeManager.getInstance().getSieges()) {
            siege.removeAttacker(clan);
        }

        for (SiegableHall hall :
                CHSiegeManager.getInstance().getConquerableHalls().values()) {
            hall.removeAttacker(clan);
        }

        final ClanMember leaderMember = clan.getLeader();
        clan.getWarehouse()
                .destroyAllItems(
                        ItemProcessType.DESTROY,
                        leaderMember == null ? null : clan.getLeader().getPlayer(),
                        null);

        for (ClanMember member : clan.getMembers()) {
            clan.removeClanMember(member.getObjectId(), 0);
        }

        clans.remove(clanId);
        // The war rows of the clan go with its row; the clans it fought forget the wars too.
        for (Clan other : clans.values()) {
            other.getWarList().remove(clanId);
            other.getAttackerList().remove(clanId);
        }

        clanRepository.delete(clanId);
        IdManager.getInstance().releaseId(clanId);

        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_DESTROY)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanDestroy(leaderMember, clan));
        }
    }

    /**
     * Frees every residence the clan owns. Must run while the clan is still in the table with its members and row: the
     * release paths look the owner up there, and a hall sale auction references the clan row without a cascade.
     */
    private void releaseResidences(Clan clan) {
        final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
        if (castle != null) {
            castle.removeOwner(clan);
            castle.setTaxPercent(0);
        }

        final Fort fort = FortManager.getInstance().getFortByOwner(clan);
        if (fort != null) {
            fort.removeOwner(true);
        }

        final ClanHall hall = ClanHallTable.getInstance().getAbstractHallByOwner(clan);
        if (hall == null) {
            return;
        }

        if (hall.isSiegableHall()) {
            hall.free();
            return;
        }

        final ClanHallAuction sale = ClanHallAuctionManager.getInstance().getAuction(hall.getId());
        if (sale != null) {
            sale.cancelAuction();
        }

        ClanHallTable.getInstance().setFree(hall.getId());
        ClanHallAuctionManager.getInstance().initNPC(hall.getId());
    }

    public void scheduleRemoveClan(int clanId) {
        ThreadPool.schedule(
                () -> {
                    if (getClan(clanId) == null) {
                        return;
                    }

                    if (getClan(clanId).getDissolvingExpiryTime() != 0) {
                        destroyClan(clanId);
                    }
                },
                Math.max(getClan(clanId).getDissolvingExpiryTime() - ClockBridge.wallMillis(), 300000));
    }

    public boolean isAllyExists(String allyName) {
        for (Clan clan : clans.values()) {
            if ((clan.getAllyName() != null) && clan.getAllyName().equalsIgnoreCase(allyName)) {
                return true;
            }
        }

        return false;
    }

    public void storeClanWars(int clanId1, int clanId2) {
        final Clan clan1 = getClan(clanId1);
        final Clan clan2 = getClan(clanId2);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CLAN_WAR_START)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnClanWarStart.builder().clan1(clan1).clan2(clan2).build());
        }

        clan1.setEnemyClan(clan2);
        clan2.setAttackerClan(clan1);
        clan1.broadcastClanStatus();
        clan2.broadcastClanStatus();
        warRepository.insert(clanId1, clanId2);

        // SystemMessage msg = new SystemMessage(SystemMessageId.WAR_WITH_THE_S1_CLAN_HAS_BEGUN);
        //
        SystemMessage msg = new SystemMessage(
                SystemMessageId
                        .A_CLAN_WAR_HAS_BEEN_DECLARED_AGAINST_THE_CLAN_S1_IF_YOU_ARE_KILLED_DURING_THE_CLAN_WAR_BY_MEMBERS_OF_THE_OPPOSING_CLAN_YOU_WILL_ONLY_LOSE_A_QUARTER_OF_THE_NORMAL_EXPERIENCE_FROM_DEATH);
        msg.addString(clan2.getName());
        clan1.broadcastToOnlineMembers(msg);

        // msg = new SystemMessage(SystemMessageId.WAR_WITH_THE_S1_CLAN_HAS_BEGUN);
        // msg.addString(clan1.getName());
        // clan2.broadcastToOnlineMembers(msg);
        // clan1 declared clan war.
        msg = new SystemMessage(SystemMessageId.S1_HAS_DECLARED_A_CLAN_WAR);
        msg.addString(clan1.getName());
        clan2.broadcastToOnlineMembers(msg);
    }

    public void deleteClanWars(int clanId1, int clanId2) {
        final Clan clan1 = getClan(clanId1);
        final Clan clan2 = getClan(clanId2);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_CLAN_WAR_FINISH)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnClanWarFinish.builder().clan1(clan1).clan2(clan2).build());
        }

        clan1.deleteEnemyClan(clan2);
        clan2.deleteAttackerClan(clan1);
        clan2.deleteEnemyClan(clan1);
        clan1.deleteAttackerClan(clan2);
        clan1.broadcastClanStatus();
        clan2.broadcastClanStatus();

        transactions.executeWithoutResult(status -> {
            warRepository.delete(clanId1, clanId2);
            warRepository.delete(clanId2, clanId1);
        });

        // SystemMessage msg = new SystemMessage(SystemMessageId.WAR_WITH_THE_S1_CLAN_HAS_ENDED);
        SystemMessage msg = new SystemMessage(SystemMessageId.THE_WAR_AGAINST_S1_CLAN_HAS_BEEN_STOPPED);
        msg.addString(clan2.getName());
        clan1.broadcastToOnlineMembers(msg);
        msg = new SystemMessage(SystemMessageId.THE_CLAN_S1_HAS_DECIDED_TO_STOP_THE_WAR);
        msg.addString(clan1.getName());
        clan2.broadcastToOnlineMembers(msg);
    }

    public void checkSurrender(Clan clan1, Clan clan2) {
        int count = 0;
        for (ClanMember member : clan1.getMembers()) {
            if ((member != null) && (member.getPlayer().getWantsPeace() == 1)) {
                count++;
            }
        }

        if (count == (clan1.getMembers().size() - 1)) {
            deleteClanWars(clan1.getId(), clan2.getId());
        }
    }

    private void restoreClanWars() {
        for (ClanWar war : warRepository.findAll()) {
            final Clan clan1 = getClan(war.clanId());
            final Clan clan2 = getClan(war.enemyClanId());
            if ((clan1 != null) && (clan2 != null)) {
                clan1.setEnemyClan(war.enemyClanId());
                clan2.setAttackerClan(war.clanId());
            } else {
                log.warn("Restorewars one of clans is null clan1:{} clan2:{}", clan1, clan2);
            }
        }
    }

    /**
     * Check for nonexistent alliances
     */
    private void allianceCheck() {
        for (Clan clan : clans.values()) {
            final int allyId = clan.getAllyId();
            if ((allyId != 0) && (clan.getId() != allyId) && !clans.containsKey(allyId)) {
                clan.setAllyId(0);
                clan.setAllyName(null);
                clan.changeAllyCrest(0, true);
                clan.updateClanInDB();
                log.info("Removed alliance from clan: {}", clan);
            }
        }
    }

    public List<Clan> getClanAllies(int allianceId) {
        final List<Clan> clanAllies = new ArrayList<>();
        if (allianceId != 0) {
            for (Clan clan : clans.values()) {
                if ((clan != null) && (clan.getAllyId() == allianceId)) {
                    clanAllies.add(clan);
                }
            }
        }

        return clanAllies;
    }

    public void shutdown() {
        for (Clan clan : clans.values()) {
            clan.updateClanInDB();
        }
    }

    private void updateClanRanks() {
        for (Clan clan : clans.values()) {
            clan.setRank(getClanRank(clan));
        }
    }

    public int getClanRank(Clan clan) {
        if (clan.getLevel() < 3) {
            return 0;
        }

        int rank = 1;
        for (Clan c : clans.values()) {
            if ((clan != c)
                    && ((clan.getLevel() < c.getLevel())
                            || ((clan.getLevel() == c.getLevel())
                                    && (clan.getReputationScore() <= c.getReputationScore())))) {
                rank++;
            }
        }

        return rank;
    }

    public Optional<ClanRow> findClan(int clanId) {
        return clanRepository.find(clanId);
    }

    public int findCastleIdOfOwner(int clanId) {
        return castleStateRepository.findCastleIdByOwner(clanId).orElse(0);
    }

    public List<CharacterClanMember> findMembers(int clanId) {
        return characterRepository.findClanMembers(clanId);
    }

    public Optional<ClanNotice> findNotice(int clanId) {
        return noticeRepository.find(clanId);
    }

    public List<ClanSkill> findSkills(int clanId) {
        return skillRepository.findAll(clanId);
    }

    public List<SubPledgeRow> findSubPledges(int clanId) {
        return subPledgeRepository.findAll(clanId);
    }

    public List<RankPrivilege> findRankPrivileges(int clanId) {
        return rankPrivilegeRepository.findAll(clanId);
    }

    public List<WarOpponent> findDeclaredWarsOnly(int clanId) {
        return warRepository.findDeclaredOnly(clanId);
    }

    public List<WarOpponent> findAttackersOnly(int clanId) {
        return warRepository.findAttackersOnly(clanId);
    }

    public List<WarOpponent> findMutualWars(int clanId) {
        return warRepository.findMutual(clanId);
    }

    public void insertClan(Clan clan) {
        clanRepository.insert(ClanRow.builder()
                .clanId(clan.getId())
                .name(clan.getName())
                .level(clan.getLevel())
                .reputationScore(clan.getReputationScore())
                .bloodAllianceCount(clan.getBloodAllianceCount())
                .bloodOathCount(clan.getBloodOathCount())
                .leaderId(clan.getLeaderId())
                .newLeaderId(clan.getNewLeaderId())
                .allyId(clan.getAllyId())
                .allyName(clan.getAllyName())
                .crestId(clan.getCrestId())
                .crestLargeId(clan.getCrestLargeId())
                .allyCrestId(clan.getAllyCrestId())
                .bidClanHallId(clan.getAuctionBiddedAt())
                .allyPenaltyEndsAt(instantOrNull(clan.getAllyPenaltyExpiryTime()))
                .allyPenaltyType(clan.getAllyPenaltyType())
                .characterPenaltyEndsAt(instantOrNull(clan.getCharPenaltyExpiryTime()))
                .dissolvingEndsAt(instantOrNull(clan.getDissolvingExpiryTime()))
                .build());
    }

    public void updateClan(Clan clan) {
        clanRepository.update(ClanUpdate.builder()
                .clanId(clan.getId())
                .leaderId(clan.getLeaderId())
                .newLeaderId(clan.getNewLeaderId())
                .allyId(clan.getAllyId())
                .allyName(clan.getAllyName())
                .reputationScore(clan.getReputationScore())
                .allyPenaltyEndsAt(instantOrNull(clan.getAllyPenaltyExpiryTime()))
                .allyPenaltyType(clan.getAllyPenaltyType())
                .characterPenaltyEndsAt(instantOrNull(clan.getCharPenaltyExpiryTime()))
                .dissolvingEndsAt(instantOrNull(clan.getDissolvingExpiryTime()))
                .build());
    }

    public void updateBloodAllianceCount(int clanId, int count) {
        clanRepository.updateBloodAllianceCount(clanId, count);
    }

    public void updateBloodOathCount(int clanId, int count) {
        clanRepository.updateBloodOathCount(clanId, count);
    }

    public void updateLevel(int clanId, int level) {
        clanRepository.updateLevel(clanId, level);
    }

    /**
     * @param clanHallId 0 when the clan bids nowhere; else its bid must be stored first
     */
    public void updateBidClanHall(int clanId, int clanHallId) {
        clanRepository.updateBidClanHall(clanId, clanHallId);
    }

    public void updateCrest(int clanId, int crestId) {
        clanRepository.updateCrest(clanId, crestId);
    }

    public void updateLargeCrest(int clanId, int crestId) {
        clanRepository.updateLargeCrest(clanId, crestId);
    }

    public void updateAllyCrest(int clanId, int crestId) {
        clanRepository.updateAllyCrest(clanId, crestId);
    }

    public void updateAllyCrestOfAlliance(int allyId, int crestId) {
        clanRepository.updateAllyCrestOfAlliance(allyId, crestId);
    }

    /**
     * @param notice {@code null} for none
     */
    public void storeNotice(int clanId, String notice, boolean enabled) {
        noticeRepository.upsert(clanId, notice, enabled);
    }

    public void storeSkill(int clanId, int skillId, int skillLevel, int pledgeType) {
        skillRepository.upsert(clanId, skillId, skillLevel, pledgeType);
    }

    public void insertSubPledge(int clanId, int pledgeType, String name, int leaderId) {
        subPledgeRepository.insert(clanId, pledgeType, name, leaderId);
    }

    public void updateSubPledge(int clanId, int pledgeType, String name, int leaderId) {
        subPledgeRepository.update(clanId, pledgeType, name, leaderId);
    }

    /**
     * @param powerGrade 1 to 9
     */
    public void storeRankPrivileges(int clanId, int powerGrade, int privilegeMask) {
        rankPrivilegeRepository.upsert(clanId, powerGrade, privilegeMask);
    }

    public void updateMemberPrivileges(int objectId, int privileges) {
        characterRepository.updateClanPrivileges(objectId, privileges);
    }

    /**
     * @param joinPenaltyEndsAt epoch milliseconds, 0 for no penalty
     * @param createPenaltyEndsAt epoch milliseconds, 0 for no penalty
     */
    public void removeMember(int objectId, long joinPenaltyEndsAt, long createPenaltyEndsAt) {
        characterRepository.removeFromClan(
                objectId, instantOrNull(joinPenaltyEndsAt), instantOrNull(createPenaltyEndsAt));
    }

    public void updateMemberPledgeType(int objectId, int pledgeType) {
        characterRepository.updatePledgeType(objectId, pledgeType);
    }

    public void updateMemberPowerGrade(int objectId, int powerGrade) {
        characterRepository.updatePowerGrade(objectId, powerGrade);
    }

    public void updateMemberApprenticeAndSponsor(int objectId, int apprenticeId, int sponsorId) {
        characterRepository.updateApprenticeAndSponsor(objectId, apprenticeId, sponsorId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static ClanTable getInstance() {
        final ClanTable current = instance;
        if (current == null) {
            throw new IllegalStateException("ClanTable is used before the Spring context created it.");
        }
        return current;
    }
}
