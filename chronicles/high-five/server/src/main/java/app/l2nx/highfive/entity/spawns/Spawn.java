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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.RandomSpawnsConfig;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.NpcPersonalAIData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.type.NpcSpawnTerritory;
import app.l2nx.highfive.entity.zone.type.WaterZone;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.taskmanagers.RespawnTaskManager;
import app.l2nx.highfive.util.LocationUtil;
import java.lang.reflect.Constructor;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.random.RandomGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class manages the spawn and respawn of a group of Npcs that are in the same are and have the same type.<br>
 * <b><u>Concept</u>:</b><br>
 * Npc can be spawned either in a random position into a location area (if Lox=0 and Locy=0), either at an exact position.<br>
 * The heading of the Npc can be a random heading if not defined (value= -1) or an exact heading (ex : merchant...).
 * @author Nightmare, Mobius
 */
@Slf4j
public class Spawn extends Location {

    @Getter
    @Setter
    private String name;

    @Getter
    private NpcTemplate template;

    @Getter
    @Setter
    private int amount;

    private int currentCount;
    public int scheduledCount;

    @Getter
    @Setter
    private int locationId;

    @Getter
    @Setter
    private NpcSpawnTerritory spawnTerritory = null;

    @Getter
    @Setter
    private int respawnMinDelay;

    @Getter
    @Setter
    private int respawnMaxDelay;

    @Getter
    @Setter
    private int chaseRange = 0;

    private Constructor<? extends Npc> constructor;

    @Getter
    private boolean respawnEnabled = true;

    private static Set<SevenSignsFestival> spawnListeners = ConcurrentHashMap.newKeySet(1);

    @Getter
    private final Deque<Npc> spawnedNpcs = new ConcurrentLinkedDeque<>();

    @Setter
    private boolean randomWalking = false;

    @Getter
    private int npcSpawnTemplateId = 0;

    @Getter
    private Location spawnLocation = null;
    /** Spawn group this spawn is a slot of, null for regular spawns */
    @Getter
    @Setter
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
        return template.getId();
    }

    public void decreaseCount(Npc oldNpc) {
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
        respawnEnabled = false;
    }

    public void startRespawn() {
        respawnEnabled = true;
    }

    public Npc doSpawn() {
        return respawnEnabled ? doSpawn(false) : null;
    }

    public Npc doSpawn(boolean isSummonSpawn) {
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
            setLocation(loc);
            setHeading(-1);
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

        if (RandomSpawnsConfig.ENABLE_RANDOM_MONSTER_SPAWNS
                && (getHeading() != -1)
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
                setXYZ(newlocx, newlocy, newlocz);
                setHeading(-1);
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

        if (getHeading() == -1) {
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
        spawnListeners.add(listener);
    }

    public static void removeSpawnListener(SevenSignsFestival listener) {
        spawnListeners.remove(listener);
    }

    public static void notifyNpcSpawned(Npc npc) {
        for (SevenSignsFestival listener : spawnListeners) {
            listener.npcSpawned(npc);
        }
    }

    /** Both arguments are seconds; the stored bounds are milliseconds. */
    public void setRespawnDelay(int delay, int randomInterval) {
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
        setRespawnDelay(delay, 0);
    }

    public int getRespawnDelay() {
        return (respawnMinDelay + respawnMaxDelay) / 2;
    }

    public boolean hasRespawnRandom() {
        return respawnMinDelay != respawnMaxDelay;
    }

    public boolean isTerritoryBased() {
        return (spawnTerritory != null) && (getX() == 0) && (getY() == 0);
    }

    public Npc getLastSpawn() {
        return spawnedNpcs.peekLast();
    }

    public void respawnNpc(Npc oldNpc) {
        if (respawnEnabled) {
            initializeNpc(oldNpc);
        }
    }

    public boolean getRandomWalking() {
        return randomWalking;
    }

    public void setSpawnTemplateId(int npcSpawnTemplateId) {
        this.npcSpawnTemplateId = npcSpawnTemplateId;
    }

    @Override
    public String toString() {
        return "Spawn ID: " + template.getId() + " X: " + getX() + " Y: " + getY() + " Z: " + getZ() + " Heading: "
                + getHeading();
    }
}
