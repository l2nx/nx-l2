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
package app.l2nx.highfive.mechanics.siege;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.FortCommander;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeGuardManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.sieges.fort.OnFortSiegeFinish;
import app.l2nx.highfive.mechanics.events.holders.sieges.fort.OnFortSiegeStart;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FortSiege implements Siegable {

    public class ScheduleEndSiegeTask implements Runnable {
        @Override
        public void run() {
            if (!inProgress) {
                return;
            }

            try {
                siegeEnd = null;
                endSiege();
            } catch (Exception e) {
                log.warn("Exception: ScheduleEndSiegeTask() for Fort: {} {}", fort.getName(), e.getMessage(), e);
            }
        }
    }

    public class ScheduleStartSiegeTask implements Runnable {
        private final Fort fortInst;
        private final int time;

        public ScheduleStartSiegeTask(int time) {
            fortInst = fort;
            this.time = time;
        }

        @Override
        public void run() {
            if (inProgress) {
                return;
            }

            try {
                final SystemMessage sm;
                if (time == 3600) // 1hr remains
                {
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(600),
                                    3000000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 10 minutes left.
                } else if (time == 600) // 10min remains
                {
                    fort.despawnSuspiciousMerchant();
                    sm = new SystemMessage(SystemMessageId.S1_MINUTE_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(10);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(300),
                                    300000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 5 minutes left.
                } else if (time == 300) // 5min remains
                {
                    sm = new SystemMessage(SystemMessageId.S1_MINUTE_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(5);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(60),
                                    240000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 1 minute left.
                } else if (time == 60) // 1min remains
                {
                    sm = new SystemMessage(SystemMessageId.S1_MINUTE_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(1);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(30),
                                    30000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 30 seconds left.
                } else if (time == 30) // 30seconds remains
                {
                    sm = new SystemMessage(SystemMessageId.S1_SECOND_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(30);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(10),
                                    20000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 10 seconds left.
                } else if (time == 10) // 10seconds remains
                {
                    sm = new SystemMessage(SystemMessageId.S1_SECOND_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(10);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(5),
                                    5000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 5 seconds left.
                } else if (time == 5) // 5seconds remains
                {
                    sm = new SystemMessage(SystemMessageId.S1_SECOND_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(5);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(1),
                                    4000,
                                    TimeUnit.MILLISECONDS); // Prepare task for 1 seconds left.
                } else if (time == 1) // 1seconds remains
                {
                    sm = new SystemMessage(SystemMessageId.S1_SECOND_S_UNTIL_THE_FORTRESS_BATTLE_STARTS);
                    sm.addInt(1);
                    announceToPlayer(sm);
                    WorldPartition.loop()
                            .schedule(
                                    new ScheduleStartSiegeTask(0),
                                    1000,
                                    TimeUnit.MILLISECONDS); // Prepare task start siege.
                } else if (time == 0) // start siege
                {
                    fortInst.getSiege().startSiege();
                } else {
                    log.warn("Exception: ScheduleStartSiegeTask(): unknown siege time: {}", time);
                }
            } catch (Exception e) {
                log.warn("Exception: ScheduleStartSiegeTask() for Fort: {} {}", fortInst.getName(), e.getMessage(), e);
            }
        }
    }

    public class ScheduleSuspiciousMerchantSpawn implements Runnable {
        @Override
        public void run() {
            if (inProgress) {
                return;
            }

            try {
                fort.spawnSuspiciousMerchant();
            } catch (Exception e) {
                log.warn(
                        "Exception: ScheduleSuspicoiusMerchantSpawn() for Fort: {} {}",
                        fort.getName(),
                        e.getMessage(),
                        e);
            }
        }
    }

    public class ScheduleSiegeRestore implements Runnable {
        @Override
        public void run() {
            if (!inProgress) {
                return;
            }

            try {
                siegeRestore = null;
                resetSiege();
                announceToPlayer(new SystemMessage(SystemMessageId.THE_BARRACKS_FUNCTION_HAS_BEEN_RESTORED));
            } catch (Exception e) {
                log.warn("Exception: ScheduleSiegeRestore() for Fort: {} {}", fort.getName(), e.getMessage(), e);
            }
        }
    }

    @Getter
    private final Collection<SiegeClan> attackerClans = ConcurrentHashMap.newKeySet();

    // Fort setting
    @Getter
    protected Collection<Spawn> commanders = ConcurrentHashMap.newKeySet();

    @Getter
    protected final Fort fort;

    @Getter
    boolean inProgress = false;

    private FortSiegeGuardManager siegeGuardManager;
    ScheduledFuture<?> siegeEnd = null;
    ScheduledFuture<?> siegeRestore = null;
    ScheduledFuture<?> siegeStartTask = null;

    public FortSiege(Fort fort) {
        this.fort = fort;
        checkAutoTask();
        FortSiegeManager.getInstance().addSiege(this);
    }

    /**
     * When siege ends.
     */
    @Override
    public void endSiege() {
        if (!inProgress) {
            return;
        }

        inProgress = false; // Flag so that siege instance can be started.
        removeFlags(); // Removes all flags. Note: Remove flag before teleporting players.
        unSpawnFlags();

        updatePlayerSiegeStateFlags(true);

        final int ownerId = fort.getOwnerClan() != null ? fort.getOwnerClan().getId() : -1;
        fort.getZone().banishForeigners(ownerId);
        fort.getZone().setActive(false);
        fort.getZone().updateZoneStatusForCharactersInside();
        fort.getZone().setSiegeInstance(null);

        saveFortSiege(); // Save fort specific data.
        clearSiegeClan(); // Clear siege clan from db.
        removeCommanders(); // Remove commander from this fort.
        fort.spawnNpcCommanders(); // Spawn NPC commanders
        getSiegeGuardManager().unspawnSiegeGuard(); // Remove all spawned siege guard from this fort.
        fort.resetDoors(); // Respawn door to fort
        WorldPartition.loop()
                .schedule(
                        new ScheduleSuspiciousMerchantSpawn(),
                        FortSiegeManager.getInstance().getSuspiciousMerchantRespawnDelay() * 60 * 1000,
                        TimeUnit.MILLISECONDS); // Prepare 3hr task for suspicious merchant respawn.
        setSiegeDateTime(true); // Store suspicious merchant spawn in DB.
        if (siegeEnd != null) {
            siegeEnd.cancel(true);
            siegeEnd = null;
        }

        if (siegeRestore != null) {
            siegeRestore.cancel(true);
            siegeRestore = null;
        }

        if ((fort.getOwnerClan() != null) && (fort.getFlagPole().getMeshIndex() == 0)) {
            fort.setVisibleFlag(true);
        }

        log.info("Siege of {} fort finished.", fort.getName());

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_FORT_SIEGE_FINISH, getFort())) {
            EventDispatcher.getInstance().notifyEventAsync(new OnFortSiegeFinish(this), getFort());
        }
    }

    /**
     * When siege starts
     */
    @Override
    public void startSiege() {
        if (inProgress) {
            return;
        }

        if (siegeStartTask != null) // used admin command "admin_startfortsiege"
        {
            siegeStartTask.cancel(true);
            fort.despawnSuspiciousMerchant();
        }

        siegeStartTask = null;
        if (attackerClans.isEmpty()) {
            return;
        }

        inProgress = true; // Flag so that same siege instance cannot be started again.
        loadSiegeClan(); // Load siege clan from db.
        updatePlayerSiegeStateFlags(false);
        teleportPlayer(FortTeleportWhoType.Attacker, TeleportWhereType.TOWN); // Teleport to the closest town.
        fort.despawnNpcCommanders(); // Despawn NPC commanders
        spawnCommanders(); // Spawn commanders
        fort.resetDoors(); // Spawn door
        spawnSiegeGuard(); // Spawn siege guard
        fort.setVisibleFlag(false);
        fort.getZone().setSiegeInstance(this);
        fort.getZone().setActive(true);
        fort.getZone().updateZoneStatusForCharactersInside();

        // Schedule a task to prepare auto siege end.
        siegeEnd = WorldPartition.loop()
                .schedule(
                        new ScheduleEndSiegeTask(),
                        FortSiegeManager.getInstance().getSiegeLength() * 60 * 1000,
                        TimeUnit.MILLISECONDS); // Prepare auto end task.

        final SystemMessage sm = new SystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_S1_HAS_BEGUN);
        sm.addCastleId(fort.getResidenceId());
        announceToPlayer(sm);
        saveFortSiege();

        log.info("Siege of {} fort started.", fort.getName());

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_FORT_SIEGE_START, getFort())) {
            EventDispatcher.getInstance().notifyEventAsync(new OnFortSiegeStart(this), getFort());
        }
    }

    /**
     * Announce to player.
     * @param sm the system message to send to player
     */
    public void announceToPlayer(SystemMessage sm) {
        // Announce messages only for participants.
        Clan clan;
        for (SiegeClan siegeclan : attackerClans) {
            clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
            for (Player member : clan.getOnlineMembers(0)) {
                member.sendPacket(sm);
            }
        }

        if (fort.getOwnerClan() == null) {
            return;
        }

        clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getId());
        for (Player member : clan.getOnlineMembers(0)) {
            if (member != null) {
                member.sendPacket(sm);
            }
        }
    }

    public void announceToPlayer(SystemMessage sm, String s) {
        sm.addString(s);
        announceToPlayer(sm);
    }

    public void updatePlayerSiegeStateFlags(boolean clear) {
        Clan clan;
        for (SiegeClan siegeclan : attackerClans) {
            clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
            for (Player member : clan.getOnlineMembers(0)) {
                if (clear) {
                    member.setSiegeState((byte) 0);
                    member.setSiegeSide(0);
                    member.setInSiege(false);
                    member.stopFameTask();
                } else {
                    member.setSiegeState((byte) 1);
                    member.setSiegeSide(fort.getResidenceId());
                    if (checkIfInZone(member)) {
                        member.setInSiege(true);
                        member.startFameTask(
                                PlayerConfig.FORTRESS_ZONE_FAME_TASK_FREQUENCY * 1000,
                                PlayerConfig.FORTRESS_ZONE_FAME_AQUIRE_POINTS);
                    }
                }

                member.broadcastUserInfo();
            }
        }

        if (fort.getOwnerClan() == null) {
            return;
        }

        clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getId());
        for (Player member : clan.getOnlineMembers(0)) {
            if (member == null) {
                continue;
            }

            if (clear) {
                member.setSiegeState((byte) 0);
                member.setSiegeSide(0);
                member.setInSiege(false);
                member.stopFameTask();
            } else {
                member.setSiegeState((byte) 2);
                member.setSiegeSide(fort.getResidenceId());
                if (checkIfInZone(member)) {
                    member.setInSiege(true);
                    member.startFameTask(
                            PlayerConfig.FORTRESS_ZONE_FAME_TASK_FREQUENCY * 1000,
                            PlayerConfig.FORTRESS_ZONE_FAME_AQUIRE_POINTS);
                }
            }

            member.broadcastUserInfo();
        }
    }

    /**
     * @param object
     * @return true if object is inside the zone
     */
    public boolean checkIfInZone(WorldObject object) {
        return checkIfInZone(object.getX(), object.getY(), object.getZ());
    }

    /**
     * @param x
     * @param y
     * @param z
     * @return true if object is inside the zone
     */
    public boolean checkIfInZone(int x, int y, int z) {
        return inProgress && fort.checkIfInZone(x, y, z); // Fort zone during siege
    }

    /**
     * @param clan The Clan of the player
     * @return true if clan is attacker
     */
    @Override
    public boolean checkIsAttacker(Clan clan) {
        return getAttackerClan(clan) != null;
    }

    /**
     * @param clan The Clan of the player
     * @return true if clan is defender
     */
    @Override
    public boolean checkIsDefender(Clan clan) {
        return (clan != null) && (fort.getOwnerClan() == clan);
    }

    /** Clear all registered siege clans from database for fort */
    public void clearSiegeClan() {
        FortSiegeManager.getInstance()
                .deleteSiegeClans(
                        fort.getResidenceId(),
                        fort.getOwnerClan() != null ? fort.getOwnerClan().getId() : 0);
        attackerClans.clear();

        // if siege is in progress, end siege
        if (inProgress) {
            endSiege();
        }

        // if siege isn't in progress (1hr waiting time till siege starts), cancel waiting time
        if (siegeStartTask != null) {
            siegeStartTask.cancel(true);
            siegeStartTask = null;
        }
    }

    /** Set the date for the next siege. */
    private void clearSiegeDate() {
        fort.getSiegeDate().setTimeInMillis(0);
    }

    /**
     * @return list of Player registered as attacker in the zone.
     */
    @Override
    public List<Player> getAttackersInZone() {
        final List<Player> players = new ArrayList<>();
        for (SiegeClan siegeclan : attackerClans) {
            for (Player player :
                    ClanTable.getInstance().getClan(siegeclan.getClanId()).getOnlineMembers(0)) {
                if (player.isInSiege()) {
                    players.add(player);
                }
            }
        }

        return players;
    }

    /**
     * @return list of Player in the zone.
     */
    public List<Player> getPlayersInZone() {
        return fort.getZone().getPlayersInside();
    }

    /**
     * @return list of Player owning the fort in the zone.
     */
    public List<Player> getOwnersInZone() {
        final List<Player> players = new ArrayList<>();
        if (fort.getOwnerClan() != null) {
            final Clan clan =
                    ClanTable.getInstance().getClan(getFort().getOwnerClan().getId());
            if (clan != fort.getOwnerClan()) {
                return null;
            }

            for (Player player : clan.getOnlineMembers(0)) {
                if (player.isInSiege()) {
                    players.add(player);
                }
            }
        }

        return players;
    }

    /**
     * TODO: To DP AI<br>
     * Commander was killed
     * @param instance
     */
    public void killedCommander(FortCommander instance) {
        if (this.commanders.isEmpty() || (fort == null)) {
            return;
        }

        final Spawn spawn = instance.getSpawn();
        if (spawn != null) {
            final List<FortSpawnHolder> commanders = FortSiegeManager.getInstance()
                    .getCommanderSpawnList(getFort().getResidenceId());
            for (FortSpawnHolder spawn2 : commanders) {
                if (spawn2.getId() == spawn.getId()) {
                    NpcStringId npcString = null;
                    switch (spawn2.getMessageId()) {
                        case 1: {
                            npcString =
                                    NpcStringId
                                            .YOU_MAY_HAVE_BROKEN_OUR_ARROWS_BUT_YOU_WILL_NEVER_BREAK_OUR_WILL_ARCHERS_RETREAT;
                            break;
                        }
                        case 2: {
                            npcString = NpcStringId.AIIEEEE_COMMAND_CENTER_THIS_IS_GUARD_UNIT_WE_NEED_BACKUP_RIGHT_AWAY;
                            break;
                        }
                        case 3: {
                            npcString =
                                    NpcStringId
                                            .AT_LAST_THE_MAGIC_FIELD_THAT_PROTECTS_THE_FORTRESS_HAS_WEAKENED_VOLUNTEERS_STAND_BACK;
                            break;
                        }
                        case 4: {
                            npcString = NpcStringId
                                    .I_FEEL_SO_MUCH_GRIEF_THAT_I_CAN_T_EVEN_TAKE_CARE_OF_MYSELF_THERE_ISN_T_ANY_REASON_FOR_ME_TO_STAY_HERE_ANY_LONGER;
                            break;
                        }
                    }

                    if (npcString != null) {
                        instance.broadcastPacket(
                                new NpcSay(instance.getObjectId(), ChatType.NPC_SHOUT, instance.getId(), npcString));
                    }
                }
            }

            this.commanders.remove(spawn);
            if (this.commanders.isEmpty()) {
                // spawn fort flags
                spawnFlag(fort.getResidenceId());

                // cancel door/commanders respawn
                if (siegeRestore != null) {
                    siegeRestore.cancel(true);
                }

                // open doors in main building
                for (Door door : fort.getDoors()) {
                    if (door.isShowHp()) {
                        continue;
                    }

                    // TODO this also opens control room door at big fort
                    door.openMe();
                }

                fort.getSiege().announceToPlayer(new SystemMessage(SystemMessageId.ALL_BARRACKS_ARE_OCCUPIED));
            } else {
                fort.getSiege().announceToPlayer(new SystemMessage(SystemMessageId.THE_BARRACKS_HAVE_BEEN_SEIZED));
                if (siegeRestore == null) {
                    siegeRestore = WorldPartition.loop()
                            .schedule(
                                    new ScheduleSiegeRestore(),
                                    FortSiegeManager.getInstance().getCountDownLength() * 60 * 1000,
                                    TimeUnit.MILLISECONDS);
                }
            }
        } else {
            log.warn(
                    "FortSiege.killedCommander(): killed commander, but commander not registered for fortress. NpcId:"
                            + " {} FortId: {}",
                    instance.getId(),
                    fort.getResidenceId());
        }
    }

    /**
     * Remove the flag that was killed
     * @param flag
     */
    public void killedFlag(Npc flag) {
        if (flag == null) {
            return;
        }

        for (SiegeClan clan : attackerClans) {
            if (clan.removeFlag(flag)) {
                return;
            }
        }
    }

    /**
     * Register clan as attacker.
     * @param player The Player of the player trying to register.
     * @param checkConditions True if should be checked conditions, false otherwise
     * @return Number that defines what happened.<br>
     *         0 - Player don't have clan.<br>
     *         1 - Player don't have enough adena to register.<br>
     *         2 - Is not right time to register Fortress now.<br>
     *         3 - Players clan is already registered to siege.<br>
     *         4 - Players clan is successfully registered to siege.
     */
    public int addAttacker(Player player, boolean checkConditions) {
        final Clan clan = player.getClan();
        if (clan == null) {
            return 0; // Player does not have clan.
        }

        if (checkConditions) {
            if (this.fort.getSiege().getAttackerClans().isEmpty()
                    && (player.getInventory().getAdena() < 250000)) {
                return 1; // Player does not havee enough adena to register.
            } else if ((ClockBridge.wallMillis()
                            < TerritoryWarManager.getInstance().getTWStartTimeInMillis())
                    && TerritoryWarManager.getInstance().isRegistrationOver()) {
                return 2; // Is not right time to register Fortress now.
            }

            if ((ClockBridge.wallMillis() > TerritoryWarManager.getInstance().getTWStartTimeInMillis())
                    && TerritoryWarManager.getInstance().isTWChannelOpen()) {
                return 2; // Is not right time to register Fortress now.
            }

            for (Fort fort : FortManager.getInstance().getForts()) {
                if (fort.getSiege().getAttackerClan(player.getClanId()) != null) {
                    return 3; // Players clan is already registred to siege.
                }

                if ((fort.getOwnerClan() == clan)
                        && (fort.getSiege().isInProgress() || (fort.getSiege().siegeStartTask != null))) {
                    return 3; // Players clan is already registred to siege.
                }
            }
        }

        saveSiegeClan(clan);
        if (attackerClans.size() == 1) {
            if (checkConditions) {
                player.reduceAdena(ItemProcessType.FEE, 250000, null, true);
            }

            startAutoTask(true);
        }

        return 4; // Players clan is successfully registered to siege.
    }

    /**
     * Remove clan from siege
     * @param clan The clan being removed
     */
    public void removeAttacker(Clan clan) {
        if ((clan == null)
                || (clan.getFortId() == getFort().getResidenceId())
                || !FortSiegeManager.getInstance()
                        .checkIsRegistered(clan, getFort().getResidenceId())) {
            return;
        }

        removeSiegeClan(clan.getId());
    }

    /**
     * This function does not do any checks and should not be called from bypass !
     * @param clanId
     */
    private void removeSiegeClan(int clanId) {
        if (clanId != 0) {
            FortSiegeManager.getInstance().deleteSiegeClan(fort.getResidenceId(), clanId);
        } else {
            FortSiegeManager.getInstance().deleteSiegeClans(fort.getResidenceId(), 0);
        }

        loadSiegeClan();
        if (attackerClans.isEmpty()) {
            if (inProgress) {
                endSiege();
            } else {
                saveFortSiege(); // Clear siege time in DB.
            }

            if (siegeStartTask != null) {
                siegeStartTask.cancel(true);
                siegeStartTask = null;
            }
        }
    }

    /**
     * Start the auto tasks
     */
    public void checkAutoTask() {
        if (siegeStartTask != null) {
            return;
        }

        final long delay = getFort().getSiegeDate().getTimeInMillis() - ClockBridge.wallMillis();
        if (delay < 0) {
            // siege time in past
            saveFortSiege();
            clearSiegeClan(); // remove all clans

            // spawn suspicious merchant immediately
            WorldPartition.loop().execute(new ScheduleSuspiciousMerchantSpawn());
        } else {
            loadSiegeClan();
            if (attackerClans.isEmpty()) {
                // no attackers - waiting for suspicious merchant spawn
                WorldPartition.loop().schedule(new ScheduleSuspiciousMerchantSpawn(), delay, TimeUnit.MILLISECONDS);
            } else {
                // preparing start siege task
                if (delay > 3600000) // more than hour, how this can happens ? spawn suspicious merchant
                {
                    WorldPartition.loop().execute(new ScheduleSuspiciousMerchantSpawn());
                    siegeStartTask = WorldPartition.loop()
                            .schedule(new ScheduleStartSiegeTask(3600), delay - 3600000, TimeUnit.MILLISECONDS);
                }

                if (delay > 600000) // more than 10 min, spawn suspicious merchant
                {
                    WorldPartition.loop().execute(new ScheduleSuspiciousMerchantSpawn());
                    siegeStartTask = WorldPartition.loop()
                            .schedule(new ScheduleStartSiegeTask(600), delay - 600000, TimeUnit.MILLISECONDS);
                } else if (delay > 300000) {
                    siegeStartTask = WorldPartition.loop()
                            .schedule(new ScheduleStartSiegeTask(300), delay - 300000, TimeUnit.MILLISECONDS);
                } else if (delay > 60000) {
                    siegeStartTask = WorldPartition.loop()
                            .schedule(new ScheduleStartSiegeTask(60), delay - 60000, TimeUnit.MILLISECONDS);
                } else {
                    // lower than 1 min, set to 1 min
                    siegeStartTask =
                            WorldPartition.loop().schedule(new ScheduleStartSiegeTask(60), 0, TimeUnit.MILLISECONDS);
                }

                log.info(
                        "Siege of {} fort: {}",
                        fort.getName(),
                        fort.getSiegeDate().getTime());
            }
        }
    }

    /**
     * Start the auto task
     * @param setTime
     */
    public void startAutoTask(boolean setTime) {
        if (siegeStartTask != null) {
            return;
        }

        if (setTime) {
            setSiegeDateTime(false);
        }

        if (fort.getOwnerClan() != null) {
            fort.getOwnerClan().broadcastToOnlineMembers(new SystemMessage(SystemMessageId.A_FORTRESS_IS_UNDER_ATTACK));
        }

        // Execute siege auto start.
        siegeStartTask = WorldPartition.loop().schedule(new ScheduleStartSiegeTask(3600), 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Teleport players
     * @param teleportWho
     * @param teleportWhere
     */
    public void teleportPlayer(FortTeleportWhoType teleportWho, TeleportWhereType teleportWhere) {
        List<Player> players;
        switch (teleportWho) {
            case Owner: {
                players = getOwnersInZone();
                break;
            }
            case Attacker: {
                players = getAttackersInZone();
                break;
            }
            default: {
                players = fort.getZone().getPlayersInside();
            }
        }

        for (Player player : players) {
            if (player.isGM() || player.isJailed()) {
                continue;
            }

            player.teleToLocation(teleportWhere);
        }
    }

    /**
     * Add clan as attacker<
     * @param clanId
     */
    private void addAttacker(int clanId) {
        attackerClans.add(new SiegeClan(clanId, SiegeClanType.ATTACKER)); // Add registered attacker to attacker list.
    }

    /**
     * @param clan
     * @return {@code true} if the clan has already registered to a siege for the same day, {@code false} otherwise.
     */
    public boolean checkIfAlreadyRegisteredForSameDay(Clan clan) {
        for (FortSiege siege : FortSiegeManager.getInstance().getSieges()) {
            if (siege == this) {
                continue;
            }

            if (siege.getSiegeDate().get(Calendar.DAY_OF_WEEK) == getSiegeDate().get(Calendar.DAY_OF_WEEK)) {
                if (siege.checkIsAttacker(clan)) {
                    return true;
                }

                if (siege.checkIsDefender(clan)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void setSiegeDateTime(boolean merchant) {
        final Calendar newDate = ClockBridge.calendar();
        if (merchant) {
            newDate.add(Calendar.MINUTE, FortSiegeManager.getInstance().getSuspiciousMerchantRespawnDelay());
        } else {
            newDate.add(Calendar.MINUTE, 60);
        }

        fort.setSiegeDate(newDate);
        saveSiegeDate();
    }

    /** Load siege clans. */
    private void loadSiegeClan() {
        attackerClans.clear();
        for (int clanId : FortSiegeManager.getInstance().findSiegeClanIds(fort.getResidenceId())) {
            addAttacker(clanId);
        }
    }

    /** Remove commanders. */
    private void removeCommanders() {
        if ((commanders == null) || commanders.isEmpty()) {
            return;
        }

        // Remove all instance of commanders for this fort.
        for (Spawn spawn : commanders) {
            if (spawn != null) {
                spawn.stopRespawn();
                if (spawn.getLastSpawn() != null) {
                    spawn.getLastSpawn().deleteMe();
                }
            }
        }

        commanders.clear();
    }

    /** Remove all flags. */
    private void removeFlags() {
        for (SiegeClan sc : attackerClans) {
            if (sc != null) {
                sc.removeFlags();
            }
        }
    }

    /** Save fort siege related to database. */
    private void saveFortSiege() {
        clearSiegeDate(); // clear siege date
        saveSiegeDate(); // Save the new date.
    }

    /** Save siege date to database. */
    private void saveSiegeDate() {
        FortManager.getInstance()
                .saveSiegeDate(fort.getResidenceId(), getFort().getSiegeDate().getTimeInMillis());
    }

    /**
     * Save registration to database.
     * @param clan
     */
    private void saveSiegeClan(Clan clan) {
        if (getAttackerClans().size() >= FortSiegeManager.getInstance().getAttackerMaxClans()) {
            return;
        }

        if (FortSiegeManager.getInstance().saveSiegeClan(fort.getResidenceId(), clan.getId())) {
            addAttacker(clan.getId());
        }
    }

    /** Spawn commanders. */
    private void spawnCommanders() {
        // Set commanders array size if one does not exist.
        try {
            commanders.clear();
            for (FortSpawnHolder sp : FortSiegeManager.getInstance()
                    .getCommanderSpawnList(getFort().getResidenceId())) {
                final Spawn spawnDat = new Spawn(sp.getId());
                spawnDat.setAmount(1);
                spawnDat.setXYZ(sp.getLocation());
                spawnDat.setHeading(sp.getLocation().getHeading());
                spawnDat.setRespawnDelay(60);
                spawnDat.doSpawn(false);
                spawnDat.stopRespawn();
                commanders.add(spawnDat);
            }
        } catch (Exception e) {
            // Problem with initializing spawn, go to next one.
            log.warn("FortSiege.spawnCommander: Spawn could not be initialized: {}", e.getMessage(), e);
        }
    }

    private void spawnFlag(int id) {
        for (CombatFlag cf : FortSiegeManager.getInstance().getFlagList(id)) {
            cf.spawnMe();
        }
    }

    private void unSpawnFlags() {
        if (FortSiegeManager.getInstance().getFlagList(getFort().getResidenceId()) == null) {
            return;
        }

        for (CombatFlag cf :
                FortSiegeManager.getInstance().getFlagList(getFort().getResidenceId())) {
            cf.unSpawnMe();
        }
    }

    /**
     * Spawn siege guard.
     */
    private void spawnSiegeGuard() {
        getSiegeGuardManager().spawnSiegeGuard();
    }

    @Override
    public SiegeClan getAttackerClan(Clan clan) {
        return clan == null ? null : getAttackerClan(clan.getId());
    }

    @Override
    public SiegeClan getAttackerClan(int clanId) {
        for (SiegeClan sc : attackerClans) {
            if ((sc != null) && (sc.getClanId() == clanId)) {
                return sc;
            }
        }

        return null;
    }

    @Override
    public Calendar getSiegeDate() {
        return fort.getSiegeDate();
    }

    @Override
    public Set<Npc> getFlag(Clan clan) {
        if (clan != null) {
            final SiegeClan sc = getAttackerClan(clan);
            if (sc != null) {
                return sc.getFlag();
            }
        }

        return null;
    }

    public FortSiegeGuardManager getSiegeGuardManager() {
        if (siegeGuardManager == null) {
            siegeGuardManager = new FortSiegeGuardManager(fort);
        }

        return siegeGuardManager;
    }

    public void resetSiege() {
        // Reload commanders and repair doors.
        removeCommanders();
        spawnCommanders();
        fort.resetDoors();
    }

    @Override
    public SiegeClan getDefenderClan(int clanId) {
        return null;
    }

    @Override
    public SiegeClan getDefenderClan(Clan clan) {
        return null;
    }

    @Override
    public List<SiegeClan> getDefenderClans() {
        return null;
    }

    @Override
    public boolean giveFame() {
        return true;
    }

    @Override
    public int getFameFrequency() {
        return PlayerConfig.FORTRESS_ZONE_FAME_TASK_FREQUENCY;
    }

    @Override
    public int getFameAmount() {
        return PlayerConfig.FORTRESS_ZONE_FAME_AQUIRE_POINTS;
    }

    @Override
    public void updateSiege() {}
}
