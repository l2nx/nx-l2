/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.entity.spawns;

import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto Spawn handler.<br>
 * Allows spawning of a NPC object based on a timer.<br>
 * (From the official idea used for the Merchant and Blacksmith of Mammon)<br>
 * General Usage: - Call registerSpawn() with the parameters listed below.<br>
 * int npcId int[][] spawnPoints or specify NULL to add points later.<br>
 * int initialDelay (If < 0 = default value) int respawnDelay (If < 0 = default value)<br>
 * int despawnDelay (If < 0 = default value or if = 0, function disabled)<br>
 * spawnPoints is a standard two-dimensional int array containing X,Y and Z coordinates.<br>
 * The default respawn/despawn delays are currently every hour (as for Mammon on official servers).<br>
 * The resulting AutoSpawnInstance object represents the newly added spawn index.<br>
 * The internal methods of this object can be used to adjust random spawning, for instance a call to setRandomSpawn(1, true); would set the spawn at index 1 to be randomly rather than sequentially-based.<br>
 * Also they can be used to specify the number of NPC instances to spawn using setSpawnCount(), and broadcast a message to all users using setBroadcast().<br>
 * Random Spawning = OFF by default Broadcasting = OFF by default
 * @author Tempy
 */
@Slf4j
public class AutoSpawnHandler {

    private static final int DEFAULT_INITIAL_SPAWN = 30000; // 30 seconds after registration
    private static final int DEFAULT_RESPAWN = 3600000; // 1 hour in millisecs
    private static final int DEFAULT_DESPAWN = 3600000; // 1 hour in millisecs

    protected Map<Integer, AutoSpawnInstance> registeredSpawns = new ConcurrentHashMap<>();
    protected Map<Integer, ScheduledFuture<?>> runningSpawns = new ConcurrentHashMap<>();

    protected boolean activeState = true;

    protected AutoSpawnHandler() {
        restoreSpawnData();
    }

    public int size() {
        return registeredSpawns.size();
    }

    public void reload() {
        // unload
        unload();

        // create clean list
        registeredSpawns.clear();
        runningSpawns.clear();

        // load
        restoreSpawnData();
    }

    public void unload() {
        // stop all timers
        for (ScheduledFuture<?> sf : runningSpawns.values()) {
            if (sf != null) {
                sf.cancel(true);
            }
        }

        // unregister all registered spawns
        for (AutoSpawnInstance asi : registeredSpawns.values()) {
            if (asi != null) {
                removeSpawn(asi);
            }
        }
    }

    private void restoreSpawnData() {
        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs = s.executeQuery("SELECT * FROM random_spawn ORDER BY groupId ASC");
                PreparedStatement ps = con.prepareStatement("SELECT * FROM random_spawn_loc WHERE groupId=?")) {
            // Restore spawn group data, then the location data.
            while (rs.next()) {
                // Register random spawn group, set various options on the created spawn instance.
                final AutoSpawnInstance spawnInst = registerSpawn(
                        rs.getInt("npcId"),
                        rs.getInt("initialDelay"),
                        rs.getInt("respawnDelay"),
                        rs.getInt("despawnDelay"));
                spawnInst.setSpawnCount(rs.getInt("count"));
                spawnInst.setBroadcast(Boolean.parseBoolean(rs.getString("broadcastSpawn")));
                spawnInst.setRandomSpawn(Boolean.parseBoolean(rs.getString("randomSpawn")));

                // Restore the spawn locations for this spawn group/instance.
                ps.setInt(1, rs.getInt("groupId"));
                try (ResultSet rs2 = ps.executeQuery()) {
                    ps.clearParameters();
                    while (rs2.next()) {
                        // Add each location to the spawn group/instance.
                        spawnInst.addSpawnLocation(
                                rs2.getInt("x"), rs2.getInt("y"), rs2.getInt("z"), rs2.getInt("heading"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not restore spawn data: {}", e.getMessage(), e);
        }
    }

    /**
     * Registers a spawn with the given parameters with the spawner, and marks it as active.<br>
     * Returns a AutoSpawnInstance containing info about the spawn.
     * @param npcId
     * @param spawnPoints
     * @param initialDelay (If < 0 = default value)
     * @param respawnDelay (If < 0 = default value)
     * @param despawnDelay (If < 0 = default value or if = 0, function disabled)
     * @return AutoSpawnInstance spawnInst
     */
    public AutoSpawnInstance registerSpawn(
            int npcId, int[][] spawnPoints, int initialDelay, int respawnDelay, int despawnDelay) {
        final AutoSpawnInstance newSpawn = new AutoSpawnInstance(
                npcId,
                initialDelay < 0 ? DEFAULT_INITIAL_SPAWN : initialDelay,
                respawnDelay < 0 ? DEFAULT_RESPAWN : respawnDelay,
                despawnDelay < 0 ? DEFAULT_DESPAWN : despawnDelay);
        if (spawnPoints != null) {
            for (int[] spawnPoint : spawnPoints) {
                newSpawn.addSpawnLocation(spawnPoint);
            }
        }

        final int newId = IdManager.getInstance().getNextId();
        newSpawn.objectId = newId;
        registeredSpawns.put(newId, newSpawn);

        setSpawnActive(newSpawn, true);
        return newSpawn;
    }

    /**
     * Registers a spawn with the given parameters with the spawner, and marks it as active.<br>
     * Returns a AutoSpawnInstance containing info about the spawn.<br>
     * <b>Warning:</b> Spawn locations must be specified separately using addSpawnLocation().
     * @param npcId
     * @param initialDelay (If < 0 = default value)
     * @param respawnDelay (If < 0 = default value)
     * @param despawnDelay (If < 0 = default value or if = 0, function disabled)
     * @return AutoSpawnInstance spawnInst
     */
    public AutoSpawnInstance registerSpawn(int npcId, int initialDelay, int respawnDelay, int despawnDelay) {
        return registerSpawn(npcId, null, initialDelay, respawnDelay, despawnDelay);
    }

    /**
     * Remove a registered spawn from the list, specified by the given spawn instance.
     * @param spawnInst
     * @return boolean removedSuccessfully
     */
    public boolean removeSpawn(AutoSpawnInstance spawnInst) {
        if (!isSpawnRegistered(spawnInst)) {
            return false;
        }

        try {
            // Try to remove from the list of registered spawns if it exists.
            registeredSpawns.remove(spawnInst.getId());

            // Cancel the currently associated running scheduled task.
            final ScheduledFuture<?> task = runningSpawns.remove(spawnInst.objectId);
            if (task != null) {
                task.cancel(false);
            }
        } catch (Exception e) {
            log.warn(
                    "Could not auto spawn for NPC ID {} (Object ID = {}): {}",
                    spawnInst.id,
                    spawnInst.objectId,
                    e.getMessage(),
                    e);
            return false;
        }

        return true;
    }

    /**
     * Remove a registered spawn from the list, specified by the given spawn object ID.
     * @param objectId
     */
    public void removeSpawn(int objectId) {
        removeSpawn(registeredSpawns.get(objectId));
    }

    /**
     * Sets the active state of the specified spawn.
     * @param spawnInst
     * @param isActive
     */
    public void setSpawnActive(AutoSpawnInstance spawnInst, boolean isActive) {
        if (spawnInst == null) {
            return;
        }

        final int objectId = spawnInst.objectId;
        if (!isSpawnRegistered(objectId)) {
            return;
        }

        ScheduledFuture<?> spawnTask = null;
        if (isActive) {
            final AutoSpawner rs = new AutoSpawner(objectId);
            spawnTask = spawnInst.despawnDelay > 0
                    ? ThreadPool.scheduleAtFixedRate(rs, spawnInst.initialDelay, spawnInst.respawnDelay)
                    : ThreadPool.schedule(rs, spawnInst.initialDelay);
            runningSpawns.put(objectId, spawnTask);
        } else {
            final AutoDespawner rd = new AutoDespawner(objectId);
            spawnTask = runningSpawns.remove(objectId);
            if (spawnTask != null) {
                spawnTask.cancel(false);
            }

            ThreadPool.schedule(rd, 0);
        }

        spawnInst.setSpawnActive(isActive);
    }

    /**
     * Sets the active state of all auto spawn instances to that specified, and cancels the scheduled spawn task if necessary.
     * @param isActive
     */
    public void setAllActive(boolean isActive) {
        if (activeState == isActive) {
            return;
        }

        for (AutoSpawnInstance spawnInst : registeredSpawns.values()) {
            setSpawnActive(spawnInst, isActive);
        }

        activeState = isActive;
    }

    /**
     * Returns the number of milliseconds until the next occurrence of the given spawn.
     * @param spawnInst
     * @return
     */
    public long getTimeToNextSpawn(AutoSpawnInstance spawnInst) {
        final int objectId = spawnInst.getObjectId();
        return !isSpawnRegistered(objectId)
                ? -1
                : runningSpawns.containsKey(objectId)
                        ? runningSpawns.get(objectId).getDelay(TimeUnit.MILLISECONDS)
                        : 0;
    }

    /**
     * Attempts to return the AutoSpawnInstance associated with the given NPC or Object ID type.<br>
     * Note: If isObjectId == false, returns first instance for the specified NPC ID.
     * @param id
     * @param isObjectId
     * @return AutoSpawnInstance spawnInst
     */
    public AutoSpawnInstance getAutoSpawnInstance(int id, boolean isObjectId) {
        if (isObjectId) {
            if (isSpawnRegistered(id)) {
                return registeredSpawns.get(id);
            }
        } else {
            for (AutoSpawnInstance spawnInst : registeredSpawns.values()) {
                if (spawnInst.getId() == id) {
                    return spawnInst;
                }
            }
        }

        return null;
    }

    public List<AutoSpawnInstance> getAutoSpawnInstances(int npcId) {
        final List<AutoSpawnInstance> result = new ArrayList<>();
        for (AutoSpawnInstance spawnInst : registeredSpawns.values()) {
            if (spawnInst.getId() == npcId) {
                result.add(spawnInst);
            }
        }

        return result;
    }

    /**
     * Tests if the specified object ID is assigned to an auto spawn.
     * @param objectId
     * @return isAssigned
     */
    public boolean isSpawnRegistered(int objectId) {
        return registeredSpawns.containsKey(objectId);
    }

    /**
     * Tests if the specified spawn instance is assigned to an auto spawn.
     * @param spawnInst
     * @return boolean isAssigned
     */
    public boolean isSpawnRegistered(AutoSpawnInstance spawnInst) {
        return registeredSpawns.containsValue(spawnInst);
    }

    /**
     * AutoSpawner class<br>
     * This handles the main spawn task for an auto spawn instance, and initializes a despawner if required.
     */
    private class AutoSpawner implements Runnable {
        private final int objectId;

        protected AutoSpawner(int objectId) {
            this.objectId = objectId;
        }

        @Override
        public void run() {
            try {
                // Retrieve the required spawn instance for this spawn task.
                final AutoSpawnInstance spawnInst = registeredSpawns.get(objectId);

                // If the spawn is not scheduled to be active, cancel the spawn task.
                if (!spawnInst.isSpawnActive()) {
                    return;
                }

                final List<Location> locationList = spawnInst.getLocationList();

                // If there are no set co-ordinates, cancel the spawn task.
                if (locationList.isEmpty()) {
                    log.info("No location co-ords specified for spawn instance (Object ID = {}).", objectId);
                    return;
                }

                final int locationCount = locationList.size();
                int locationIndex = ThreadLocalRandom.current().nextInt(locationCount);

                // If random spawning is disabled, the spawn at the next set of co-ordinates after the last.
                // If the index is greater than the number of possible spawns, reset the counter to zero.
                if (!spawnInst.isRandomSpawn()) {
                    locationIndex = spawnInst.lastLocIndex + 1;
                    if (locationIndex == locationCount) {
                        locationIndex = 0;
                    }

                    spawnInst.lastLocIndex = locationIndex;
                }

                // Set the X, Y and Z co-ordinates, where this spawn will take place.
                final int x = locationList.get(locationIndex).getX();
                final int y = locationList.get(locationIndex).getY();
                final int z = locationList.get(locationIndex).getZ();
                final int heading = locationList.get(locationIndex).getHeading();
                final Spawn newSpawn = new Spawn(spawnInst.getId());
                newSpawn.setXYZ(x, y, z);
                if (heading != -1) {
                    newSpawn.setHeading(heading);
                }

                newSpawn.setAmount(spawnInst.getSpawnCount());
                if (spawnInst.despawnDelay == 0) {
                    newSpawn.setRespawnDelay(spawnInst.respawnDelay);
                }

                // Add the new spawn information to the spawn table, but do not store it.
                SpawnTable.getInstance().addSpawn(newSpawn);
                Npc npcInst = null;
                if (spawnInst.spawnCount == 1) {
                    npcInst = newSpawn.doSpawn();
                    npcInst.setXYZ(npcInst.getX(), npcInst.getY(), npcInst.getZ());
                    spawnInst.addNpc(npcInst);
                } else {
                    for (int i = 0; i < spawnInst.spawnCount; i++) {
                        npcInst = newSpawn.doSpawn();

                        // To prevent spawning of more than one NPC in the exact same spot, move it slightly by a small
                        // random offset.
                        npcInst.setXYZ(
                                npcInst.getX() + ThreadLocalRandom.current().nextInt(50),
                                npcInst.getY() + ThreadLocalRandom.current().nextInt(50),
                                npcInst.getZ());

                        // Add the NPC instance to the list of managed instances.
                        spawnInst.addNpc(npcInst);
                    }
                }

                newSpawn.stopRespawn();

                if (npcInst != null) {
                    if (spawnInst.isRandomSpawn()) {
                        while ((World.getFirstVisibleObjectInRange(npcInst, Npc.class, 5) != null)) {
                            // LOGGER.log(Level.INFO, "AutoSpawnHandler: Random spawn location " + npcInst.getLocation()
                            // + " for " + npcInst + " is occupied. Teleporting...");
                            npcInst.teleToLocation(
                                    locationList.get(ThreadLocalRandom.current().nextInt(locationList.size())));
                        }
                    }

                    if (spawnInst.isBroadcasting()) {
                        World.broadcastToAllOnlinePlayers("The " + npcInst.getName() + " has spawned near "
                                + MapRegionData.getInstance().getClosestTownName(npcInst) + "!");
                    }
                }

                // If there is no despawn time, do not create a despawn task.
                if (spawnInst.getDespawnDelay() > 0) {
                    ThreadPool.schedule(new AutoDespawner(objectId), spawnInst.getDespawnDelay() - 1000);
                }
            } catch (Exception e) {
                log.warn(
                        "An error occurred while initializing spawn instance (Object ID = {}): {}",
                        objectId,
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * AutoDespawner Class<br>
     * Simply used as a secondary class for despawning an auto spawn instance.
     */
    private class AutoDespawner implements Runnable {
        private final int objectId;

        protected AutoDespawner(int objectId) {
            this.objectId = objectId;
        }

        @Override
        public void run() {
            try {
                final AutoSpawnInstance spawnInst = registeredSpawns.get(objectId);
                if (spawnInst == null) {
                    log.info("No spawn registered for object ID = {}.", objectId);
                    return;
                }

                for (Npc npcInst : spawnInst.getNpcList()) {
                    if (npcInst == null) {
                        continue;
                    }

                    npcInst.deleteMe();
                    SpawnTable.getInstance().removeSpawn(npcInst.getSpawn());
                    spawnInst.removeNpc(npcInst);
                }
            } catch (Exception e) {
                log.warn("An error occurred while despawning spawn (Object ID = {}): {}", objectId, e.getMessage(), e);
            }
        }
    }

    /**
     * AutoSpawnInstance Class<br>
     * Stores information about a registered auto spawn.
     */
    public static class AutoSpawnInstance {
        @Getter
        protected int objectId;

        protected int spawnIndex;

        @Getter
        protected int id;

        @Getter
        protected int initialDelay;

        @Getter
        protected int respawnDelay;

        @Getter
        protected int despawnDelay;

        @Getter
        @Setter
        protected int spawnCount = 1;

        protected int lastLocIndex = -1;

        @Getter
        private final Queue<Npc> npcList = new ConcurrentLinkedQueue<>();

        @Getter
        private final List<Location> locationList = new CopyOnWriteArrayList<>();

        @Getter
        @Setter(AccessLevel.PROTECTED)
        private boolean spawnActive;

        @Getter
        @Setter
        private boolean randomSpawn = false;

        @Getter
        private boolean broadcasting = false;

        protected AutoSpawnInstance(int npcId, int initDelay, int respawnDelay, int despawnDelay) {
            id = npcId;
            initialDelay = initDelay;
            this.respawnDelay = respawnDelay;
            this.despawnDelay = despawnDelay;
        }

        protected boolean addNpc(Npc npcInst) {
            return npcList.add(npcInst);
        }

        protected boolean removeNpc(Npc npcInst) {
            return npcList.remove(npcInst);
        }

        public List<Spawn> getSpawns() {
            final List<Spawn> npcSpawns = new ArrayList<>();
            for (Npc npcInst : npcList) {
                npcSpawns.add(npcInst.getSpawn());
            }

            return npcSpawns;
        }

        public void setBroadcast(boolean broadcastValue) {
            broadcasting = broadcastValue;
        }

        public boolean addSpawnLocation(int x, int y, int z, int heading) {
            return locationList.add(new Location(x, y, z, heading));
        }

        public boolean addSpawnLocation(int[] spawnLoc) {
            return (spawnLoc.length == 3) && addSpawnLocation(spawnLoc[0], spawnLoc[1], spawnLoc[2], -1);
        }

        public Location removeSpawnLocation(int locIndex) {
            try {
                return locationList.remove(locIndex);
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        }
    }

    public static AutoSpawnHandler getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AutoSpawnHandler INSTANCE = new AutoSpawnHandler();
    }
}
