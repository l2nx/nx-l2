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
package app.l2nx.highfive.entity.spawns;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.RandomSpawnsConfig;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.NpcPersonalAIData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.type.NpcSpawnTerritory;
import app.l2nx.highfive.entity.zone.type.WaterZone;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.interfaces.IPositionable;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.RespawnTaskManager;
import app.l2nx.highfive.util.LocationUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * This class manages the spawn and respawn of a group of Npcs that are in the same are and have the same type.<br>
 * <b><u>Concept</u>:</b><br>
 * Npc can be spawned either in a random position into a location area (if Lox=0 and Locy=0), either at an exact position.<br>
 * The heading of the Npc can be a random heading if not defined (value= -1) or an exact heading (ex : merchant...).
 * @author Nightmare, Mobius
 */
@Slf4j
@SingleWriter
public class Spawn extends Location {
    private static final ObjectLinkedOpenHashSet<SevenSignsFestival> SPAWN_LISTENERS = new ObjectLinkedOpenHashSet<>(1);
    /** The heading a spawn definition gives for "pick one per spawned NPC"; {@link Location} clamps it to 0. */
    private static final int RANDOM_HEADING = -1;

    // Hash of a spawn: its creation order, which is the same on every run, unlike an identity hash.
    private static int nextSequence;

    private final int sequence = nextSequence++;
    private boolean randomHeading;

    private String name;
    private final NpcTemplate template;
    private int amount;
    private int currentCount;
    private int scheduledCount;
    private int locationId;
    private NpcSpawnTerritory spawnTerritory = null;
    private int respawnMinDelay;
    private int respawnMaxDelay;
    private int chaseRange = 0;
    private Constructor<? extends Npc> constructor;
    private boolean respawnEnabled = true;
    private final ObjectArrayList<Npc> spawnedNpcs = new ObjectArrayList<>(1);
    private boolean randomWalking = false;
    private int npcSpawnTemplateId = 0;
    private Location spawnLocation = null;
    /** Spawn group this spawn is a slot of, null for regular spawns */
    private SpawnGroup group = null;

    public Spawn(NpcTemplate template) throws ClassNotFoundException, NoSuchMethodException, ClassCastException {
        super(0, 0, -10000);

        this.template = template;

        if (this.template == null) {
            return;
        }

        constructor = Class.forName("app.l2nx.highfive.entity.actor.instance." + this.template.getType())
                .asSubclass(Npc.class)
                .getConstructor(NpcTemplate.class);
    }

    public Spawn(int npcId) throws ClassNotFoundException, NoSuchMethodException, ClassCastException {
        this(NpcData.getInstance().getTemplate(npcId));
    }

    public int getId() {
        WorldPartition.checkOwner();
        return template.getId();
    }

    public void decreaseCount(Npc oldNpc) {
        WorldPartition.checkOwner();
        if (currentCount <= 0) {
            return;
        }

        currentCount--;

        spawnedNpcs.remove(oldNpc);

        // The scheduled+alive cap prevents duplicate respawns under lag.
        if (respawnEnabled && ((scheduledCount + currentCount) < amount)) {
            scheduledCount++;

            RespawnTaskManager.getInstance()
                    .add(
                            oldNpc,
                            ClockBridge.gameMillis()
                                    + (respawnMinDelay < respawnMaxDelay
                                            ? ClockBridge.random().nextInt(respawnMinDelay, respawnMaxDelay + 1)
                                            : respawnMinDelay));
        }

        if (group != null) {
            group.onUnitDeath(this);
        }
    }

    public int init() {
        WorldPartition.checkOwner();
        while (currentCount < amount) {
            final int previousCount = currentCount;
            if ((doSpawn() == null) && (currentCount == previousCount)) {
                // Respawn is disabled or the NPC could not be created, looping again would never end.
                break;
            }
        }

        respawnEnabled = respawnMinDelay > 0;

        return currentCount;
    }

    public void stopRespawn() {
        WorldPartition.checkOwner();
        respawnEnabled = false;
    }

    public void startRespawn() {
        WorldPartition.checkOwner();
        respawnEnabled = true;
    }

    public Npc doSpawn() {
        WorldPartition.checkOwner();
        return respawnEnabled ? doSpawn(false) : null;
    }

    public Npc doSpawn(boolean isSummonSpawn) {
        WorldPartition.checkOwner();
        try {
            if (template.isType("Pet") || template.isType("Decoy") || template.isType("Trap")) {
                currentCount++;
                return null;
            }

            final Npc npc = constructor.newInstance(template);
            npc.setInstanceId(getInstanceId()); // Must be done before object is spawned into visible world.
            if (isSummonSpawn) {
                npc.setShowSummonAnimation(isSummonSpawn);
            }

            return initializeNpc(npc);
        } catch (Exception e) {
            log.warn("NPC {} class not found", template.getId(), e);
        }

        return null;
    }

    private Npc initializeNpc(Npc npc) {
        npc.setDead(false);
        npc.setDecayed(false);

        int newlocx = 0;
        int newlocy = 0;
        int newlocz = -10000;

        if (spawnTerritory != null) {
            final Location loc = spawnTerritory.getRandomPoint();
            newlocx = loc.getX();
            newlocy = loc.getY();
            newlocz = loc.getZ();
            super.setLocation(loc);
            heading(RANDOM_HEADING);
        } else if ((getX() == 0) && (getY() == 0)) {
            log.warn("NPC {} doesn't have spawn location!", npc);
            return null;
        } else {
            newlocx = spawnLocation != null ? spawnLocation.getX() : getX();
            newlocy = spawnLocation != null ? spawnLocation.getY() : getY();
            newlocz = spawnLocation != null ? spawnLocation.getZ() : getZ();

            if (spawnLocation == null) {
                spawnLocation = new Location(newlocx, newlocy, newlocz);
            }
        }

        final WaterZone water = ZoneManager.getInstance().getZone(newlocx, newlocy, newlocz, WaterZone.class);

        // Rolled on every spawn of a point spawn, with any heading; a territory spawn already picked a random point
        // inside its territory, which the offset could leave.
        if (RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS
                && (spawnTerritory == null)
                && npc.isMonster()
                && !npc.isQuestMonster()
                && !WalkingManager.getInstance().isTargeted(npc)
                && (getInstanceId() == 0)
                && !getTemplate().isUndying()
                && !npc.isRaid()
                && !npc.isRaidMinion()
                && !npc.isFlying()
                && (water == null)
                && !RandomSpawnsConfig.MOBS_LIST_NOT_RANDOM.contains(npc.getId())) {
            final RandomGenerator random = ClockBridge.random();
            final int minRange = RandomSpawnsConfig.MOB_MIN_SPAWN_RANGE;
            final int maxRange = RandomSpawnsConfig.MOB_MAX_SPAWN_RANGE;
            final int randX = newlocx + (minRange < maxRange ? random.nextInt(minRange, maxRange + 1) : minRange);
            final int randY = newlocy + (minRange < maxRange ? random.nextInt(minRange, maxRange + 1) : minRange);
            if (GeoEngine.getInstance()
                            .canMoveToTarget(newlocx, newlocy, newlocz, randX, randY, newlocz, getInstanceId()) //
                    && GeoEngine.getInstance()
                            .canSeeTarget(newlocx, newlocy, newlocz, randX, randY, newlocz, getInstanceId())) {
                newlocx = randX;
                newlocy = randY;
                super.setXYZ(newlocx, newlocy, newlocz);
            }
        }

        if (npc.isMonster() && !npc.isFlying() && (water == null)) {
            final int geoZ = GeoEngine.getInstance().getHeight(newlocx, newlocy, newlocz);
            if (LocationUtil.calculateDistance(newlocx, newlocy, newlocz, newlocx, newlocy, geoZ, true, true) < 300) {
                newlocz = geoZ;
            }

            if (spawnTerritory != null) {
                final int highZ = spawnTerritory.getHighZ();
                if (highZ < newlocz) {
                    newlocz = highZ;
                }
            }
        }

        npc.stopAllEffects();

        npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());

        if (npc.hasVariables()) {
            npc.getVariables().clear();
        }

        npc.setRandomWalking(randomWalking);

        if (randomHeading) {
            npc.setHeading(ClockBridge.random().nextInt(61794));
        } else {
            npc.setHeading(getHeading());
        }

        npc.setSummoner(null);
        npc.resetSummonedNpcs();
        npc.setSpawn(this);
        npc.spawnMe(newlocx, newlocy, newlocz);

        if (npc.isInInstance()) {
            npc.broadcastInfo();
        }

        notifyNpcSpawned(npc);

        if (name != null) {
            NpcPersonalAIData.getInstance().initializeNpcParameters(npc, this, name);
        }

        spawnedNpcs.add(npc);

        currentCount++;

        if (npc.isMonster() && NpcData.getMasterMonsterIDs().contains(npc.getId())) {
            npc.asMonster()
                    .getMinionList()
                    .spawnMinions(npc.getTemplate().getParameters().getMinionList("Privates"));
        }

        if (npc.isAttackable()) {
            npc.asAttackable().setChampion(false);
        }

        if (ChampionMonstersConfig.CHAMPION_ENABLE
                && npc.isMonster()
                && !npc.isQuestMonster()
                && !template.isUndying()
                && !npc.isRaid()
                && !npc.isRaidMinion()
                && (ChampionMonstersConfig.CHAMPION_FREQUENCY > 0)
                && (npc.getLevel() >= ChampionMonstersConfig.CHAMP_MIN_LEVEL)
                && (npc.getLevel() <= ChampionMonstersConfig.CHAMP_MAX_LEVEL)
                && (ChampionMonstersConfig.CHAMPION_ENABLE_IN_INSTANCES || (getInstanceId() == 0))
                && (ClockBridge.random().nextInt(100) < ChampionMonstersConfig.CHAMPION_FREQUENCY)) {
            npc.asAttackable().setChampion(true);
        }

        return npc;
    }

    public static void addSpawnListener(SevenSignsFestival listener) {
        WorldPartition.checkOwner();
        SPAWN_LISTENERS.add(listener);
    }

    public static void removeSpawnListener(SevenSignsFestival listener) {
        WorldPartition.checkOwner();
        SPAWN_LISTENERS.remove(listener);
    }

    private static void notifyNpcSpawned(Npc npc) {
        if (SPAWN_LISTENERS.isEmpty()) {
            return;
        }

        try (ScratchBuffers.Snapshot<SevenSignsFestival> listeners = ScratchBuffers.snapshot(SPAWN_LISTENERS)) {
            for (SevenSignsFestival listener : listeners) {
                listener.npcSpawned(npc);
            }
        }
    }

    /** Both arguments are seconds; the stored bounds are milliseconds. */
    public void setRespawnDelay(int delay, int randomInterval) {
        WorldPartition.checkOwner();
        if (delay != 0) {
            if (delay < 0) {
                log.warn("respawn delay is negative for spawn:{}", this);
            }

            final int minDelay = delay - randomInterval;
            final int maxDelay = delay + randomInterval;

            respawnMinDelay = Math.max(1, minDelay) * 1000;
            respawnMaxDelay = Math.max(1, maxDelay) * 1000;
        } else {
            respawnMinDelay = 0;
            respawnMaxDelay = 0;
        }
    }

    public void setRespawnDelay(int delay) {
        WorldPartition.checkOwner();
        setRespawnDelay(delay, 0);
    }

    public int getRespawnDelay() {
        WorldPartition.checkOwner();
        return (respawnMinDelay + respawnMaxDelay) / 2;
    }

    public boolean hasRespawnRandom() {
        WorldPartition.checkOwner();
        return respawnMinDelay != respawnMaxDelay;
    }

    public boolean isTerritoryBased() {
        WorldPartition.checkOwner();
        return (spawnTerritory != null) && (getX() == 0) && (getY() == 0);
    }

    public Npc getLastSpawn() {
        WorldPartition.checkOwner();
        return spawnedNpcs.isEmpty() ? null : spawnedNpcs.get(spawnedNpcs.size() - 1);
    }

    /** Runs a respawn {@link #decreaseCount(Npc)} scheduled, which frees its slot whether or not the NPC comes back. */
    public void respawnNpc(Npc oldNpc) {
        WorldPartition.checkOwner();
        scheduledCount--;
        if (respawnEnabled) {
            initializeNpc(oldNpc);
        }
    }

    public boolean getRandomWalking() {
        WorldPartition.checkOwner();
        return randomWalking;
    }

    public void setSpawnTemplateId(int npcSpawnTemplateId) {
        WorldPartition.checkOwner();
        this.npcSpawnTemplateId = npcSpawnTemplateId;
    }

    public int getNpcSpawnTemplateId() {
        WorldPartition.checkOwner();
        return npcSpawnTemplateId;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void setName(String name) {
        WorldPartition.checkOwner();
        this.name = name;
    }

    public NpcTemplate getTemplate() {
        WorldPartition.checkOwner();
        return template;
    }

    public int getAmount() {
        WorldPartition.checkOwner();
        return amount;
    }

    public void setAmount(int amount) {
        WorldPartition.checkOwner();
        this.amount = amount;
    }

    public int getLocationId() {
        WorldPartition.checkOwner();
        return locationId;
    }

    public void setLocationId(int locationId) {
        WorldPartition.checkOwner();
        this.locationId = locationId;
    }

    public NpcSpawnTerritory getSpawnTerritory() {
        WorldPartition.checkOwner();
        return spawnTerritory;
    }

    public void setSpawnTerritory(NpcSpawnTerritory spawnTerritory) {
        WorldPartition.checkOwner();
        this.spawnTerritory = spawnTerritory;
    }

    public int getRespawnMinDelay() {
        WorldPartition.checkOwner();
        return respawnMinDelay;
    }

    public void setRespawnMinDelay(int respawnMinDelay) {
        WorldPartition.checkOwner();
        this.respawnMinDelay = respawnMinDelay;
    }

    public int getRespawnMaxDelay() {
        WorldPartition.checkOwner();
        return respawnMaxDelay;
    }

    public void setRespawnMaxDelay(int respawnMaxDelay) {
        WorldPartition.checkOwner();
        this.respawnMaxDelay = respawnMaxDelay;
    }

    public int getChaseRange() {
        WorldPartition.checkOwner();
        return chaseRange;
    }

    public void setChaseRange(int chaseRange) {
        WorldPartition.checkOwner();
        this.chaseRange = chaseRange;
    }

    public boolean isRespawnEnabled() {
        WorldPartition.checkOwner();
        return respawnEnabled;
    }

    public List<Npc> getSpawnedNpcs() {
        WorldPartition.checkOwner();
        return spawnedNpcs.isEmpty()
                ? ObjectLists.emptyList()
                : ObjectLists.unmodifiable(new ObjectArrayList<>(spawnedNpcs));
    }

    public void setRandomWalking(boolean randomWalking) {
        WorldPartition.checkOwner();
        this.randomWalking = randomWalking;
    }

    public Location getSpawnLocation() {
        WorldPartition.checkOwner();
        return spawnLocation;
    }

    public SpawnGroup getGroup() {
        WorldPartition.checkOwner();
        return group;
    }

    public void setGroup(SpawnGroup group) {
        WorldPartition.checkOwner();
        this.group = group;
    }

    @Override
    public int getX() {
        WorldPartition.checkOwner();
        return super.getX();
    }

    @Override
    public int getY() {
        WorldPartition.checkOwner();
        return super.getY();
    }

    @Override
    public int getZ() {
        WorldPartition.checkOwner();
        return super.getZ();
    }

    @Override
    public int getHeading() {
        WorldPartition.checkOwner();
        return super.getHeading();
    }

    @Override
    public int getInstanceId() {
        WorldPartition.checkOwner();
        return super.getInstanceId();
    }

    @Override
    public IPositionable getLocation() {
        WorldPartition.checkOwner();
        return super.getLocation();
    }

    @Override
    public Location clone() {
        WorldPartition.checkOwner();
        return super.clone();
    }

    @Override
    public void setXYZ(int x, int y, int z) {
        WorldPartition.checkOwner();
        super.setXYZ(x, y, z);
    }

    @Override
    public void setXYZ(ILocational loc) {
        WorldPartition.checkOwner();
        super.setXYZ(loc);
    }

    /** {@code -1} gives every spawned NPC a random heading; {@link #getHeading()} then answers 0. */
    @Override
    public void setHeading(int heading) {
        WorldPartition.checkOwner();
        heading(heading);
    }

    public boolean isRandomHeading() {
        WorldPartition.checkOwner();
        return randomHeading;
    }

    private void heading(int value) {
        randomHeading = value == RANDOM_HEADING;
        super.setHeading(randomHeading ? 0 : value);
    }

    @Override
    public void setInstanceId(int instanceId) {
        WorldPartition.checkOwner();
        super.setInstanceId(instanceId);
    }

    @Override
    public void setLocation(Location loc) {
        WorldPartition.checkOwner();
        super.setLocation(loc);
        randomHeading = false;
    }

    /** Identity: a spawn is world state that moves while hash sets ({@code SpawnTable}) hold it. */
    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return sequence;
    }

    @Override
    public String toString() {
        return "Spawn ID: " + (template != null ? template.getId() : null) + " X: " + x + " Y: " + y + " Z: " + z
                + " Heading: " + heading;
    }
}
