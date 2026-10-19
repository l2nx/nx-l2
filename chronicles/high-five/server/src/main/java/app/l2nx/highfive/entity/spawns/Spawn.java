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
import java.util.concurrent.ThreadLocalRandom;
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

    /** String identifier of this spawn */
    @Getter
    @Setter
    private String name;
    /** The link on the NpcTemplate object containing generic and static properties of this spawn (ex : RewardExp, RewardSP, AggroRange...) */
    @Getter
    private NpcTemplate template;
    /** The maximum number of Npc that can manage this Spawn */
    @Getter
    @Setter
    private int amount;
    /** The current number of Npc managed by this Spawn */
    private int currentCount;
    /** The current number of SpawnTask in progress or stand by of this Spawn */
    public int scheduledCount;
    /** The identifier of the location area where Npc can be spawned */
    @Getter
    @Setter
    private int locationId;
    /** Link to NPC spawn territory */
    @Getter
    @Setter
    private NpcSpawnTerritory spawnTerritory = null;
    /** Minimum respawn delay */
    @Getter
    @Setter
    private int respawnMinDelay;
    /** Maximum respawn delay */
    @Getter
    @Setter
    private int respawnMaxDelay;
    /** Maximum distance monsters can be pulled away from spawn. */
    @Getter
    @Setter
    private int chaseRange = 0;
    /** The generic constructor of Npc managed by this Spawn */
    private Constructor<? extends Npc> constructor;
    /** If True an Npc is respawned each time that another is killed */
    @Getter
    private boolean respawnEnabled = true;

    private static Set<SevenSignsFestival> spawnListeners = ConcurrentHashMap.newKeySet(1);

    @Getter
    private final Deque<Npc> spawnedNpcs = new ConcurrentLinkedDeque<>();

    @Setter
    private boolean randomWalking = false; // Is random walk

    @Getter
    private int npcSpawnTemplateId = 0;

    @Getter
    private Location spawnLocation = null;
    /** Spawn group this spawn is a slot of, null for regular spawns */
    @Getter
    @Setter
    private SpawnGroup group = null;

    /**
     * Constructor of Spawn.<br>
     * <br>
     * <b><u>Concept</u>:</b><br>
     * <br>
     * Each Spawn owns generic and static properties (ex : RewardExp, RewardSP, AggroRange...).<br>
     * All of those properties are stored in a different NpcTemplate for each type of Spawn. Each template is loaded once in the server cache memory (reduce memory use).<br>
     * When a new instance of Spawn is created, server just create a link between the instance and the template.<br>
     * This link is stored in <b>template</b> Each Npc is linked to a Spawn that manages its spawn and respawn (delay, location...).<br>
     * This link is stored in <b>spawn</b> of the Npc.<br>
     * <br>
     * <b><u>Actions</u>:</b><br>
     * <ul>
     * <li>Set the template of the Spawn</li>
     * <li>Calculate the implementationName used to generate the generic constructor of Npc managed by this Spawn</li>
     * <li>Create the generic constructor of Npc managed by this Spawn</li>
     * </ul>
     * @param template The NpcTemplate to link to this Spawn
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws ClassCastException when template type is not subclass of Npc
     */
    public Spawn(NpcTemplate template) throws ClassNotFoundException, NoSuchMethodException, ClassCastException {
        super(0, 0, -10000);

        // Set the template of the Spawn.
        this.template = template;

        if (this.template == null) {
            return;
        }

        constructor = Class.forName("app.l2nx.highfive.entity.actor.instance." + this.template.getType())
                .asSubclass(Npc.class)
                .getConstructor(NpcTemplate.class);
    }

    /**
     * Creates a spawn.
     * @param npcId the NPC ID
     * @throws ClassCastException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     */
    public Spawn(int npcId) throws ClassNotFoundException, NoSuchMethodException, ClassCastException {
        this(NpcData.getInstance().getTemplate(npcId));
    }

    /**
     * Gets the NPC ID.
     * @return the NPC ID
     */
    public int getId() {
        return template.getId();
    }

    /**
     * Decrease the current number of Npc of this Spawn and if necessary create a SpawnTask to launch after the respawn Delay. <b><u>Actions</u>:</b>
     * <li>Decrease the current number of Npc of this Spawn</li>
     * <li>Check if respawn is possible to prevent multiple respawning caused by lag</li>
     * <li>Update the current number of SpawnTask in progress or stand by of this Spawn</li>
     * <li>Create a new SpawnTask to launch after the respawn Delay</li> <font color=#FF0000><b><u>Caution</u>: A respawn is possible ONLY if _doRespawn=True and scheduledCount + currentCount < _maximumCount</b></font>
     * @param oldNpc
     */
    public void decreaseCount(Npc oldNpc) {
        // sanity check
        if (currentCount <= 0) {
            return;
        }

        // Decrease the current number of Npc of this Spawn.
        currentCount--;

        // Remove this NPC from list of spawned.
        spawnedNpcs.remove(oldNpc);

        // Check if respawn is possible to prevent multiple respawning caused by lag.
        if (respawnEnabled && ((scheduledCount + currentCount) < amount)) {
            // Update the current number of SpawnTask in progress or stand by of this Spawn.
            scheduledCount++;

            // Schedule the next respawn.
            RespawnTaskManager.getInstance()
                    .add(
                            oldNpc,
                            System.currentTimeMillis()
                                    + (respawnMinDelay < respawnMaxDelay
                                            ? ThreadLocalRandom.current().nextInt(respawnMinDelay, respawnMaxDelay + 1)
                                            : respawnMinDelay));
        }

        // Notify the spawn group, it rolls the replacement itself.
        if (group != null) {
            group.onUnitDeath(this);
        }
    }

    /**
     * Create the initial spawning and set _doRespawn to False, if respawn time set to 0, or set it to True otherwise.
     * @return The number of Npc that were spawned
     */
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

    /**
     * Set _doRespawn to False to stop respawn in this Spawn.
     */
    public void stopRespawn() {
        respawnEnabled = false;
    }

    /**
     * Set _doRespawn to True to start or restart respawn in this Spawn.
     */
    public void startRespawn() {
        respawnEnabled = true;
    }

    public Npc doSpawn() {
        return respawnEnabled ? doSpawn(false) : null;
    }

    /**
     * Create the Npc, add it to the world and lauch its OnSpawn action.<br>
     * <br>
     * <b><u>Concept</u>:</b><br>
     * <br>
     * Npc can be spawned either in a random position into a location area (if Lox=0 and Locy=0), either at an exact position.<br>
     * The heading of the Npc can be a random heading if not defined (value= -1) or an exact heading (ex : merchant...).<br>
     * <br>
     * <b><u>Actions for an random spawn into location area</u>:<i> (if Locx=0 and Locy=0)</i></b>
     * <ul>
     * <li>Get Npc Init parameters and its generate an Identifier</li>
     * <li>Call the constructor of the Npc</li>
     * <li>Calculate the random position in the location area (if Locx=0 and Locy=0) or get its exact position from the Spawn</li>
     * <li>Set the position of the Npc</li>
     * <li>Set the HP and MP of the Npc to the max</li>
     * <li>Set the heading of the Npc (random heading if not defined : value=-1)</li>
     * <li>Link the Npc to this Spawn</li>
     * <li>Init other values of the Npc (ex : from its CreatureTemplate for INT, STR, DEX...) and add it in the world</li>
     * <li>Launch the action OnSpawn fo the Npc</li>
     * <li>Increase the current number of Npcs managed by this Spawn</li>
     * </ul>
     * @param isSummonSpawn
     * @return
     */
    public Npc doSpawn(boolean isSummonSpawn) {
        try {
            // Check if the spawn is not a Pet, Decoy or Trap spawn.
            if (template.isType("Pet") || template.isType("Decoy") || template.isType("Trap")) {
                currentCount++;
                return null;
            }

            // Call the constructor of the Npc.
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

    /**
     * @param npc
     * @return
     */
    private Npc initializeNpc(Npc npc) {
        // Make it alive
        npc.setDead(false);

        // Reset decay info
        npc.setDecayed(false);

        int newlocx = 0;
        int newlocy = 0;
        int newlocz = -10000;

        // If Locx and Locy are not defined, the Npc must be spawned in an area defined by location or spawn territory.
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
            // The Npc is spawned at the exact position (Lox, Locy, Locz).
            newlocx = spawnLocation != null ? spawnLocation.getX() : getX();
            newlocy = spawnLocation != null ? spawnLocation.getY() : getY();
            newlocz = spawnLocation != null ? spawnLocation.getZ() : getZ();

            if (spawnLocation == null) {
                spawnLocation = new Location(newlocx, newlocy, newlocz);
            }
        }

        // Check if npc is in water.
        final WaterZone water = ZoneManager.getInstance().getZone(newlocx, newlocy, newlocz, WaterZone.class);

        // If random spawn system is enabled.
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
            final ThreadLocalRandom random = ThreadLocalRandom.current();
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

        // Correct Z of monsters.
        if (npc.isMonster() && !npc.isFlying() && (water == null)) {
            // Do not correct Z distances greater than 300.
            final int geoZ = GeoEngine.getInstance().getHeight(newlocx, newlocy, newlocz);
            if (LocationUtil.calculateDistance(newlocx, newlocy, newlocz, newlocx, newlocy, geoZ, true, true) < 300) {
                newlocz = geoZ;
            }

            // Prevent new z exceeding spawn territory high z.
            if (spawnTerritory != null) {
                final int highZ = spawnTerritory.getHighZ();
                if (highZ < newlocz) {
                    newlocz = highZ;
                }
            }
        }

        npc.stopAllEffects();

        // Set the HP and MP of the Npc to the max.
        npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());

        // Clear script variables
        if (npc.hasVariables()) {
            npc.getVariables().getSet().clear();
        }

        // Set is not random walk default value.
        npc.setRandomWalking(randomWalking);

        // Set the heading of the Npc (random heading if not defined).
        if (getHeading() == -1) {
            npc.setHeading(ThreadLocalRandom.current().nextInt(61794));
        } else {
            npc.setHeading(getHeading());
        }

        // Reset summoner
        npc.setSummoner(null);

        // Reset summoned list
        npc.resetSummonedNpcs();

        // Link the Npc to this Spawn.
        npc.setSpawn(this);

        // Spawn NPC
        npc.spawnMe(newlocx, newlocy, newlocz);

        // Make sure info is broadcasted in instances.
        if (npc.isInInstance()) {
            npc.broadcastInfo();
        }

        notifyNpcSpawned(npc);

        // Check for overriden by spawnlist AIData.
        if (name != null) {
            NpcPersonalAIData.getInstance().initializeNpcParameters(npc, this, name);
        }

        spawnedNpcs.add(npc);

        // Increase the current number of Npcs managed by this Spawn.
        currentCount++;

        // Minions
        if (npc.isMonster() && NpcData.getMasterMonsterIDs().contains(npc.getId())) {
            npc.asMonster()
                    .getMinionList()
                    .spawnMinions(npc.getTemplate().getParameters().getMinionList("Privates"));
        }

        if (npc.isAttackable()) {
            npc.asAttackable().setChampion(false);
        }

        // Set champion on next spawn.
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
                && (ThreadLocalRandom.current().nextInt(100) < ChampionMonstersConfig.CHAMPION_FREQUENCY)) {
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

    /**
     * Set bounds for random calculation and delay for respawn
     * @param delay delay in seconds
     * @param randomInterval random interval in seconds
     */
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
            // oldNpc.refreshId();
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
