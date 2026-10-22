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

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.holders.FortSpawn;
import app.l2nx.highfive.data.holders.FortTemplate;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.FortData;
import app.l2nx.highfive.data.xml.StaticObjectData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.StaticObject;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.residences.AbstractResidence;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.entity.zone.type.FortZone;
import app.l2nx.highfive.entity.zone.type.SiegeZone;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.residence.model.FortDoorUpgrade;
import app.l2nx.highfive.repository.residence.model.FortState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Fort extends AbstractResidence {

    @Getter
    private final List<Door> doors = new ArrayList<>();

    @Getter
    private StaticObject flagPole = null;

    private FortSiege siege = null;

    @Getter
    @Setter
    private Calendar siegeDate;

    private Calendar lastOwnedTime;
    private SiegeZone zone;

    @Getter
    private Clan ownerClan = null;

    @Getter
    private int fortType = 0;

    @Getter
    private int fortState = 0;

    @Getter
    private int contractedCastleId = 0;

    @Getter
    private int supplyLeveL = 0;

    private final Map<Integer, FortFunction> function;
    private final ScheduledFuture<?>[] fortUpdater = new ScheduledFuture<?>[2];

    // Spawn Data
    private boolean suspiciousMerchantSpawned = false;
    private final Collection<Spawn> siegeNpcs = ConcurrentHashMap.newKeySet();
    private final Collection<Spawn> npcCommanders = ConcurrentHashMap.newKeySet();
    private final Collection<Spawn> specialEnvoys = ConcurrentHashMap.newKeySet();

    private final Map<Integer, Integer> envoyCastles = new HashMap<>(2);
    private final Set<Integer> availableCastles = new HashSet<>(1);

    /** Fortress Functions */
    public static final int FUNC_TELEPORT = 1;

    public static final int FUNC_RESTORE_HP = 2;
    public static final int FUNC_RESTORE_MP = 3;
    public static final int FUNC_RESTORE_EXP = 4;
    public static final int FUNC_SUPPORT = 5;

    public class FortFunction {
        @Getter
        final int type;

        @Getter
        @Setter
        private int level;

        @Getter
        @Setter
        protected int lease;

        protected int tempFee;

        @Getter
        final long rate;

        @Getter
        @Setter
        long endTime;

        protected boolean inDebt;
        public boolean cwh;

        public FortFunction(int type, int level, int lease, int tempLease, long rate, long time, boolean cwh) {
            this.type = type;
            this.level = level;
            this.lease = lease;
            tempFee = tempLease;
            this.rate = rate;
            endTime = time;
            initializeTask(cwh);
        }

        private void initializeTask(boolean cwh) {
            if (ownerClan == null) {
                return;
            }

            final long currentTime = ClockBridge.wallMillis();
            if (endTime > currentTime) {
                WorldPartition.loop().schedule(new FunctionTask(cwh), endTime - currentTime, TimeUnit.MILLISECONDS);
            } else {
                WorldPartition.loop().schedule(new FunctionTask(cwh), 0, TimeUnit.MILLISECONDS);
            }
        }

        private class FunctionTask implements Runnable {
            public FunctionTask(boolean cwh) {
                Fort.FortFunction.this.cwh = cwh;
            }

            @Override
            public void run() {
                try {
                    if (ownerClan == null) {
                        return;
                    }

                    if ((ownerClan.getWarehouse().getAdena() >= lease) || !cwh) {
                        final int fee = endTime == -1 ? tempFee : lease;
                        setEndTime(ClockBridge.wallMillis() + rate);
                        dbSave();
                        if (cwh) {
                            ownerClan
                                    .getWarehouse()
                                    .destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, fee, null, null);
                        }

                        WorldPartition.loop().schedule(new FunctionTask(true), rate, TimeUnit.MILLISECONDS);
                    } else {
                        removeFunction(type);
                    }
                } catch (Throwable t) {
                    // Ignore.
                }
            }
        }

        public void dbSave() {
            FortManager.getInstance()
                    .saveFunction(
                            getResidenceId(),
                            ResidenceFunction.builder()
                                    .type(type)
                                    .level(level)
                                    .lease(lease)
                                    .rate(rate)
                                    .endTime(instantOrNull(endTime))
                                    .build());
        }
    }

    public Fort(int fortId) {
        super(fortId);
        load();
        loadFlagPoles();
        function = new ConcurrentHashMap<>();
        if (ownerClan != null) {
            setVisibleFlag(true);
            loadFunctions();
        }

        initResidenceZone();
        initNpcs(); // load and spawn npcs (Always spawned)
        initSiegeNpcs(); // load suspicious merchants (Despawned 10mins before siege)

        // spawnSuspiciousMerchant(); // spawn suspicious merchants
        initNpcCommanders(); // npc Commanders (not monsters) (Spawned during siege)
        spawnNpcCommanders(); // spawn npc Commanders
        initSpecialEnvoys(); // envoys from castles (Spawned after fort taken)
        if ((ownerClan != null) && (fortState == 0)) {
            spawnSpecialEnvoys();
        }
    }

    /**
     * Return function with id
     * @param type
     * @return
     */
    public FortFunction getFunction(int type) {
        return function.get(type);
    }

    public void endOfSiege(Clan clan) {
        WorldPartition.loop().execute(new endFortressSiege(this, clan));
    }

    /**
     * Move non clan members off fort area and to nearest town.
     */
    public void banishForeigners() {
        getResidenceZone().banishForeigners(ownerClan.getId());
    }

    /**
     * @param x
     * @param y
     * @param z
     * @return true if object is inside the zone
     */
    public boolean checkIfInZone(int x, int y, int z) {
        final SiegeZone zone = getZone();
        return (zone != null) && zone.isInsideZone(x, y, z);
    }

    public SiegeZone getZone() {
        if (this.zone == null) {
            for (SiegeZone zone : ZoneManager.getInstance().getAllZones(SiegeZone.class)) {
                if (zone.getSiegeObjectId() == getResidenceId()) {
                    this.zone = zone;
                    break;
                }
            }
        }

        return this.zone;
    }

    @Override
    public FortZone getResidenceZone() {
        return (FortZone) super.getResidenceZone();
    }

    /**
     * Get the objects distance to this fort
     * @param obj
     * @return
     */
    public double getDistance(WorldObject obj) {
        return getZone().getDistanceToZone(obj);
    }

    public void closeDoor(Player player, int doorId) {
        openCloseDoor(player, doorId, false);
    }

    public void openDoor(Player player, int doorId) {
        openCloseDoor(player, doorId, true);
    }

    public void openCloseDoor(Player player, int doorId, boolean open) {
        if (player.getClan() != ownerClan) {
            return;
        }

        final Door door = getDoor(doorId);
        if (door != null) {
            if (open) {
                door.openMe();
            } else {
                door.closeMe();
            }
        }
    }

    // This method is used to begin removing all fort upgrades.
    public void removeUpgrade() {
        removeDoorUpgrade();
    }

    /**
     * This method will set owner for Fort
     * @param clan
     * @param updateClansReputation
     * @return
     */
    public boolean setOwner(Clan clan, boolean updateClansReputation) {
        if (clan == null) {
            log.warn("Updating Fort owner with null clan!!!");
            return false;
        }

        final SystemMessage sm = new SystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_OF_S1_HAS_FINISHED);
        sm.addCastleId(getResidenceId());
        getSiege().announceToPlayer(sm);

        final Clan oldowner = ownerClan;
        if ((oldowner != null) && (clan != oldowner)) {
            // Remove points from old owner.
            updateClansReputation(oldowner, true);
            try {
                final Player oldleader = oldowner.getLeader().getPlayer();
                if ((oldleader != null) && (oldleader.getMountType() == MountType.WYVERN)) {
                    oldleader.dismount();
                }
            } catch (Exception e) {
                log.warn("Exception in setOwner: {}", e.getMessage(), e);
            }

            if (getSiege().isInProgress()) {
                getSiege().updatePlayerSiegeStateFlags(true);
            }

            removeOwner(true);
        }

        setFortState(0, 0); // initialize fort state

        // if clan already have castle, don't store him in fortress
        if (clan.getCastleId() > 0) {
            getSiege().announceToPlayer(new SystemMessage(SystemMessageId.THE_REBEL_ARMY_RECAPTURED_THE_FORTRESS));

            // The siege is over, the fortress went back to the rebels instead of to the castle owner.
            if (getSiege().isInProgress()) {
                getSiege().endSiege();
            }

            return false;
        }

        // Give points to new owner.
        if (updateClansReputation) {
            updateClansReputation(clan, false);
        }

        spawnSpecialEnvoys();

        // If clan have already fortress, remove it.
        if (clan.getFortId() > 0) {
            FortManager.getInstance().getFortByOwner(clan).removeOwner(true);
        }

        setSupplyLeveL(0);
        setOwnerClan(clan);
        updateOwnerInDB(); // Update in database
        saveFortVariables();

        if (getSiege().isInProgress()) {
            getSiege().endSiege();
        }

        for (Player member : clan.getOnlineMembers(0)) {
            giveResidentialSkills(member);
            member.sendSkillList();
        }

        return true;
    }

    public void removeOwner(boolean updateDB) {
        final Clan clan = ownerClan;
        if (clan != null) {
            for (Player member : clan.getOnlineMembers(0)) {
                removeResidentialSkills(member);
                member.sendSkillList();
            }

            clan.setFortId(0);
            clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
            setOwnerClan(null);
            setSupplyLeveL(0);
            saveFortVariables();
            removeAllFunctions();
            if (updateDB) {
                updateOwnerInDB();
            }
        }
    }

    public void raiseSupplyLeveL() {
        supplyLeveL++;
        if (supplyLeveL > FeatureConfig.FS_MAX_SUPPLY_LEVEL) {
            supplyLeveL = FeatureConfig.FS_MAX_SUPPLY_LEVEL;
        }
    }

    public void setSupplyLeveL(int value) {
        if (value <= FeatureConfig.FS_MAX_SUPPLY_LEVEL) {
            supplyLeveL = value;
        }
    }

    public void saveFortVariables() {
        FortManager.getInstance().saveSupplyLevel(getResidenceId(), supplyLeveL);
    }

    /**
     * Show or hide flag inside flag pole.
     * @param value
     */
    public void setVisibleFlag(boolean value) {
        final StaticObject flagPole = this.flagPole;
        if (flagPole != null) {
            flagPole.setMeshIndex(value ? 1 : 0);
        }
    }

    /**
     * Respawn all doors on fort grounds.
     */
    public void resetDoors() {
        for (Door door : doors) {
            if (door.isOpen()) {
                door.closeMe();
            }

            if (door.isDead()) {
                door.doRevive();
            }

            if (door.getCurrentHp() < door.getMaxHp()) {
                door.setCurrentHp(door.getMaxHp());
            }
        }

        loadDoorUpgrade(); // Check for any upgrade the doors may have.
    }

    // This method upgrade door.
    public void upgradeDoor(int doorId, int hp, int pDef, int mDef) {
        final Door door = getDoor(doorId);
        if (door != null) {
            door.setCurrentHp(door.getMaxHp() + hp);
            saveDoorUpgrade(doorId, hp, pDef, mDef);
        }
    }

    // This method loads fort.
    @Override
    protected void load() {
        final FortTemplate template = FortData.getInstance().getFort(getResidenceId());
        setName(template.name());
        fortType = template.type();

        final FortState state = FortManager.getInstance().findState(getResidenceId());
        siegeDate = ClockBridge.calendar();
        lastOwnedTime = ClockBridge.calendar();
        siegeDate.setTimeInMillis(epochMillisOrZero(state.siegeDate()));
        lastOwnedTime.setTimeInMillis(epochMillisOrZero(state.lastOwnedTime()));
        final int ownerId = state.ownerClanId();
        fortState = state.contractState();
        contractedCastleId = state.contractedCastleId();
        supplyLeveL = state.supplyLevel();

        final Clan clan = ownerId > 0 ? ClanTable.getInstance().getClan(ownerId) : null;
        if (clan != null) {
            clan.setFortId(getResidenceId());
            setOwnerClan(clan);
            final int runCount = getOwnedTime() / (FeatureConfig.FS_UPDATE_FRQ * 60);
            long initial = ClockBridge.wallMillis() - lastOwnedTime.getTimeInMillis();
            while (initial > TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ)) {
                initial -= TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ);
            }

            initial = TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ) - initial;
            if ((FeatureConfig.FS_MAX_OWN_TIME <= 0) || (getOwnedTime() < (FeatureConfig.FS_MAX_OWN_TIME * 3600))) {
                fortUpdater[0] = WorldPartition.loop()
                        .scheduleAtFixedRate(
                                new FortUpdater(this, clan, runCount, FortUpdaterType.PERIODIC_UPDATE),
                                initial,
                                TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ),
                                TimeUnit.MILLISECONDS); // Schedule owner tasks to start running.
                if (FeatureConfig.FS_MAX_OWN_TIME > 0) {
                    fortUpdater[1] = WorldPartition.loop()
                            .scheduleAtFixedRate(
                                    new FortUpdater(this, clan, runCount, FortUpdaterType.MAX_OWN_TIME),
                                    3600000,
                                    3600000,
                                    TimeUnit.MILLISECONDS); // Schedule owner tasks to remove owener.
                }
            } else {
                fortUpdater[1] = WorldPartition.loop()
                        .schedule(
                                new FortUpdater(this, clan, 0, FortUpdaterType.MAX_OWN_TIME),
                                60000,
                                TimeUnit.MILLISECONDS); // Schedule owner tasks to remove owner.
            }
        } else {
            setOwnerClan(null);
            if (ownerId > 0) {
                releaseUnloadedOwner(ownerId);
            }
        }
    }

    /**
     * Leaves the fort as {@link #removeOwner} would when the stored owner clan is not loaded. Runs from the constructor,
     * so it calls no overridable method.
     */
    private void releaseUnloadedOwner(int ownerId) {
        log.warn("Fort {}: owner clan {} is not loaded, the fort is left without an owner.", getResidenceId(), ownerId);
        supplyLeveL = 0;
        FortManager.getInstance().saveSupplyLevel(getResidenceId(), supplyLeveL);
        for (ResidenceFunction stored : FortManager.getInstance().findFunctions(getResidenceId())) {
            FortManager.getInstance().deleteFunction(getResidenceId(), stored.type());
        }

        fortState = 0;
        contractedCastleId = 0;
        updateOwnerInDB();
    }

    /** Load All Functions */
    private void loadFunctions() {
        for (ResidenceFunction stored : FortManager.getInstance().findFunctions(getResidenceId())) {
            function.put(
                    stored.type(),
                    new FortFunction(
                            stored.type(),
                            stored.level(),
                            stored.lease(),
                            0,
                            stored.rate(),
                            epochMillisOrZero(stored.endTime()),
                            true));
        }
    }

    /**
     * Remove function In List and in DB
     * @param functionType
     */
    public void removeFunction(int functionType) {
        function.remove(functionType);
        FortManager.getInstance().deleteFunction(getResidenceId(), functionType);
    }

    /**
     * Remove all fort functions.
     */
    private void removeAllFunctions() {
        for (int id : function.keySet()) {
            removeFunction(id);
        }
    }

    public boolean updateFunctions(Player player, int type, int level, int lease, long rate, boolean addNew) {
        if (player == null) {
            return false;
        }

        if ((lease > 0) && !player.destroyItemByItemId(null, Inventory.ADENA_ID, lease, null, true)) {
            return false;
        }

        if (addNew) {
            function.put(type, new FortFunction(type, level, lease, 0, rate, 0, false));
        } else if ((level == 0) && (lease == 0)) {
            removeFunction(type);
        } else if ((lease - function.get(type).getLease()) > 0) {
            function.remove(type);
            function.put(type, new FortFunction(type, level, lease, 0, rate, -1, false));
        } else {
            function.get(type).setLease(lease);
            function.get(type).setLevel(level);
            function.get(type).dbSave();
        }

        return true;
    }

    public void activateInstance() {
        loadDoor();
    }

    // This method loads fort door data from database.
    private void loadDoor() {
        for (Door door : DoorData.getInstance().getDoors()) {
            if ((door.getFort() != null) && (door.getFort().getResidenceId() == getResidenceId())) {
                doors.add(door);
            }
        }
    }

    private void loadFlagPoles() {
        for (StaticObject obj : StaticObjectData.getInstance().getStaticObjects()) {
            if ((obj.getType() == 3) && obj.getName().startsWith(getName())) {
                flagPole = obj;
                break;
            }
        }

        if (flagPole == null) {
            throw new NullPointerException("Can't find flagpole for Fort " + this);
        }
    }

    // This method loads fort door upgrade data from database.
    private void loadDoorUpgrade() {
        for (FortDoorUpgrade upgrade : FortManager.getInstance().findDoorUpgrades(getResidenceId())) {
            upgradeDoor(upgrade.doorId(), upgrade.hp(), upgrade.pDef(), upgrade.mDef());
        }
    }

    private void removeDoorUpgrade() {
        FortManager.getInstance().deleteDoorUpgrades(getResidenceId());
    }

    private void saveDoorUpgrade(int doorId, int hp, int pDef, int mDef) {
        FortManager.getInstance()
                .saveDoorUpgrade(
                        getResidenceId(),
                        FortDoorUpgrade.builder()
                                .doorId(doorId)
                                .hp(hp)
                                .pDef(pDef)
                                .mDef(mDef)
                                .build());
    }

    private void updateOwnerInDB() {
        final Clan clan = ownerClan;
        int clanId = 0;
        if (clan != null) {
            clanId = clan.getId();
            lastOwnedTime.setTimeInMillis(ClockBridge.wallMillis());
        } else {
            lastOwnedTime.setTimeInMillis(0);
        }

        FortManager.getInstance().saveOwner(getResidenceId(), clanId, lastOwnedTime.getTimeInMillis());

        // Announce to clan members.
        if (clan != null) {
            clan.setFortId(getResidenceId()); // Set has fort flag for new owner.
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_VICTORIOUS_IN_THE_FORTRESS_BATTLE_OF_S2);
            sm.addString(clan.getName());
            sm.addCastleId(getResidenceId());
            World.getPlayers().forEach(p -> p.sendPacket(sm));
            clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
            clan.broadcastToOnlineMembers(new PlaySound(1, "Siege_Victory", 0, 0, 0, 0, 0));
            if (fortUpdater[0] != null) {
                fortUpdater[0].cancel(false);
            }

            if (fortUpdater[1] != null) {
                fortUpdater[1].cancel(false);
            }

            fortUpdater[0] = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new FortUpdater(this, clan, 0, FortUpdaterType.PERIODIC_UPDATE),
                            TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ),
                            TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ),
                            TimeUnit.MILLISECONDS); // Schedule owner tasks to start running.
            if (FeatureConfig.FS_MAX_OWN_TIME > 0) {
                fortUpdater[1] = WorldPartition.loop()
                        .scheduleAtFixedRate(
                                new FortUpdater(this, clan, 0, FortUpdaterType.MAX_OWN_TIME),
                                3600000,
                                3600000,
                                TimeUnit.MILLISECONDS); // Schedule owner tasks to remove owener.
            }
        } else {
            if (fortUpdater[0] != null) {
                fortUpdater[0].cancel(false);
            }

            fortUpdater[0] = null;
            if (fortUpdater[1] != null) {
                fortUpdater[1].cancel(false);
            }

            fortUpdater[1] = null;
        }
    }

    public void setOwnerClan(Clan clan) {
        setVisibleFlag(clan != null);
        ownerClan = clan;
    }

    public Door getDoor(int doorId) {
        if (doorId <= 0) {
            return null;
        }

        for (Door door : doors) {
            if (door.getId() == doorId) {
                return door;
            }
        }

        return null;
    }

    public FortSiege getSiege() {
        if (siege == null) {
            synchronized (this) {
                if (siege == null) {
                    siege = new FortSiege(this);
                }
            }
        }

        return siege;
    }

    public int getOwnedTime() {
        return lastOwnedTime.getTimeInMillis() == 0
                ? 0
                : (int) ((ClockBridge.wallMillis() - lastOwnedTime.getTimeInMillis()) / 1000);
    }

    public int getTimeTillRebelArmy() {
        return lastOwnedTime.getTimeInMillis() == 0
                ? 0
                : (int) (((lastOwnedTime.getTimeInMillis() + (FeatureConfig.FS_MAX_OWN_TIME * 3600000))
                                - ClockBridge.wallMillis())
                        / 1000);
    }

    public long getTimeTillNextFortUpdate() {
        return fortUpdater[0] == null ? 0 : fortUpdater[0].getDelay(TimeUnit.SECONDS);
    }

    public void updateClansReputation(Clan owner, boolean removePoints) {
        if (owner != null) {
            if (removePoints) {
                owner.takeReputationScore(FeatureConfig.LOOSE_FORT_POINTS);
            } else {
                owner.addReputationScore(FeatureConfig.TAKE_FORT_POINTS);
            }
        }
    }

    @Slf4j
    private static class endFortressSiege implements Runnable {
        private final Fort f;
        private final Clan clan;

        public endFortressSiege(Fort f, Clan clan) {
            this.f = f;
            this.clan = clan;
        }

        @Override
        public void run() {
            try {
                f.setOwner(clan, true);
            } catch (Exception e) {
                log.warn("Exception in endFortressSiege {}", e.getMessage(), e);
            }
        }
    }

    /**
     * @param state
     *            <ul>
     *            <li>0 - not decided yet</li>
     *            <li>1 - independent</li>
     *            <li>2 - contracted with castle</li>
     *            </ul>
     * @param castleId the Id of the contracted castle (0 if no contract with any castle)
     */
    public void setFortState(int state, int castleId) {
        fortState = state;
        contractedCastleId = castleId;
        FortManager.getInstance().saveContract(getResidenceId(), fortState, contractedCastleId);
    }

    /**
     * @param npcId the Id of the ambassador NPC
     * @return the Id of the castle this ambassador represents
     */
    public int getCastleIdByAmbassador(int npcId) {
        if ((envoyCastles == null) || !envoyCastles.containsKey(npcId)) {
            return -1;
        }

        return envoyCastles.get(npcId);
    }

    /**
     * @param npcId the Id of the ambassador NPC
     * @return the castle this ambassador represents
     */
    public Castle getCastleByAmbassador(int npcId) {
        return CastleManager.getInstance().getCastleById(getCastleIdByAmbassador(npcId));
    }

    /**
     * @return the castle contracted with this fortress ({@code null} if no contract with any castle)
     */
    public Castle getContractedCastle() {
        return CastleManager.getInstance().getCastleById(getContractedCastleId());
    }

    /**
     * Check if this is a border fortress (associated with multiple castles).
     * @return {@code true} if this is a border fortress (associated with more than one castle), {@code false} otherwise
     */
    public boolean isBorderFortress() {
        return availableCastles.size() > 1;
    }

    /**
     * @return the amount of barracks in this fortress
     */
    public int getFortSize() {
        return fortType == 0 ? 3 : 5;
    }

    public void spawnSuspiciousMerchant() {
        if (suspiciousMerchantSpawned) {
            return;
        }

        suspiciousMerchantSpawned = true;
        for (Spawn spawnDat : siegeNpcs) {
            spawnDat.doSpawn(false);
            spawnDat.startRespawn();
        }
    }

    public void despawnSuspiciousMerchant() {
        if (!suspiciousMerchantSpawned) {
            return;
        }

        suspiciousMerchantSpawned = false;
        for (Spawn spawnDat : siegeNpcs) {
            spawnDat.stopRespawn();
            spawnDat.getLastSpawn().deleteMe();
        }
    }

    public void spawnNpcCommanders() {
        for (Spawn spawnDat : npcCommanders) {
            spawnDat.doSpawn(false);
            spawnDat.startRespawn();
        }
    }

    public void despawnNpcCommanders() {
        for (Spawn spawnDat : npcCommanders) {
            spawnDat.stopRespawn();
            spawnDat.getLastSpawn().deleteMe();
        }
    }

    public void spawnSpecialEnvoys() {
        for (Spawn spawnDat : specialEnvoys) {
            spawnDat.doSpawn(false);
            spawnDat.startRespawn();
        }
    }

    private void initNpcs() {
        try {
            for (FortSpawn fortSpawn : spawnsOfType(0)) {
                final Spawn spawnDat = newSpawn(fortSpawn);
                SpawnTable.getInstance().addSpawn(spawnDat);
                spawnDat.doSpawn(false);
                spawnDat.startRespawn();
            }
        } catch (Exception e) {
            log.warn("Fort {} initNpcs: Spawn could not be initialized: {}", getResidenceId(), e.getMessage(), e);
        }
    }

    private void initSiegeNpcs() {
        siegeNpcs.clear();
        try {
            for (FortSpawn fortSpawn : spawnsOfType(2)) {
                siegeNpcs.add(newSpawn(fortSpawn));
            }
        } catch (Exception e) {
            log.warn("Fort {} initSiegeNpcs: Spawn could not be initialized: {}", getResidenceId(), e.getMessage(), e);
        }
    }

    private void initNpcCommanders() {
        npcCommanders.clear();
        try {
            for (FortSpawn fortSpawn : spawnsOfType(1)) {
                npcCommanders.add(newSpawn(fortSpawn));
            }
        } catch (Exception e) {
            // Problem with initializing spawn, go to next one.
            log.warn(
                    "Fort {} initNpcCommanders: Spawn could not be initialized: {}",
                    getResidenceId(),
                    e.getMessage(),
                    e);
        }
    }

    private void initSpecialEnvoys() {
        specialEnvoys.clear();
        envoyCastles.clear();
        availableCastles.clear();
        try {
            for (FortSpawn fortSpawn : spawnsOfType(3)) {
                final Spawn spawnDat = newSpawn(fortSpawn);
                specialEnvoys.add(spawnDat);
                envoyCastles.put(spawnDat.getId(), fortSpawn.castleId());
                availableCastles.add(fortSpawn.castleId());
            }
        } catch (Exception e) {
            // Problem with initializing spawn, go to next one.
            log.warn(
                    "Fort {} initSpecialEnvoys: Spawn could not be initialized: {}",
                    getResidenceId(),
                    e.getMessage(),
                    e);
        }
    }

    private List<FortSpawn> spawnsOfType(int type) {
        return FortData.getInstance().getFort(getResidenceId()).spawns().stream()
                .filter(fortSpawn -> fortSpawn.type() == type)
                .toList();
    }

    private static Spawn newSpawn(FortSpawn fortSpawn) throws ClassNotFoundException, NoSuchMethodException {
        final Spawn spawnDat = new Spawn(fortSpawn.npcId());
        spawnDat.setAmount(1);
        spawnDat.setXYZ(
                fortSpawn.spawnPoint().x(),
                fortSpawn.spawnPoint().y(),
                fortSpawn.spawnPoint().z());
        spawnDat.setHeading(fortSpawn.spawnPoint().heading());
        spawnDat.setRespawnDelay(60);
        return spawnDat;
    }

    @Override
    protected void initResidenceZone() {
        for (FortZone zone : ZoneManager.getInstance().getAllZones(FortZone.class)) {
            if (zone.getResidenceId() == getResidenceId()) {
                setResidenceZone(zone);
                break;
            }
        }
    }
}
