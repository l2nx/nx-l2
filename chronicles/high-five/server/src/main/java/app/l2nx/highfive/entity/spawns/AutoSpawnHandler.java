/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.holders.AutoSpawnGroup;
import app.l2nx.highfive.data.holders.SpawnPoint;
import app.l2nx.highfive.data.xml.AutoSpawnData;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
@SingleWriter
public class AutoSpawnHandler {

    private static final int DEFAULT_INITIAL_SPAWN = 30000; // 30 seconds after registration
    private static final int DEFAULT_RESPAWN = 3600000; // 1 hour in millisecs
    private static final int DEFAULT_DESPAWN = 3600000; // 1 hour in millisecs

    // Registration order: a lookup by NPC id answers the first registered instance.
    private final Int2ObjectLinkedOpenHashMap<AutoSpawnInstance> registeredSpawns = new Int2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectLinkedOpenHashMap<ScheduledFuture<?>> runningSpawns = new Int2ObjectLinkedOpenHashMap<>();

    private boolean activeState = true;

    protected AutoSpawnHandler() {
        restoreSpawnData();
    }

    public int size() {
        WorldPartition.checkOwner();
        return registeredSpawns.size();
    }

    public void reload() {
        WorldPartition.checkOwner();
        unload();

        registeredSpawns.clear();
        runningSpawns.clear();

        restoreSpawnData();
    }

    public void unload() {
        WorldPartition.checkOwner();
        for (ScheduledFuture<?> sf : runningSpawns.values()) {
            if (sf != null) {
                sf.cancel(true);
            }
        }

        try (ScratchBuffers.Snapshot<AutoSpawnInstance> walk = ScratchBuffers.snapshot(registeredSpawns.values())) {
            for (AutoSpawnInstance asi : walk) {
                if (asi != null) {
                    removeSpawn(asi);
                }
            }
        }
    }

    private void restoreSpawnData() {
        for (AutoSpawnGroup group : AutoSpawnData.getInstance().getGroups()) {
            try {
                final AutoSpawnInstance spawnInst = registerSpawn(
                        group.npcId(),
                        toMillis(group.initialDelaySeconds()),
                        toMillis(group.respawnDelaySeconds()),
                        toMillis(group.despawnDelaySeconds()));
                spawnInst.setSpawnCount(group.count());
                spawnInst.setBroadcast(group.broadcastSpawn());
                spawnInst.setRandomSpawn(group.randomSpawn());
                for (SpawnPoint location : group.locations()) {
                    spawnInst.addSpawnLocation(location.x(), location.y(), location.z(), location.heading());
                }
            } catch (Exception e) {
                log.warn("Could not restore auto spawn of NPC {}: {}", group.npcId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Keeps a negative delay negative: it selects the handler default.
     */
    private static int toMillis(int seconds) {
        return seconds < 0 ? seconds : seconds * 1000;
    }

    /** A negative delay selects the handler default; a zero {@code despawnDelay} disables despawning. */
    public AutoSpawnInstance registerSpawn(
            int npcId, int[][] spawnPoints, int initialDelay, int respawnDelay, int despawnDelay) {
        WorldPartition.checkOwner();
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

    /** Spawn locations must be added separately through {@code addSpawnLocation}. */
    public AutoSpawnInstance registerSpawn(int npcId, int initialDelay, int respawnDelay, int despawnDelay) {
        WorldPartition.checkOwner();
        return registerSpawn(npcId, null, initialDelay, respawnDelay, despawnDelay);
    }

    public boolean removeSpawn(AutoSpawnInstance spawnInst) {
        WorldPartition.checkOwner();
        if (!isSpawnRegistered(spawnInst)) {
            return false;
        }

        registeredSpawns.remove(spawnInst.objectId);

        final ScheduledFuture<?> task = runningSpawns.remove(spawnInst.objectId);
        if (task != null) {
            task.cancel(false);
        }

        return true;
    }

    public void removeSpawn(int objectId) {
        WorldPartition.checkOwner();
        removeSpawn(registeredSpawns.get(objectId));
    }

    public void setSpawnActive(AutoSpawnInstance spawnInst, boolean isActive) {
        WorldPartition.checkOwner();
        if (spawnInst == null) {
            return;
        }

        final int objectId = spawnInst.objectId;
        if (!isSpawnRegistered(objectId)) {
            return;
        }

        ScheduledFuture<?> spawnTask = null;
        if (isActive) {
            // Seven Signs activates some spawns again without asking: an active spawn keeps its timer and its NPCs,
            // even a one-shot one whose timer already fired.
            if (spawnInst.spawnActive) {
                return;
            }

            final AutoSpawner rs = new AutoSpawner(objectId);
            spawnTask = spawnInst.despawnDelay > 0
                    ? WorldPartition.loop()
                            .scheduleAtFixedRate(
                                    rs, spawnInst.initialDelay, spawnInst.respawnDelay, TimeUnit.MILLISECONDS)
                    : WorldPartition.loop().schedule(rs, spawnInst.initialDelay, TimeUnit.MILLISECONDS);
            runningSpawns.put(objectId, spawnTask);
        } else {
            final AutoDespawner rd = new AutoDespawner(objectId);
            spawnTask = runningSpawns.remove(objectId);
            if (spawnTask != null) {
                spawnTask.cancel(false);
            }

            WorldPartition.loop().schedule(rd, 0, TimeUnit.MILLISECONDS);
        }

        spawnInst.spawnActive = isActive;
    }

    public void setAllActive(boolean isActive) {
        WorldPartition.checkOwner();
        if (activeState == isActive) {
            return;
        }

        try (ScratchBuffers.Snapshot<AutoSpawnInstance> walk = ScratchBuffers.snapshot(registeredSpawns.values())) {
            for (AutoSpawnInstance spawnInst : walk) {
                setSpawnActive(spawnInst, isActive);
            }
        }

        activeState = isActive;
    }

    /** @return milliseconds to the next spawn; -1 when not registered, 0 when no task is scheduled */
    public long getTimeToNextSpawn(AutoSpawnInstance spawnInst) {
        WorldPartition.checkOwner();
        final int objectId = spawnInst.objectId;
        if (!registeredSpawns.containsKey(objectId)) {
            return -1;
        }

        final ScheduledFuture<?> task = runningSpawns.get(objectId);
        return task != null ? task.getDelay(TimeUnit.MILLISECONDS) : 0;
    }

    /** With {@code isObjectId} false, {@code id} is an NPC id and the first registered instance answers. */
    public AutoSpawnInstance getAutoSpawnInstance(int id, boolean isObjectId) {
        WorldPartition.checkOwner();
        if (isObjectId) {
            return registeredSpawns.get(id);
        }

        for (AutoSpawnInstance spawnInst : registeredSpawns.values()) {
            if (spawnInst.id == id) {
                return spawnInst;
            }
        }

        return null;
    }

    public List<AutoSpawnInstance> getAutoSpawnInstances(int npcId) {
        WorldPartition.checkOwner();
        final List<AutoSpawnInstance> result = new ObjectArrayList<>();
        for (AutoSpawnInstance spawnInst : registeredSpawns.values()) {
            if (spawnInst.id == npcId) {
                result.add(spawnInst);
            }
        }

        return result;
    }

    public boolean isSpawnRegistered(int objectId) {
        WorldPartition.checkOwner();
        return registeredSpawns.containsKey(objectId);
    }

    public boolean isSpawnRegistered(AutoSpawnInstance spawnInst) {
        WorldPartition.checkOwner();
        return (spawnInst != null) && (registeredSpawns.get(spawnInst.objectId) == spawnInst);
    }

    @SingleWriter
    private class AutoSpawner implements Runnable {
        private final int objectId;

        protected AutoSpawner(int objectId) {
            this.objectId = objectId;
        }

        @Override
        public void run() {
            WorldPartition.checkOwner();
            try {
                final AutoSpawnInstance spawnInst = registeredSpawns.get(objectId);

                // A removed spawn or one not scheduled to be active spawns nothing.
                if ((spawnInst == null) || !spawnInst.spawnActive) {
                    return;
                }

                final List<Location> locationList = spawnInst.locationList;

                if (locationList.isEmpty()) {
                    log.info("No location co-ords specified for spawn instance (Object ID = {}).", objectId);
                    return;
                }

                final int locationCount = locationList.size();
                int locationIndex = ClockBridge.random().nextInt(locationCount);

                if (!spawnInst.randomSpawn) {
                    locationIndex = spawnInst.lastLocIndex + 1;
                    if (locationIndex == locationCount) {
                        locationIndex = 0;
                    }

                    spawnInst.lastLocIndex = locationIndex;
                }

                final int x = locationList.get(locationIndex).getX();
                final int y = locationList.get(locationIndex).getY();
                final int z = locationList.get(locationIndex).getZ();
                final int heading = locationList.get(locationIndex).getHeading();
                final Spawn newSpawn = new Spawn(spawnInst.id);
                newSpawn.setXYZ(x, y, z);
                newSpawn.setHeading(heading);

                newSpawn.setAmount(spawnInst.spawnCount);
                if (spawnInst.despawnDelay == 0) {
                    newSpawn.setRespawnDelay(spawnInst.respawnDelay / 1000);
                }

                SpawnTable.getInstance().addSpawn(newSpawn);
                Npc npcInst = null;
                if (spawnInst.spawnCount == 1) {
                    npcInst = newSpawn.doSpawn();
                    if (npcInst != null) {
                        npcInst.setXYZ(npcInst.getX(), npcInst.getY(), npcInst.getZ());
                        spawnInst.npcList.add(npcInst);
                    }
                } else {
                    for (int i = 0; i < spawnInst.spawnCount; i++) {
                        final Npc spawned = newSpawn.doSpawn();
                        if (spawned == null) {
                            continue;
                        }

                        // Offset so that NPCs do not stack on the exact same spot.
                        spawned.setXYZ(
                                spawned.getX() + ClockBridge.random().nextInt(50),
                                spawned.getY() + ClockBridge.random().nextInt(50),
                                spawned.getZ());

                        spawnInst.npcList.add(spawned);
                        npcInst = spawned;
                    }
                }

                newSpawn.stopRespawn();

                if (npcInst != null) {
                    if (spawnInst.randomSpawn) {
                        while ((World.getFirstVisibleObjectInRange(npcInst, Npc.class, 5) != null)) {
                            npcInst.teleToLocation(
                                    locationList.get(ClockBridge.random().nextInt(locationList.size())));
                        }
                    }

                    if (spawnInst.broadcasting) {
                        World.broadcastToAllOnlinePlayers("The " + npcInst.getName() + " has spawned near "
                                + MapRegionData.getInstance().getClosestTownName(npcInst) + "!");
                    }
                }

                if (spawnInst.despawnDelay > 0) {
                    WorldPartition.loop()
                            .schedule(
                                    new AutoDespawner(objectId), spawnInst.despawnDelay - 1000, TimeUnit.MILLISECONDS);
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

    @SingleWriter
    private class AutoDespawner implements Runnable {
        private final int objectId;

        protected AutoDespawner(int objectId) {
            this.objectId = objectId;
        }

        @Override
        public void run() {
            WorldPartition.checkOwner();
            try {
                final AutoSpawnInstance spawnInst = registeredSpawns.get(objectId);
                if (spawnInst == null) {
                    log.info("No spawn registered for object ID = {}.", objectId);
                    return;
                }

                try (ScratchBuffers.Snapshot<Npc> walk = ScratchBuffers.snapshot(spawnInst.npcList)) {
                    for (Npc npcInst : walk) {
                        if (npcInst == null) {
                            continue;
                        }

                        npcInst.deleteMe();
                        SpawnTable.getInstance().removeSpawn(npcInst.getSpawn());
                        spawnInst.npcList.remove(npcInst);
                    }
                }
            } catch (Exception e) {
                log.warn("An error occurred while despawning spawn (Object ID = {}): {}", objectId, e.getMessage(), e);
            }
        }
    }

    @SingleWriter
    public static class AutoSpawnInstance {
        private int objectId;
        private final int id;
        private final int initialDelay;
        private final int respawnDelay;
        private final int despawnDelay;
        private int spawnCount = 1;
        private int lastLocIndex = -1;
        private final ObjectArrayList<Npc> npcList = new ObjectArrayList<>();
        private final ObjectArrayList<Location> locationList = new ObjectArrayList<>();
        private boolean spawnActive;
        private boolean randomSpawn = false;
        private boolean broadcasting = false;

        protected AutoSpawnInstance(int npcId, int initDelay, int respawnDelay, int despawnDelay) {
            id = npcId;
            initialDelay = initDelay;
            this.respawnDelay = respawnDelay;
            this.despawnDelay = despawnDelay;
        }

        public int getObjectId() {
            WorldPartition.checkOwner();
            return objectId;
        }

        public int getId() {
            WorldPartition.checkOwner();
            return id;
        }

        public int getInitialDelay() {
            WorldPartition.checkOwner();
            return initialDelay;
        }

        public int getRespawnDelay() {
            WorldPartition.checkOwner();
            return respawnDelay;
        }

        public int getDespawnDelay() {
            WorldPartition.checkOwner();
            return despawnDelay;
        }

        public int getSpawnCount() {
            WorldPartition.checkOwner();
            return spawnCount;
        }

        public void setSpawnCount(int spawnCount) {
            WorldPartition.checkOwner();
            this.spawnCount = spawnCount;
        }

        public boolean isSpawnActive() {
            WorldPartition.checkOwner();
            return spawnActive;
        }

        public boolean isRandomSpawn() {
            WorldPartition.checkOwner();
            return randomSpawn;
        }

        public void setRandomSpawn(boolean randomSpawn) {
            WorldPartition.checkOwner();
            this.randomSpawn = randomSpawn;
        }

        public boolean isBroadcasting() {
            WorldPartition.checkOwner();
            return broadcasting;
        }

        public void setBroadcast(boolean broadcastValue) {
            WorldPartition.checkOwner();
            broadcasting = broadcastValue;
        }

        public List<Npc> getNpcList() {
            WorldPartition.checkOwner();
            return ObjectLists.unmodifiable(new ObjectArrayList<>(npcList));
        }

        /** @return the earliest spawned NPC still managed, {@code null} when none is */
        public Npc getFirstNpc() {
            WorldPartition.checkOwner();
            return npcList.isEmpty() ? null : npcList.getFirst();
        }

        public List<Spawn> getSpawns() {
            WorldPartition.checkOwner();
            final List<Spawn> npcSpawns = new ObjectArrayList<>(npcList.size());
            for (Npc npcInst : npcList) {
                npcSpawns.add(npcInst.getSpawn());
            }

            return npcSpawns;
        }

        public boolean addSpawnLocation(int x, int y, int z, int heading) {
            WorldPartition.checkOwner();
            return locationList.add(new Location(x, y, z, heading));
        }

        public boolean addSpawnLocation(int[] spawnLoc) {
            WorldPartition.checkOwner();
            return (spawnLoc.length == 3) && addSpawnLocation(spawnLoc[0], spawnLoc[1], spawnLoc[2], -1);
        }

        public Location removeSpawnLocation(int locIndex) {
            WorldPartition.checkOwner();
            return (locIndex >= 0) && (locIndex < locationList.size()) ? locationList.remove(locIndex) : null;
        }
    }

    public static AutoSpawnHandler getInstance() {
        WorldPartition.checkOwner();
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AutoSpawnHandler INSTANCE = new AutoSpawnHandler();
    }
}
