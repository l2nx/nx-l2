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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.ConfigReader;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.TerritorySpawn;
import app.l2nx.highfive.data.holders.TerritoryTemplate;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.data.xml.TerritoryData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.SiegeFlag;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.Siegable;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.TerritoryWard;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.territory.TerritoryRegistrationRepository;
import app.l2nx.highfive.repository.territory.TerritoryStateRepository;
import app.l2nx.highfive.repository.territory.model.TerritoryRegistrantType;
import app.l2nx.highfive.repository.territory.model.TerritoryRegistration;
import app.l2nx.highfive.util.LocationUtil;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TerritoryWarManager implements Siegable {
    private static volatile TerritoryWarManager instance;

    private static final String TW_CONFIG_FILE = "./config/TerritoryWar.ini";

    public static final Map<Integer, Integer> TERRITORY_ITEM_IDS = new HashMap<>();

    static {
        TERRITORY_ITEM_IDS.put(81, 13757);
        TERRITORY_ITEM_IDS.put(82, 13758);
        TERRITORY_ITEM_IDS.put(83, 13759);
        TERRITORY_ITEM_IDS.put(84, 13760);
        TERRITORY_ITEM_IDS.put(85, 13761);
        TERRITORY_ITEM_IDS.put(86, 13762);
        TERRITORY_ITEM_IDS.put(87, 13763);
        TERRITORY_ITEM_IDS.put(88, 13764);
        TERRITORY_ITEM_IDS.put(89, 13765);
    }

    public static String qn = "TerritoryWarSuperClass";
    public static String GLOBAL_VARIABLE = "nextTWStartDate";
    public static int DEFENDERMAXCLANS; // Max number of clans
    public static int DEFENDERMAXPLAYERS; // Max number of individual player
    public static int CLANMINLEVEL;
    public static int PLAYERMINLEVEL;
    public static int MINTWBADGEFORNOBLESS;
    public static int MINTWBADGEFORSTRIDERS;
    public static int MINTWBADGEFORBIGSTRIDER;
    public static Long WARLENGTH;
    public static boolean PLAYER_WITH_WARD_CAN_BE_KILLED_IN_PEACEZONE;
    public static boolean SPAWN_WARDS_WHEN_TW_IS_NOT_IN_PROGRESS;
    public static boolean RETURN_WARDS_WHEN_TW_STARTS;

    // Territory War settings
    private final Map<Integer, List<Clan>> registeredClans = new ConcurrentHashMap<>();
    private final Map<Integer, List<Integer>> registeredMercenaries = new ConcurrentHashMap<>();
    private final Map<Integer, Territory> territoryList = new ConcurrentHashMap<>();
    protected Collection<Integer> disguisedPlayers = ConcurrentHashMap.newKeySet();

    @Getter
    private final Collection<TerritoryWard> allTerritoryWards = ConcurrentHashMap.newKeySet();

    private final Map<Clan, SiegeFlag> clanFlags = new ConcurrentHashMap<>();
    private final Map<Integer, Integer[]> participantPoints = new ConcurrentHashMap<>();
    protected Calendar twStart = ClockBridge.calendar();

    @Getter
    protected boolean registrationOver = true;

    protected boolean twChannelOpen = false;
    boolean twInProgress = false;
    protected ScheduledFuture<?> scheduledStartTWTask = null;
    protected ScheduledFuture<?> scheduledEndTWTask = null;
    protected ScheduledFuture<?> scheduledRewardOnlineTask = null;

    private final TerritoryRegistrationRepository registrationRepository;
    private final TerritoryStateRepository stateRepository;

    public TerritoryWarManager(
            TerritoryRegistrationRepository registrationRepository, TerritoryStateRepository stateRepository) {
        this.registrationRepository = registrationRepository;
        this.stateRepository = stateRepository;
        instance = this;
    }

    public int getRegisteredTerritoryId(Player player) {
        if ((player == null) || !twChannelOpen || (player.getLevel() < PLAYERMINLEVEL)) {
            return 0;
        }

        final Clan clan = player.getClan();
        if (clan != null) {
            if (clan.getCastleId() > 0) {
                return clan.getCastleId() + 80;
            }

            for (int cId : registeredClans.keySet()) {
                if (registeredClans.get(cId).contains(clan)) {
                    return cId + 80;
                }
            }
        }

        for (int cId : registeredMercenaries.keySet()) {
            if (registeredMercenaries.get(cId).contains(player.getObjectId())) {
                return cId + 80;
            }
        }

        return 0;
    }

    public boolean isAllyField(Player player, int fieldId) {
        if ((player == null) || (player.getSiegeSide() == 0)) {
            return false;
        } else if ((player.getSiegeSide() - 80) == fieldId) {
            return true;
        } else if ((fieldId > 100)
                && territoryList.containsKey((player.getSiegeSide() - 80))
                && (territoryList.get((player.getSiegeSide() - 80)).getFortId() == fieldId)) {
            return true;
        }

        return false;
    }

    /**
     * @param castleId
     * @param clan The Clan of the player
     * @return true if the clan is registered
     */
    public boolean checkIsRegistered(int castleId, Clan clan) {
        if (clan == null) {
            return false;
        }

        if (clan.getCastleId() > 0) {
            return (castleId == -1 ? true : (clan.getCastleId() == castleId));
        }

        if (castleId == -1) {
            for (int cId : registeredClans.keySet()) {
                if (registeredClans.get(cId).contains(clan)) {
                    return true;
                }
            }

            return false;
        }

        return registeredClans.get(castleId).contains(clan);
    }

    /**
     * @param castleId
     * @param objId
     * @return true if the player is registered
     */
    public boolean checkIsRegistered(int castleId, int objId) {
        if (castleId == -1) {
            for (int cId : registeredMercenaries.keySet()) {
                if (registeredMercenaries.get(cId).contains(objId)) {
                    return true;
                }
            }

            return false;
        }

        return registeredMercenaries.get(castleId).contains(objId);
    }

    public Territory getTerritory(int castleId) {
        return territoryList.get(castleId);
    }

    public List<Territory> getAllTerritories() {
        final List<Territory> ret = new ArrayList<>();
        for (Territory t : territoryList.values()) {
            if (t.getOwnerClan() != null) {
                ret.add(t);
            }
        }

        return ret;
    }

    public List<Clan> getRegisteredClans(int castleId) {
        return registeredClans.get(castleId);
    }

    public void addDisguisedPlayer(int playerObjId) {
        disguisedPlayers.add(playerObjId);
    }

    public boolean isDisguised(int playerObjId) {
        return disguisedPlayers.contains(playerObjId);
    }

    public List<Integer> getRegisteredMercenaries(int castleId) {
        return registeredMercenaries.get(castleId);
    }

    public long getTWStartTimeInMillis() {
        return twStart.getTimeInMillis();
    }

    public Calendar getTWStart() {
        return twStart;
    }

    public void setTWStartTimeInMillis(long time) {
        twStart.setTimeInMillis(time);
        if (twInProgress) {
            if (scheduledEndTWTask != null) {
                scheduledEndTWTask.cancel(false);
            }

            scheduledEndTWTask = WorldPartition.loop().schedule(new ScheduleEndTWTask(), 1000, TimeUnit.MILLISECONDS);
        } else {
            if (scheduledStartTWTask != null) {
                scheduledStartTWTask.cancel(false);
            }

            scheduledStartTWTask =
                    WorldPartition.loop().schedule(new ScheduleStartTWTask(), 1000, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isTWChannelOpen() {
        return twChannelOpen;
    }

    public void registerClan(int castleId, Clan clan) {
        if ((clan == null)
                || ((registeredClans.get(castleId) != null)
                        && registeredClans.get(castleId).contains(clan))) {
            return;
        }

        registeredClans.putIfAbsent(castleId, new CopyOnWriteArrayList<>());
        registeredClans.get(castleId).add(clan);
        addRegistration(castleId, clan.getId(), TerritoryRegistrantType.CLAN);
    }

    public void registerMerc(int castleId, Player player) {
        if ((player == null)
                || (player.getLevel() < PLAYERMINLEVEL)
                || ((registeredMercenaries.get(castleId) != null)
                        && registeredMercenaries.get(castleId).contains(player.getObjectId()))) {
            return;
        }

        registeredMercenaries.putIfAbsent(castleId, new CopyOnWriteArrayList<>());
        registeredMercenaries.get(castleId).add(player.getObjectId());
        addRegistration(castleId, player.getObjectId(), TerritoryRegistrantType.MERCENARY);
    }

    public void removeClan(int castleId, Clan clan) {
        if ((clan != null)
                && (registeredClans.get(castleId) != null)
                && registeredClans.get(castleId).remove(clan)) {
            removeRegistration(castleId, clan.getId());
        }
    }

    public void removeMerc(int castleId, Player player) {
        if ((player != null)
                && (registeredMercenaries.get(castleId) != null)
                && registeredMercenaries.get(castleId).contains(player.getObjectId())) {
            registeredMercenaries
                    .get(castleId)
                    .remove(registeredMercenaries.get(castleId).indexOf(player.getObjectId()));
            removeRegistration(castleId, player.getObjectId());
        }
    }

    public boolean isTWInProgress() {
        return twInProgress;
    }

    public void territoryCatapultDestroyed(int castleId) {
        if (territoryList.get(castleId) != null) {
            territoryList.get(castleId).changeNPCsSpawn(2, false);
        }

        for (Door door : CastleManager.getInstance().getCastleById(castleId).getDoors()) {
            door.openMe();
        }
    }

    public Npc addTerritoryWard(int territoryId, int newOwnerId, int oldOwnerId, boolean broadcastMessage) {
        Npc ret = null;
        final Territory terNew = territoryList.get(newOwnerId);
        if (terNew != null) {
            final TerritoryNPCSpawn ward = terNew.getFreeWardSpawnPlace();
            if (ward != null) {
                ward.id = territoryId;
                ret = spawnNPC(36491 + territoryId, ward.getLocation());
                ward.setNPC(ret);
                if (!twInProgress && !SPAWN_WARDS_WHEN_TW_IS_NOT_IN_PROGRESS) {
                    ret.decayMe();
                }

                if ((terNew.getOwnerClan() != null) && terNew.getOwnedWardIds().contains(newOwnerId + 80)) {
                    for (int wardId : terNew.getOwnedWardIds()) {
                        final List<SkillLearn> residentialSkills =
                                SkillTreeData.getInstance().getAvailableResidentialSkills(wardId);
                        for (SkillLearn s : residentialSkills) {
                            final Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
                            if (sk != null) {
                                for (Player member : terNew.getOwnerClan().getOnlineMembers(0)) {
                                    if (!member.isInOlympiadMode()) {
                                        member.addSkill(sk, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            final Territory terOld = territoryList.get(oldOwnerId);
            if (terOld != null) {
                terOld.removeWard(territoryId);
                updateTerritoryData(terOld);
                updateTerritoryData(terNew);
                if (broadcastMessage) {
                    final SystemMessage sm =
                            new SystemMessage(SystemMessageId.CLAN_S1_HAS_SUCCEEDED_IN_CAPTURING_S2_S_TERRITORY_WARD);
                    sm.addString(terNew.getOwnerClan().getName());
                    sm.addCastleId(territoryId);
                    announceToParticipants(sm, 135000, 13500);
                }

                if (terOld.getOwnerClan() != null) {
                    final List<SkillLearn> territorySkills =
                            SkillTreeData.getInstance().getAvailableResidentialSkills(territoryId);
                    for (SkillLearn s : territorySkills) {
                        final Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
                        if (sk != null) {
                            for (Player member : terOld.getOwnerClan().getOnlineMembers(0)) {
                                member.removeSkill(sk, false);
                            }
                        }
                    }

                    if (!terOld.getOwnedWardIds().isEmpty()
                            && !terOld.getOwnedWardIds().contains(oldOwnerId + 80)) {
                        for (int wardId : terOld.getOwnedWardIds()) {
                            final List<SkillLearn> wardSkills =
                                    SkillTreeData.getInstance().getAvailableResidentialSkills(wardId);
                            for (SkillLearn s : wardSkills) {
                                final Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
                                if (sk != null) {
                                    for (Player member : terOld.getOwnerClan().getOnlineMembers(0)) {
                                        member.removeSkill(sk, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            log.warn("Missing territory for new Ward owner: {};{}", newOwnerId, territoryId);
        }

        return ret;
    }

    public SiegeFlag getHQForClan(Clan clan) {
        if (clan.getCastleId() > 0) {
            return territoryList.get(clan.getCastleId()).getHQ();
        }

        return null;
    }

    public SiegeFlag getHQForTerritory(int territoryId) {
        return territoryList.get(territoryId - 80).getHQ();
    }

    public void setHQForClan(Clan clan, SiegeFlag hq) {
        if (clan.getCastleId() > 0) {
            territoryList.get(clan.getCastleId()).setHQ(hq);
        }
    }

    public void addClanFlag(Clan clan, SiegeFlag flag) {
        clanFlags.put(clan, flag);
    }

    public boolean isClanHasFlag(Clan clan) {
        return clanFlags.containsKey(clan);
    }

    public SiegeFlag getFlagForClan(Clan clan) {
        return clanFlags.get(clan);
    }

    public void removeClanFlag(Clan clan) {
        clanFlags.remove(clan);
    }

    public TerritoryWard getTerritoryWardForOwner(int castleId) {
        for (TerritoryWard twWard : allTerritoryWards) {
            if (twWard.getTerritoryId() == castleId) {
                return twWard;
            }
        }

        return null;
    }

    public TerritoryWard getTerritoryWard(int territoryId) {
        for (TerritoryWard twWard : allTerritoryWards) {
            if (twWard.getTerritoryId() == territoryId) {
                return twWard;
            }
        }

        return null;
    }

    public TerritoryWard getTerritoryWard(Player player) {
        for (TerritoryWard twWard : allTerritoryWards) {
            if (twWard.playerId == player.getObjectId()) {
                return twWard;
            }
        }

        return null;
    }

    public void dropCombatFlag(Player player, boolean isKilled, boolean isSpawnBack) {
        for (TerritoryWard twWard : allTerritoryWards) {
            if (twWard.playerId == player.getObjectId()) {
                twWard.dropIt();
                if (twInProgress) {
                    if (isKilled) {
                        twWard.spawnMe();
                    } else if (isSpawnBack) {
                        twWard.spawnBack();
                    } else {
                        for (TerritoryNPCSpawn wardSpawn :
                                territoryList.get(twWard.getOwnerCastleId()).getOwnedWard()) {
                            if (wardSpawn.getId() == twWard.getTerritoryId()) {
                                wardSpawn.setNPC(wardSpawn.getNpc().getSpawn().doSpawn(false));
                                twWard.unSpawnMe();
                                twWard.setNpc(wardSpawn.getNpc());
                            }
                        }
                    }
                }

                if (isKilled) {
                    final SystemMessage sm =
                            new SystemMessage(SystemMessageId.THE_CHARACTER_THAT_ACQUIRED_S1_S_WARD_HAS_BEEN_KILLED);
                    sm.addString(twWard.getNpc().getName().replace(" Ward", ""));
                    announceToParticipants(sm, 0, 0);
                }
            }
        }
    }

    public void giveTWQuestPoint(Player player) {
        participantPoints.putIfAbsent(player.getObjectId(), new Integer[] {player.getSiegeSide(), 0, 0, 0, 0, 0, 0});
        participantPoints.get(player.getObjectId())[2]++;
    }

    public void giveTWPoint(Player killer, int victimSide, int type) {
        if (victimSide == 0) {
            return;
        }

        if ((killer.getParty() != null) && (type < 5)) {
            for (Player pl : killer.getParty().getMembers()) {
                if ((pl.getSiegeSide() == victimSide)
                        || (pl.getSiegeSide() == 0)
                        || !LocationUtil.checkIfInRange(2000, killer, pl, false)) {
                    continue;
                }

                participantPoints.putIfAbsent(pl.getObjectId(), new Integer[] {pl.getSiegeSide(), 0, 0, 0, 0, 0, 0});
                participantPoints.get(pl.getObjectId())[type]++;
            }
        } else {
            if ((killer.getSiegeSide() == victimSide) || (killer.getSiegeSide() == 0)) {
                return;
            }

            participantPoints.putIfAbsent(
                    killer.getObjectId(), new Integer[] {killer.getSiegeSide(), 0, 0, 0, 0, 0, 0});
            participantPoints.get(killer.getObjectId())[type]++;
        }
    }

    public int[] calcReward(Player player) {
        if (participantPoints.containsKey(player.getObjectId())) {
            final int[] reward = new int[2];
            final Integer[] temp = participantPoints.get(player.getObjectId());
            reward[0] = temp[0];
            reward[1] = 0;

            // badges for being online. if char was not online at least 10 mins
            // than he cannot get rewards(also this will handle that player already get his/her rewards)
            if (temp[6] < 10) {
                return reward;
            }

            reward[1] += (temp[6] > 70 ? 7 : (int) (temp[6] * 0.1));

            // badges for player Quests
            reward[1] += temp[2] * 7;

            // badges for player Kills
            if (temp[1] < 50) {
                reward[1] += temp[1] * 0.1;
            } else if (temp[1] < 120) {
                reward[1] += (5 + ((temp[1] - 50) / 14));
            } else {
                reward[1] += 10;
            }

            // badges for territory npcs
            reward[1] += temp[3];

            // badges for territory catapults
            reward[1] += temp[4] * 2;

            // badges for territory Wards
            reward[1] += (temp[5] > 0 ? 5 : 0);

            // badges for territory quest done
            reward[1] += Math.min(territoryList.get(temp[0] - 80).getQuestDone()[0], 10);
            reward[1] += territoryList.get(temp[0] - 80).getQuestDone()[1];
            reward[1] += territoryList.get(temp[0] - 80).getOwnedWardIds().size();
            return reward;
        }

        return new int[] {0, 0};
    }

    public void debugReward(Player player) {
        player.sendMessage("Registred TerrId: " + player.getSiegeSide());
        if (participantPoints.containsKey(player.getObjectId())) {
            final Integer[] temp = participantPoints.get(player.getObjectId());
            player.sendMessage("TerrId: " + temp[0]);
            player.sendMessage("PcKill: " + temp[1]);
            player.sendMessage("PcQuests: " + temp[2]);
            player.sendMessage("npcKill: " + temp[3]);
            player.sendMessage("CatatKill: " + temp[4]);
            player.sendMessage("WardKill: " + temp[5]);
            player.sendMessage("onlineTime: " + temp[6]);
        } else {
            player.sendMessage("No points for you!");
        }

        if (territoryList.containsKey(player.getSiegeSide() - 80)) {
            player.sendMessage("Your Territory's jobs:");
            player.sendMessage(
                    "npcKill: " + territoryList.get(player.getSiegeSide() - 80).getQuestDone()[0]);
            player.sendMessage("WardCaptured: "
                    + territoryList.get(player.getSiegeSide() - 80).getQuestDone()[1]);
        }
    }

    public void resetReward(Player player) {
        if (participantPoints.containsKey(player.getObjectId())) {
            participantPoints.get(player.getObjectId())[6] = 0;
        }
    }

    public Npc spawnNPC(int npcId, Location loc) {
        try {
            final Spawn spawnDat = new Spawn(npcId);
            spawnDat.setAmount(1);
            spawnDat.setXYZ(loc);
            spawnDat.setHeading(loc.getHeading());
            spawnDat.stopRespawn();
            return spawnDat.doSpawn(false);
        } catch (Exception e) {
            log.warn("{}", e.getMessage(), e);
        }

        return null;
    }

    private void addRegistration(int castleId, int registrantId, TerritoryRegistrantType registrantType) {
        try {
            registrationRepository.insert(castleId, registrantId, registrantType);
        } catch (DataAccessException e) {
            log.warn("Exception: Territory War registration: {}", e.getMessage(), e);
        }
    }

    private void removeRegistration(int castleId, int registrantId) {
        try {
            registrationRepository.delete(castleId, registrantId);
        } catch (DataAccessException e) {
            log.warn("Exception: Territory War registration: {}", e.getMessage(), e);
        }
    }

    private void updateTerritoryData(Territory ter) {
        try {
            stateRepository.save(ter.getTerritoryId(), ter.getOwnedWardIds());
        } catch (DataAccessException e) {
            log.warn("Exception: Territory Data update: {}", e.getMessage(), e);
        }
    }

    public void load() {
        final ConfigReader territoryWarConfig = new ConfigReader(TW_CONFIG_FILE);

        // Siege configurations.
        DEFENDERMAXCLANS = territoryWarConfig.getInt("DefenderMaxClans", 500);
        DEFENDERMAXPLAYERS = territoryWarConfig.getInt("DefenderMaxPlayers", 500);
        CLANMINLEVEL = territoryWarConfig.getInt("ClanMinLevel", 0);
        PLAYERMINLEVEL = territoryWarConfig.getInt("PlayerMinLevel", 40);
        WARLENGTH = territoryWarConfig.getLong("WarLength", 120) * 60000;
        PLAYER_WITH_WARD_CAN_BE_KILLED_IN_PEACEZONE =
                territoryWarConfig.getBoolean("PlayerWithWardCanBeKilledInPeaceZone", false);
        SPAWN_WARDS_WHEN_TW_IS_NOT_IN_PROGRESS =
                territoryWarConfig.getBoolean("SpawnWardsWhenTWIsNotInProgress", false);
        RETURN_WARDS_WHEN_TW_STARTS = territoryWarConfig.getBoolean("ReturnWardsWhenTWStarts", false);
        MINTWBADGEFORNOBLESS = territoryWarConfig.getInt("MinTerritoryBadgeForNobless", 100);
        MINTWBADGEFORSTRIDERS = territoryWarConfig.getInt("MinTerritoryBadgeForStriders", 50);
        MINTWBADGEFORBIGSTRIDER = territoryWarConfig.getInt("MinTerritoryBadgeForBigStrider", 80);

        for (TerritoryTemplate template : TerritoryData.getInstance().getTerritories()) {
            final int castleId = template.castleId();
            final Territory territory = territoryList.computeIfAbsent(castleId, k -> new Territory(castleId));
            territory.fortId = template.fortId();
            for (TerritorySpawn spawn : template.spawns()) {
                final Location loc = spawn.spawnPoint().toLocation();
                switch (spawn.type()) {
                    case 0: // town npcs
                    case 1: // fortress npcs
                    case 2: // castle npcs
                    {
                        territory
                                .getSpawnList()
                                .add(new TerritoryNPCSpawn(castleId, loc, spawn.npcId(), spawn.type(), null));
                        break;
                    }
                    case 3: // ward spawns
                    {
                        territory.addWardSpawnPlace(loc);
                        break;
                    }
                    default: {
                        log.warn("Unknown npc type {} in territory {}", spawn.type(), template.id());
                    }
                }
            }
        }

        Map<Integer, List<Integer>> storedWards;
        try {
            storedWards = stateRepository.findAll();
        } catch (DataAccessException e) {
            log.warn("Territory list error(): {}", e.getMessage(), e);
            storedWards = Map.of();
        }

        for (Territory t : territoryList.values()) {
            final int castleId = t.getCastleId();
            if (CastleManager.getInstance().getCastleById(castleId).getOwnerId() > 0) {
                t.setOwnerClan(ClanTable.getInstance()
                        .getClan(CastleManager.getInstance()
                                .getCastleById(castleId)
                                .getOwnerId()));
                t.changeNPCsSpawn(0, true);
            }

            // A territory without a stored state still holds its own ward.
            for (int wardId : storedWards.getOrDefault(t.getTerritoryId(), List.of(t.getTerritoryId()))) {
                if (wardId > 0) {
                    addTerritoryWard(wardId, castleId, 0, false);
                }
            }
        }

        final List<TerritoryRegistration> registrations;
        try {
            registrations = registrationRepository.findAll();
        } catch (DataAccessException e) {
            log.warn("Registration list error: {}", e.getMessage(), e);
            return;
        }

        for (TerritoryRegistration registration : registrations) {
            final int castleId = registration.castleId();
            final int registeredId = registration.registrantId();
            if (registration.registrantType() == TerritoryRegistrantType.CLAN) {
                final Clan clan = ClanTable.getInstance().getClan(registeredId);
                if (clan != null) {
                    registeredClans
                            .computeIfAbsent(castleId, k -> new CopyOnWriteArrayList<>())
                            .add(clan);
                }
            } else {
                registeredMercenaries
                        .computeIfAbsent(castleId, k -> new CopyOnWriteArrayList<>())
                        .add(registeredId);
            }
        }
    }

    protected void startTerritoryWar() {
        if (territoryList == null) {
            log.warn("TerritoryList is NULL!");
            return;
        }

        final List<Territory> activeTerritoryList = new ArrayList<>();
        for (Territory t : territoryList.values()) {
            final Castle castle = CastleManager.getInstance().getCastleById(t.getCastleId());
            if (castle != null) {
                if (castle.getOwnerId() > 0) {
                    activeTerritoryList.add(t);
                }
            } else {
                log.warn("Castle missing! CastleId: {}", t.getCastleId());
            }
        }

        if (activeTerritoryList.size() < 2) {
            return;
        }

        twInProgress = true;
        updatePlayerTWStateFlags(false);

        // teleportPlayer(Siege.TeleportWhoType.Attacker, MapRegionTable.TeleportWhereType.Town); // Teleport to the
        // closest town
        for (Territory t : activeTerritoryList) {
            final Castle castle = CastleManager.getInstance().getCastleById(t.getCastleId());
            final Fort fort = FortManager.getInstance().getFortById(t.getFortId());

            // spawnControlTower(t.getCastleId()); // Spawn control tower
            if (castle != null) {
                t.changeNPCsSpawn(2, true);
                castle.spawnDoor(); // Spawn door
                castle.getZone().setSiegeInstance(this);
                castle.getZone().setActive(true);
                castle.getZone().updateZoneStatusForCharactersInside();
            } else {
                log.warn("Castle missing! CastleId: {}", t.getCastleId());
            }

            if (fort != null) {
                t.changeNPCsSpawn(1, true);
                fort.resetDoors(); // Spawn door
                fort.getZone().setSiegeInstance(this);
                fort.getZone().setActive(true);
                fort.getZone().updateZoneStatusForCharactersInside();
            } else {
                log.warn("Fort missing! FortId: {}", t.getFortId());
            }

            for (TerritoryNPCSpawn ward : t.getOwnedWard()) {
                if ((ward.getNpc() != null) && (t.getOwnerClan() != null)) {
                    if (!ward.getNpc().isSpawned()) {
                        ward.setNPC(ward.getNpc().getSpawn().doSpawn(false));
                    }

                    allTerritoryWards.add(new TerritoryWard(
                            ward.getId(),
                            ward.getLocation().getX(),
                            ward.getLocation().getY(),
                            ward.getLocation().getZ(),
                            0,
                            ward.getId() + 13479,
                            t.getCastleId(),
                            ward.getNpc()));
                }
            }

            t.getQuestDone()[0] = 0; // killed npc
            t.getQuestDone()[1] = 0; // captured wards
        }

        participantPoints.clear();

        if (RETURN_WARDS_WHEN_TW_STARTS) {
            for (TerritoryWard ward : allTerritoryWards) {
                if (ward.getOwnerCastleId() != (ward.getTerritoryId() - 80)) {
                    ward.unSpawnMe();
                    ward.setNpc(addTerritoryWard(
                            ward.getTerritoryId(), ward.getTerritoryId() - 80, ward.getOwnerCastleId(), false));
                    ward.setOwnerCastleId(ward.getTerritoryId() - 80);
                }
            }
        }

        World.broadcastToAllOnlinePlayers(new SystemMessage(SystemMessageId.TERRITORY_WAR_HAS_BEGUN));
    }

    protected void endTerritoryWar() {
        twInProgress = false;
        if (territoryList == null) {
            log.warn("TerritoryList is NULL!");
            return;
        }

        final List<Territory> activeTerritoryList = new ArrayList<>();
        for (Territory t : territoryList.values()) {
            final Castle castle = CastleManager.getInstance().getCastleById(t.getCastleId());
            if (castle != null) {
                if (castle.getOwnerId() > 0) {
                    activeTerritoryList.add(t);
                }
            } else {
                log.warn("Castle missing! CastleId: {}", t.getCastleId());
            }
        }

        updatePlayerTWStateFlags(true);

        for (TerritoryWard twWard : allTerritoryWards) {
            twWard.unSpawnMe();
        }

        allTerritoryWards.clear();

        // teleportPlayer(Siege.TeleportWhoType.Attacker, MapRegionTable.TeleportWhereType.Town); // Teleport to the
        // closest town
        if (activeTerritoryList.size() > 1) {
            for (Territory t : activeTerritoryList) {
                final Castle castle = CastleManager.getInstance().getCastleById(t.getCastleId());
                final Fort fort = FortManager.getInstance().getFortById(t.getFortId());
                if (castle != null) {
                    castle.spawnDoor();
                    t.changeNPCsSpawn(2, false);
                    castle.getZone().setActive(false);
                    castle.getZone().updateZoneStatusForCharactersInside();
                    castle.getZone().setSiegeInstance(null);
                } else {
                    log.warn("Castle missing! CastleId: {}", t.getCastleId());
                }

                if (fort != null) {
                    t.changeNPCsSpawn(1, false);
                    fort.getZone().setActive(false);
                    fort.getZone().updateZoneStatusForCharactersInside();
                    fort.getZone().setSiegeInstance(null);
                } else {
                    log.warn("Fort missing! FortId: {}", t.getFortId());
                }

                if (t.getHQ() != null) {
                    t.getHQ().deleteMe();
                }

                for (TerritoryNPCSpawn ward : t.getOwnedWard()) {
                    if (ward.getNpc() != null) {
                        if (!ward.getNpc().isSpawned() && SPAWN_WARDS_WHEN_TW_IS_NOT_IN_PROGRESS) {
                            ward.setNPC(ward.getNpc().getSpawn().doSpawn(false));
                        } else if (ward.getNpc().isSpawned() && !SPAWN_WARDS_WHEN_TW_IS_NOT_IN_PROGRESS) {
                            ward.getNpc().decayMe();
                        }
                    }
                }
            }
        }

        for (SiegeFlag flag : clanFlags.values()) {
            flag.deleteMe();
        }

        clanFlags.clear();

        for (Integer castleId : registeredClans.keySet()) {
            for (Clan clan : registeredClans.get(castleId)) {
                removeRegistration(castleId, clan.getId());
            }
        }

        for (Integer castleId : registeredMercenaries.keySet()) {
            for (Integer plObjId : registeredMercenaries.get(castleId)) {
                removeRegistration(castleId, plObjId);
            }
        }

        // Change next TW date.
        setNextTWDate();
        World.broadcastToAllOnlinePlayers(new SystemMessage(SystemMessageId.TERRITORY_WAR_HAS_ENDED));
    }

    public void setNextTWDate() {
        final Calendar cal = ClockBridge.calendar();
        final long nextSiegeDate = GlobalVariablesManager.getInstance().getLong(GLOBAL_VARIABLE, 0);
        if (nextSiegeDate > ClockBridge.wallMillis()) {
            cal.setTimeInMillis(nextSiegeDate);
        } else {
            // Let's check if territory war date was in the past.
            if (cal.before(ClockBridge.calendar())) {
                cal.setTimeInMillis(ClockBridge.wallMillis());
            }

            final boolean hasOwnedCastle = CastleManager.getInstance().hasOwnedCastle();
            cal.set(Calendar.DAY_OF_WEEK, hasOwnedCastle ? Calendar.SATURDAY : Calendar.SUNDAY);
            cal.set(Calendar.HOUR_OF_DAY, hasOwnedCastle ? 20 : 22);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            if (cal.before(ClockBridge.calendar())) {
                cal.add(Calendar.WEEK_OF_YEAR, 2);
            }

            GlobalVariablesManager.getInstance().set(GLOBAL_VARIABLE, cal.getTimeInMillis());
        }

        setTWStartTimeInMillis(cal.getTimeInMillis());
    }

    protected void updatePlayerTWStateFlags(boolean clear) {
        final Quest twQuest = ScriptManager.getInstance().getScript(qn);
        if (twQuest != null) {
            twQuest.setOnEnterWorld(twInProgress);
        }

        for (int castleId : registeredClans.keySet()) {
            for (Clan clan : registeredClans.get(castleId)) {
                for (Player player : clan.getOnlineMembers(0)) {
                    if (clear) {
                        player.setSiegeState((byte) 0);
                        if (!twChannelOpen) {
                            player.setSiegeSide(0);
                        }
                    } else {
                        if ((player.getLevel() < PLAYERMINLEVEL)
                                || (player.getPlayerClass().level() < 2)) {
                            continue;
                        }

                        if (twInProgress) {
                            player.setSiegeState((byte) 1);
                        }

                        player.setSiegeSide(80 + castleId);
                    }

                    player.broadcastUserInfo();
                }
            }
        }

        for (int castleId : registeredMercenaries.keySet()) {
            for (int objId : registeredMercenaries.get(castleId)) {
                final Player player = World.getPlayer(objId);
                if (player == null) {
                    continue;
                }

                if (clear) {
                    player.setSiegeState((byte) 0);
                    if (!twChannelOpen) {
                        player.setSiegeSide(0);
                    }
                } else {
                    if (twInProgress) {
                        player.setSiegeState((byte) 1);
                    }

                    player.setSiegeSide(80 + castleId);
                }

                player.broadcastUserInfo();
            }
        }

        for (Territory terr : territoryList.values()) {
            if (terr.getOwnerClan() != null) {
                for (Player player : terr.getOwnerClan().getOnlineMembers(0)) {
                    if (player == null) {
                        continue;
                    }

                    if (clear) {
                        player.setSiegeState((byte) 0);
                        if (!twChannelOpen) {
                            player.setSiegeSide(0);
                        }
                    } else {
                        if ((player.getLevel() < PLAYERMINLEVEL)
                                || (player.getPlayerClass().level() < 2)) {
                            continue;
                        }

                        if (twInProgress) {
                            player.setSiegeState((byte) 1);
                        }

                        player.setSiegeSide(80 + terr.getCastleId());
                    }

                    player.broadcastUserInfo();
                }
            }
        }
    }

    protected class RewardOnlineParticipants implements Runnable {
        @Override
        public void run() {
            if (twInProgress) {
                for (Player player : World.getPlayers()) {
                    if ((player != null) && (player.getSiegeSide() > 0)) {
                        giveTWPoint(player, 1000, 6);
                    }
                }
            } else {
                scheduledRewardOnlineTask.cancel(false);
            }
        }
    }

    protected class ScheduleStartTWTask implements Runnable {

        @Override
        public void run() {
            scheduledStartTWTask.cancel(false);
            try {
                final long timeRemaining = twStart.getTimeInMillis() - ClockBridge.wallMillis();
                if (timeRemaining > 7200000) {
                    registrationOver = false;
                    scheduledStartTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartTWTask(),
                                    timeRemaining - 7200000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 2h before TW start to end registration
                } else if ((timeRemaining <= 7200000) && (timeRemaining > 1200000)) {
                    final SystemMessage sm =
                            new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_REQUEST_PERIOD_HAS_ENDED);
                    World.broadcastToAllOnlinePlayers(sm);
                    registrationOver = true;
                    scheduledStartTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartTWTask(),
                                    timeRemaining - 1200000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 20 mins left before TW start.
                } else if ((timeRemaining <= 1200000) && (timeRemaining > 600000)) {
                    final SystemMessage sm = new SystemMessage(
                            SystemMessageId
                                    .THE_TERRITORY_WAR_WILL_BEGIN_IN_20_MINUTES_TERRITORY_RELATED_FUNCTIONS_I_E_BATTLEFIELD_CHANNEL_DISGUISE_SCROLLS_TRANSFORMATIONS_ETC_CAN_NOW_BE_USED);
                    World.broadcastToAllOnlinePlayers(sm);
                    twChannelOpen = true;
                    registrationOver = true;
                    updatePlayerTWStateFlags(false);
                    scheduledStartTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartTWTask(),
                                    timeRemaining - 600000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 10 mins left before TW start.
                } else if ((timeRemaining <= 600000) && (timeRemaining > 300000)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_BEGINS_IN_10_MINUTES);
                    World.broadcastToAllOnlinePlayers(sm);
                    twChannelOpen = true;
                    registrationOver = true;
                    updatePlayerTWStateFlags(false);
                    scheduledStartTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartTWTask(),
                                    timeRemaining - 300000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 5 mins left before TW start.
                } else if ((timeRemaining <= 300000) && (timeRemaining > 60000)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_BEGINS_IN_5_MINUTES);
                    World.broadcastToAllOnlinePlayers(sm);
                    twChannelOpen = true;
                    registrationOver = true;
                    updatePlayerTWStateFlags(false);
                    scheduledStartTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartTWTask(),
                                    timeRemaining - 60000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 1 min left before TW start.
                } else if ((timeRemaining <= 60000) && (timeRemaining > 0)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_BEGINS_IN_1_MINUTE);
                    World.broadcastToAllOnlinePlayers(sm);
                    twChannelOpen = true;
                    registrationOver = true;
                    updatePlayerTWStateFlags(false);
                    scheduledStartTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartTWTask(),
                                    timeRemaining,
                                    TimeUnit.MILLISECONDS); // Prepare task for TW start.
                } else if ((timeRemaining + WARLENGTH) > 0) {
                    twChannelOpen = true;
                    registrationOver = true;
                    startTerritoryWar();
                    scheduledEndTWTask = WorldPartition.loop()
                            .schedule(new ScheduleEndTWTask(), 1000, TimeUnit.MILLISECONDS); // Prepare task for TW end.
                    scheduledRewardOnlineTask = WorldPartition.loop()
                            .scheduleAtFixedRate(new RewardOnlineParticipants(), 60000, 60000, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    private class ScheduleEndTWTask implements Runnable {

        protected ScheduleEndTWTask() {}

        @Override
        public void run() {
            try {
                scheduledEndTWTask.cancel(false);
                final long timeRemaining = (twStart.getTimeInMillis() + WARLENGTH) - ClockBridge.wallMillis();
                if (timeRemaining > 3600000) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_WILL_END_IN_S1_HOUR_S);
                    sm.addInt(2);
                    announceToParticipants(sm, 0, 0);
                    scheduledEndTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleEndTWTask(),
                                    timeRemaining - 3600000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 1 hr left.
                } else if ((timeRemaining <= 3600000) && (timeRemaining > 600000)) {
                    final SystemMessage sm =
                            new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_WILL_END_IN_S1_MINUTE_S);
                    sm.addInt((int) (timeRemaining / 60000));
                    announceToParticipants(sm, 0, 0);
                    scheduledEndTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleEndTWTask(),
                                    timeRemaining - 600000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 10 minute left.
                } else if ((timeRemaining <= 600000) && (timeRemaining > 300000)) {
                    final SystemMessage sm =
                            new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_WILL_END_IN_S1_MINUTE_S);
                    sm.addInt((int) (timeRemaining / 60000));
                    announceToParticipants(sm, 0, 0);
                    scheduledEndTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleEndTWTask(),
                                    timeRemaining - 300000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 5 minute left.
                } else if ((timeRemaining <= 300000) && (timeRemaining > 10000)) {
                    final SystemMessage sm =
                            new SystemMessage(SystemMessageId.THE_TERRITORY_WAR_WILL_END_IN_S1_MINUTE_S);
                    sm.addInt((int) (timeRemaining / 60000));
                    announceToParticipants(sm, 0, 0);
                    scheduledEndTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleEndTWTask(),
                                    timeRemaining - 10000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 10 seconds count down
                } else if ((timeRemaining <= 10000) && (timeRemaining > 0)) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S1_SECOND_S_TO_THE_END_OF_TERRITORY_WAR);
                    sm.addInt((int) (timeRemaining / 1000));
                    announceToParticipants(sm, 0, 0);
                    scheduledEndTWTask = WorldPartition.loop()
                            .schedule(
                                    new ScheduleEndTWTask(),
                                    timeRemaining,
                                    TimeUnit.MILLISECONDS); // Prepare task for second count down
                } else {
                    endTerritoryWar();
                    // _scheduledStartTWTask = ThreadPoolManager.schedule(new ScheduleStartTWTask(), 1000);
                    WorldPartition.loop().schedule(new closeTerritoryChannelTask(), 600000, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    private class closeTerritoryChannelTask implements Runnable {
        public closeTerritoryChannelTask() {}

        @Override
        public void run() {
            twChannelOpen = false;
            disguisedPlayers.clear();
            updatePlayerTWStateFlags(true);
        }
    }

    public void announceToParticipants(ServerPacket sm, int exp, int sp) {
        // broadcast to clan members
        for (Territory ter : territoryList.values()) {
            if (ter.getOwnerClan() != null) {
                for (Player member : ter.getOwnerClan().getOnlineMembers(0)) {
                    member.sendPacket(sm);
                    if ((exp > 0) || (sp > 0)) {
                        member.addExpAndSp(exp, sp);
                    }
                }
            }
        }

        for (List<Clan> list : registeredClans.values()) {
            for (Clan c : list) {
                for (Player member : c.getOnlineMembers(0)) {
                    member.sendPacket(sm);
                    if ((exp > 0) || (sp > 0)) {
                        member.addExpAndSp(exp, sp);
                    }
                }
            }
        }

        // broadcast to mercenaries
        for (List<Integer> list : registeredMercenaries.values()) {
            for (int objId : list) {
                final Player player = World.getPlayer(objId);
                if ((player != null) && ((player.getClan() == null) || !checkIsRegistered(-1, player.getClan()))) {
                    player.sendPacket(sm);
                    if ((exp > 0) || (sp > 0)) {
                        player.addExpAndSp(exp, sp);
                    }
                }
            }
        }
    }

    public static class TerritoryNPCSpawn {
        @Getter
        private final Location location;

        @Getter
        protected int id;

        @Getter
        private final int castleId;

        @Getter
        private final int type;

        @Getter
        private Npc npc;

        public TerritoryNPCSpawn(int castleId, Location loc, int npcId, int type, Npc npc) {
            this.castleId = castleId;
            location = loc;
            id = npcId;
            this.type = type;
            this.npc = npc;
        }

        public void setNPC(Npc npc) {
            if (this.npc != null) {
                this.npc.deleteMe();
            }

            this.npc = npc;
        }
    }

    public class Territory {

        @Getter
        private final int territoryId;

        @Getter
        private final int castleId; // territory Castle

        @Getter
        protected int fortId; // territory Fortress

        @Getter
        @Setter
        private Clan ownerClan;

        @Getter
        private final List<TerritoryNPCSpawn> spawnList = new ArrayList<>();

        @Getter
        private final TerritoryNPCSpawn[] ownedWard;

        @Getter
        @Setter
        private boolean inProgress = false;

        private SiegeFlag hq = null;

        @Getter
        private final int[] questDone;

        public Territory(int castleId) {
            this.castleId = castleId;
            territoryId = castleId + 80;
            ownedWard = new TerritoryNPCSpawn[9];
            questDone = new int[2];
        }

        protected void addWardSpawnPlace(Location loc) {
            for (int i = 0; i < ownedWard.length; i++) {
                if (ownedWard[i] == null) {
                    ownedWard[i] = new TerritoryNPCSpawn(castleId, loc, 0, 4, null);
                    return;
                }
            }
        }

        protected TerritoryNPCSpawn getFreeWardSpawnPlace() {
            for (TerritoryNPCSpawn territoryWardSpawnPlace : ownedWard) {
                if ((territoryWardSpawnPlace != null) && (territoryWardSpawnPlace.getNpc() == null)) {
                    return territoryWardSpawnPlace;
                }
            }

            log.warn("No free Ward spawn found for territory: {}", territoryId);
            for (int i = 0; i < ownedWard.length; i++) {
                if (ownedWard[i] == null) {
                    log.warn("Territory ward spawn place {} is null!", i);
                } else if (ownedWard[i].getNpc() != null) {
                    log.warn(
                            "Territory ward spawn place {} has npc name: {}",
                            i,
                            ownedWard[i].getNpc().getName());
                } else {
                    log.warn("Territory ward spawn place {} is empty!", i);
                }
            }

            return null;
        }

        protected void changeNPCsSpawn(int type, boolean isSpawn) {
            if ((type < 0) || (type > 3)) {
                log.warn("Wrong type({}) for NPCs spawn change!", type);
                return;
            }

            for (TerritoryNPCSpawn twSpawn : spawnList) {
                if (twSpawn.getType() != type) {
                    continue;
                }

                if (isSpawn) {
                    twSpawn.setNPC(spawnNPC(twSpawn.getId(), twSpawn.getLocation()));
                } else {
                    final Npc npc = twSpawn.getNpc();
                    if ((npc != null) && !npc.isDead()) {
                        npc.deleteMe();
                    }

                    twSpawn.setNPC(null);
                }
            }
        }

        protected void removeWard(int wardId) {
            for (TerritoryNPCSpawn wardSpawn : ownedWard) {
                if (wardSpawn.getId() == wardId) {
                    wardSpawn.getNpc().deleteMe();
                    wardSpawn.setNPC(null);
                    wardSpawn.id = 0;
                    return;
                }
            }

            log.warn("Can't delete wardId: {} for territory: {}", wardId, territoryId);
        }

        public void setHQ(SiegeFlag hq) {
            this.hq = hq;
        }

        public SiegeFlag getHQ() {
            return hq;
        }

        public List<Integer> getOwnedWardIds() {
            final List<Integer> ret = new ArrayList<>();
            for (TerritoryNPCSpawn wardSpawn : ownedWard) {
                if (wardSpawn.getId() > 0) {
                    ret.add(wardSpawn.getId());
                }
            }

            return ret;
        }
    }

    @Override
    public void startSiege() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void endSiege() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SiegeClan getAttackerClan(int clanId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SiegeClan getAttackerClan(Clan clan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<SiegeClan> getAttackerClans() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Player> getAttackersInZone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkIsAttacker(Clan clan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SiegeClan getDefenderClan(int clanId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SiegeClan getDefenderClan(Clan clan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<SiegeClan> getDefenderClans() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkIsDefender(Clan clan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Npc> getFlag(Clan clan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Calendar getSiegeDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean giveFame() {
        return true;
    }

    @Override
    public int getFameFrequency() {
        return PlayerConfig.CASTLE_ZONE_FAME_TASK_FREQUENCY;
    }

    @Override
    public int getFameAmount() {
        return PlayerConfig.CASTLE_ZONE_FAME_AQUIRE_POINTS;
    }

    @Override
    public void updateSiege() {}

    // TODO: callers receive the bean through their constructor (#41).
    public static TerritoryWarManager getInstance() {
        final TerritoryWarManager current = instance;
        if (current == null) {
            throw new IllegalStateException("TerritoryWarManager is used before the Spring context created it.");
        }
        return current;
    }
}
