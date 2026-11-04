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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.holders.FortSpawn;
import app.l2nx.highfive.data.holders.FortTemplate;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.FortData;
import app.l2nx.highfive.data.xml.StaticObjectData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.StaticObject;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.residences.AbstractResidence;
import app.l2nx.highfive.entity.residences.function.AbstractResidenceFunction;
import app.l2nx.highfive.entity.residences.function.ResidenceFunctions;
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
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class Fort extends AbstractResidence {

    // Load order of the doors, which reset in it.
    private final List<Door> doors = new ObjectArrayList<>();

    private StaticObject flagPole = null;

    private FortSiege siege = null;

    private Calendar siegeDate;

    private Calendar lastOwnedTime;
    private SiegeZone zone;

    private Clan ownerClan = null;

    private int fortType = 0;

    private int fortState = 0;

    private int contractedCastleId = 0;

    private int supplyLeveL = 0;

    private final ResidenceFunctions<FortFunction> function = new ResidenceFunctions<>();
    private ScheduledFuture<?> periodicUpdate;
    private ScheduledFuture<?> maxOwnTimeUpdate;

    // Spawn Data, in the order of the fort template
    private boolean suspiciousMerchantSpawned = false;
    private final List<Spawn> siegeNpcs = new ObjectArrayList<>();
    private final List<Spawn> npcCommanders = new ObjectArrayList<>();
    private final List<Spawn> specialEnvoys = new ObjectArrayList<>();

    private final Int2IntOpenHashMap envoyCastles = new Int2IntOpenHashMap(2);
    private final IntOpenHashSet availableCastles = new IntOpenHashSet(1);

    public static final int FUNC_TELEPORT = 1;

    public static final int FUNC_RESTORE_HP = 2;
    public static final int FUNC_RESTORE_MP = 3;
    public static final int FUNC_RESTORE_EXP = 4;
    public static final int FUNC_SUPPORT = 5;

    @SingleWriter
    public class FortFunction extends AbstractResidenceFunction {
        public FortFunction(int type, int level, int lease, long rate, long time, boolean cwh) {
            super(type, level, lease, rate, time);
            startFee(cwh);
        }

        @Override
        protected int residenceId() {
            return getResidenceId();
        }

        @Override
        protected boolean isOwned() {
            return ownerClan != null;
        }

        @Override
        protected ItemContainer ownerWarehouse() {
            return ownerClan.getWarehouse();
        }

        @Override
        protected void save(ResidenceFunction function) {
            FortManager.getInstance().saveFunction(getResidenceId(), function);
        }

        @Override
        protected void removeUnpaid() {
            removeFunction(getType());
        }
    }

    public Fort(int fortId) {
        super(fortId);
        load();
        loadFlagPoles();
        if (ownerClan != null) {
            setVisibleFlag(true);
            loadFunctions();
        }

        initResidenceZone();
        initNpcs(); // load and spawn npcs (Always spawned)
        initSiegeNpcs(); // load suspicious merchants (Despawned 10mins before siege)

        initNpcCommanders(); // npc Commanders (not monsters) (Spawned during siege)
        spawnNpcCommanders();
        initSpecialEnvoys(); // envoys from castles (Spawned after fort taken)
        if ((ownerClan != null) && (fortState == 0)) {
            spawnSpecialEnvoys();
        }
    }

    public FortFunction getFunction(int type) {
        WorldPartition.checkOwner();
        return function.get(type);
    }

    /**
     * @return a read-only snapshot of the fort doors in load order
     */
    public List<Door> getDoors() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(doors));
    }

    public StaticObject getFlagPole() {
        WorldPartition.checkOwner();
        return flagPole;
    }

    /**
     * @return a copy of the siege date; {@link #setSiegeDate} changes it
     */
    public Calendar getSiegeDate() {
        WorldPartition.checkOwner();
        return (Calendar) siegeDate.clone();
    }

    public void setSiegeDate(long millis) {
        WorldPartition.checkOwner();
        siegeDate.setTimeInMillis(millis);
    }

    public Clan getOwnerClan() {
        WorldPartition.checkOwner();
        return ownerClan;
    }

    public int getFortType() {
        WorldPartition.checkOwner();
        return fortType;
    }

    public int getFortState() {
        WorldPartition.checkOwner();
        return fortState;
    }

    public int getContractedCastleId() {
        WorldPartition.checkOwner();
        return contractedCastleId;
    }

    public int getSupplyLeveL() {
        WorldPartition.checkOwner();
        return supplyLeveL;
    }

    public void endOfSiege(Clan clan) {
        WorldPartition.checkOwner();
        WorldPartition.loop().execute(new endFortressSiege(this, clan));
    }

    public void banishForeigners() {
        WorldPartition.checkOwner();
        getResidenceZone().banishForeigners(ownerClan.getId());
    }

    public boolean checkIfInZone(int x, int y, int z) {
        WorldPartition.checkOwner();
        final SiegeZone zone = getZone();
        return (zone != null) && zone.isInsideZone(x, y, z);
    }

    public SiegeZone getZone() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return (FortZone) super.getResidenceZone();
    }

    public double getDistance(WorldObject obj) {
        WorldPartition.checkOwner();
        return getZone().getDistanceToZone(obj);
    }

    public void closeDoor(Player player, int doorId) {
        WorldPartition.checkOwner();
        openCloseDoor(player, doorId, false);
    }

    public void openDoor(Player player, int doorId) {
        WorldPartition.checkOwner();
        openCloseDoor(player, doorId, true);
    }

    public void openCloseDoor(Player player, int doorId, boolean open) {
        WorldPartition.checkOwner();
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

    public void removeUpgrade() {
        WorldPartition.checkOwner();
        removeDoorUpgrade();
    }

    public boolean setOwner(Clan clan, boolean updateClansReputation) {
        WorldPartition.checkOwner();
        if (clan == null) {
            log.warn("Updating Fort owner with null clan!!!");
            return false;
        }

        final SystemMessage sm = new SystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_OF_S1_HAS_FINISHED);
        sm.addCastleId(getResidenceId());
        getSiege().announceToPlayer(sm);

        final Clan oldowner = ownerClan;
        if ((oldowner != null) && (clan != oldowner)) {
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

        setFortState(0, 0);
        if (clan.getCastleId() > 0) {
            getSiege().announceToPlayer(new SystemMessage(SystemMessageId.THE_REBEL_ARMY_RECAPTURED_THE_FORTRESS));

            // The siege is over, the fortress went back to the rebels instead of to the castle owner.
            if (getSiege().isInProgress()) {
                getSiege().endSiege();
            }

            return false;
        }

        if (updateClansReputation) {
            updateClansReputation(clan, false);
        }

        spawnSpecialEnvoys();

        if (clan.getFortId() > 0) {
            FortManager.getInstance().getFortByOwner(clan).removeOwner(true);
        }

        setSupplyLeveL(0);
        setOwnerClan(clan);
        updateOwnerInDB();
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        supplyLeveL++;
        if (supplyLeveL > FeatureConfig.FS_MAX_SUPPLY_LEVEL) {
            supplyLeveL = FeatureConfig.FS_MAX_SUPPLY_LEVEL;
        }
    }

    public void setSupplyLeveL(int value) {
        WorldPartition.checkOwner();
        if (value <= FeatureConfig.FS_MAX_SUPPLY_LEVEL) {
            supplyLeveL = value;
        }
    }

    public void saveFortVariables() {
        WorldPartition.checkOwner();
        FortManager.getInstance().saveSupplyLevel(getResidenceId(), supplyLeveL);
    }

    public void setVisibleFlag(boolean value) {
        WorldPartition.checkOwner();
        final StaticObject flagPole = this.flagPole;
        if (flagPole != null) {
            flagPole.setMeshIndex(value ? 1 : 0);
        }
    }

    public void resetDoors() {
        WorldPartition.checkOwner();
        try (Snapshot<Door> walk = ScratchBuffers.snapshot(doors)) {
            for (Door door : walk) {
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
        }

        loadDoorUpgrade();
    }

    public void upgradeDoor(int doorId, int hp, int pDef, int mDef) {
        WorldPartition.checkOwner();
        final Door door = getDoor(doorId);
        if (door != null) {
            door.setCurrentHp(door.getMaxHp() + hp);
            saveDoorUpgrade(doorId, hp, pDef, mDef);
        }
    }

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
                periodicUpdate = WorldPartition.loop()
                        .scheduleAtFixedRate(
                                new FortUpdater(this, clan, runCount, FortUpdaterType.PERIODIC_UPDATE),
                                initial,
                                TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ),
                                TimeUnit.MILLISECONDS);
                if (FeatureConfig.FS_MAX_OWN_TIME > 0) {
                    maxOwnTimeUpdate = WorldPartition.loop()
                            .scheduleAtFixedRate(
                                    new FortUpdater(this, clan, runCount, FortUpdaterType.MAX_OWN_TIME),
                                    3600000,
                                    3600000,
                                    TimeUnit.MILLISECONDS);
                }
            } else {
                maxOwnTimeUpdate = WorldPartition.loop()
                        .schedule(
                                new FortUpdater(this, clan, 0, FortUpdaterType.MAX_OWN_TIME),
                                60000,
                                TimeUnit.MILLISECONDS);
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

    private void loadFunctions() {
        for (ResidenceFunction stored : FortManager.getInstance().findFunctions(getResidenceId())) {
            function.put(new FortFunction(
                    stored.type(),
                    stored.level(),
                    stored.lease(),
                    stored.rate(),
                    epochMillisOrZero(stored.endTime()),
                    true));
        }
    }

    public void removeFunction(int functionType) {
        WorldPartition.checkOwner();
        function.remove(functionType);
        FortManager.getInstance().deleteFunction(getResidenceId(), functionType);
    }

    private void removeAllFunctions() {
        function.forEachType(this::removeFunction);
    }

    public boolean updateFunctions(Player player, int type, int level, int lease, long rate, boolean addNew) {
        WorldPartition.checkOwner();
        if (player == null) {
            return false;
        }

        if ((lease > 0) && !player.destroyItemByItemId(null, Inventory.ADENA_ID, lease, null, true)) {
            return false;
        }

        if (addNew) {
            function.put(new FortFunction(type, level, lease, rate, 0, false));
        } else if ((level == 0) && (lease == 0)) {
            removeFunction(type);
        } else if ((lease - function.get(type).getLease()) > 0) {
            function.put(new FortFunction(type, level, lease, rate, -1, false));
        } else {
            function.get(type).change(level, lease);
        }

        return true;
    }

    public void activateInstance() {
        WorldPartition.checkOwner();
        loadDoor();
    }

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

        if (clan != null) {
            clan.setFortId(getResidenceId());
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_VICTORIOUS_IN_THE_FORTRESS_BATTLE_OF_S2);
            sm.addString(clan.getName());
            sm.addCastleId(getResidenceId());
            World.getPlayers().forEach(p -> p.sendPacket(sm));
            clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
            clan.broadcastToOnlineMembers(new PlaySound(1, "Siege_Victory", 0, 0, 0, 0, 0));
            cancelUpdates();
            periodicUpdate = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new FortUpdater(this, clan, 0, FortUpdaterType.PERIODIC_UPDATE),
                            TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ),
                            TimeUnit.MINUTES.toMillis(FeatureConfig.FS_UPDATE_FRQ),
                            TimeUnit.MILLISECONDS);
            if (FeatureConfig.FS_MAX_OWN_TIME > 0) {
                maxOwnTimeUpdate = WorldPartition.loop()
                        .scheduleAtFixedRate(
                                new FortUpdater(this, clan, 0, FortUpdaterType.MAX_OWN_TIME),
                                3600000,
                                3600000,
                                TimeUnit.MILLISECONDS);
            }
        } else {
            cancelUpdates();
        }
    }

    private void cancelUpdates() {
        if (periodicUpdate != null) {
            periodicUpdate.cancel(false);
            periodicUpdate = null;
        }

        if (maxOwnTimeUpdate != null) {
            maxOwnTimeUpdate.cancel(false);
            maxOwnTimeUpdate = null;
        }
    }

    public void setOwnerClan(Clan clan) {
        WorldPartition.checkOwner();
        setVisibleFlag(clan != null);
        ownerClan = clan;
    }

    public Door getDoor(int doorId) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (siege == null) {
            siege = new FortSiege(this);
        }

        return siege;
    }

    public int getOwnedTime() {
        WorldPartition.checkOwner();
        return lastOwnedTime.getTimeInMillis() == 0
                ? 0
                : (int) ((ClockBridge.wallMillis() - lastOwnedTime.getTimeInMillis()) / 1000);
    }

    public int getTimeTillRebelArmy() {
        WorldPartition.checkOwner();
        return lastOwnedTime.getTimeInMillis() == 0
                ? 0
                : (int) (((lastOwnedTime.getTimeInMillis() + (FeatureConfig.FS_MAX_OWN_TIME * 3600000))
                                - ClockBridge.wallMillis())
                        / 1000);
    }

    public long getTimeTillNextFortUpdate() {
        WorldPartition.checkOwner();
        return periodicUpdate == null ? 0 : periodicUpdate.getDelay(TimeUnit.SECONDS);
    }

    public void updateClansReputation(Clan owner, boolean removePoints) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        fortState = state;
        contractedCastleId = castleId;
        FortManager.getInstance().saveContract(getResidenceId(), fortState, contractedCastleId);
    }

    public int getCastleIdByAmbassador(int npcId) {
        WorldPartition.checkOwner();
        return envoyCastles.containsKey(npcId) ? envoyCastles.get(npcId) : -1;
    }

    public Castle getCastleByAmbassador(int npcId) {
        WorldPartition.checkOwner();
        return CastleManager.getInstance().getCastleById(getCastleIdByAmbassador(npcId));
    }

    /**
     * @return the castle contracted with this fortress ({@code null} if no contract with any castle)
     */
    public Castle getContractedCastle() {
        WorldPartition.checkOwner();
        return CastleManager.getInstance().getCastleById(getContractedCastleId());
    }

    public boolean isBorderFortress() {
        WorldPartition.checkOwner();
        return availableCastles.size() > 1;
    }

    public int getFortSize() {
        WorldPartition.checkOwner();
        return fortType == 0 ? 3 : 5;
    }

    public void spawnSuspiciousMerchant() {
        WorldPartition.checkOwner();
        if (suspiciousMerchantSpawned) {
            return;
        }

        suspiciousMerchantSpawned = true;
        spawnAll(siegeNpcs);
    }

    public void despawnSuspiciousMerchant() {
        WorldPartition.checkOwner();
        if (!suspiciousMerchantSpawned) {
            return;
        }

        suspiciousMerchantSpawned = false;
        despawnAll(siegeNpcs);
    }

    public void spawnNpcCommanders() {
        WorldPartition.checkOwner();
        spawnAll(npcCommanders);
    }

    public void despawnNpcCommanders() {
        WorldPartition.checkOwner();
        despawnAll(npcCommanders);
    }

    public void spawnSpecialEnvoys() {
        WorldPartition.checkOwner();
        spawnAll(specialEnvoys);
    }

    private static void spawnAll(List<Spawn> spawns) {
        try (Snapshot<Spawn> walk = ScratchBuffers.snapshot(spawns)) {
            for (Spawn spawnDat : walk) {
                spawnDat.doSpawn(false);
                spawnDat.startRespawn();
            }
        }
    }

    private static void despawnAll(List<Spawn> spawns) {
        try (Snapshot<Spawn> walk = ScratchBuffers.snapshot(spawns)) {
            for (Spawn spawnDat : walk) {
                spawnDat.stopRespawn();
                spawnDat.getLastSpawn().deleteMe();
            }
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
                envoyCastles.put(spawnDat.getId(), (int) fortSpawn.castleId());
                availableCastles.add((int) fortSpawn.castleId());
            }
        } catch (Exception e) {
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
