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
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.SpawnGroupTaskManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * A group of NPC spawn entries sharing an entry selection policy, the runtime equivalent of a retail npcmaker.
 * @author Altur
 */
@Slf4j
@SingleWriter
public class SpawnGroup {
    private final String name;
    private final SpawnSelection selection;
    private final int maximumNpc;
    private final ObjectArrayList<SpawnGroupEntry> entries = new ObjectArrayList<>();
    private int aliveOrReserved = 0;
    private boolean active = true;

    public SpawnGroup(String name, SpawnSelection selection, int maximumNpc) {
        this.name = name;
        this.selection = selection;
        this.maximumNpc = maximumNpc;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public SpawnSelection getSelection() {
        WorldPartition.checkOwner();
        return selection;
    }

    public int getMaximumNpc() {
        WorldPartition.checkOwner();
        return maximumNpc;
    }

    public boolean isActive() {
        WorldPartition.checkOwner();
        return active;
    }

    public void setActive(boolean active) {
        WorldPartition.checkOwner();
        this.active = active;
    }

    public List<SpawnGroupEntry> getEntries() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(entries));
    }

    public void addEntry(SpawnGroupEntry entry) {
        WorldPartition.checkOwner();
        entries.add(entry);
    }

    public int getAliveOrReserved() {
        WorldPartition.checkOwner();
        return aliveOrReserved;
    }

    /**
     * The group cap is deliberately not checked for RANDOM selection, retail random_spawn never reads it.
     * @return {@code false} when the entry quota or the group cap would be exceeded
     */
    public boolean tryReserve(SpawnGroupEntry entry, int count) {
        WorldPartition.checkOwner();
        if ((selection != SpawnSelection.RANDOM) && (maximumNpc > 0) && ((aliveOrReserved + count) > maximumNpc)) {
            return false;
        }

        if ((entry.getAliveOrReserved() + count) > entry.getTotal()) {
            return false;
        }

        entry.setAliveOrReserved(entry.getAliveOrReserved() + count);
        aliveOrReserved += count;
        return true;
    }

    public void release(SpawnGroupEntry entry, int count) {
        WorldPartition.checkOwner();
        entry.setAliveOrReserved(entry.getAliveOrReserved() - count);
        aliveOrReserved -= count;
    }

    public int initialSpawn() {
        WorldPartition.checkOwner();
        if (entries.isEmpty()) {
            log.warn("{} has no valid entries to spawn.", name);
            return 0;
        }

        int spawned = 0;
        switch (selection) {
            case ALL: {
                try (ScratchBuffers.Snapshot<SpawnGroupEntry> walk = ScratchBuffers.snapshot(entries)) {
                    for (SpawnGroupEntry entry : walk) {
                        if (tryReserve(entry, entry.getTotal())) {
                            spawned += spawnUnits(entry, entry.getTotal());
                        }
                    }
                }
                break;
            }
            case RANDOM: {
                // A single roll over the entry index, the whole quota of the rolled entry spawns. Nothing is filled up
                // and the cap is not read.
                final SpawnGroupEntry entry = entries.get(ClockBridge.random().nextInt(entries.size()));
                if (tryReserve(entry, entry.getTotal())) {
                    spawned += spawnUnits(entry, entry.getTotal());
                }
                break;
            }
            case RANDOM_FILL: {
                // The cap is the roll counter. Rolls use replacement and no retry, a roll into a full entry is simply
                // lost.
                for (int i = 0; i < maximumNpc; i++) {
                    final SpawnGroupEntry entry =
                            entries.get(ClockBridge.random().nextInt(entries.size()));
                    if (tryReserve(entry, 1)) {
                        spawned += spawnUnits(entry, 1);
                    }
                }
                break;
            }
        }

        return spawned;
    }

    /**
     * A new entry is rolled ignoring which one died; when the rolled entry is at its quota nothing is replaced this
     * cycle.
     */
    public void onUnitDeath(Spawn spawn) {
        WorldPartition.checkOwner();
        if (selection == SpawnSelection.ALL) {
            // ALL slots respawn themselves with their fixed species, the unit stays reserved.
            return;
        }

        final SpawnGroupEntry deadEntry = getEntryBySlot(spawn);
        if (deadEntry == null) {
            return;
        }

        deadEntry.setAliveOrReserved(deadEntry.getAliveOrReserved() - 1);
        aliveOrReserved--;

        if (!active) {
            // Deactivated groups keep their census truthful but stop replacing dead units.
            return;
        }

        final SpawnGroupEntry rolled = entries.get(ClockBridge.random().nextInt(entries.size()));
        if (tryReserve(rolled, 1)) {
            // Reserve now, materialize later. The delay comes from the rolled entry, not from the dead one.
            final int respawnDelay = rolled.getRespawnDelay();
            final int respawnRandom = rolled.getRespawnRandom();
            final int minDelay = Math.max(1, respawnDelay - respawnRandom) * 1000;
            final int maxDelay = Math.max(1, respawnDelay + respawnRandom) * 1000;
            SpawnGroupTaskManager.getInstance()
                    .add(
                            this,
                            rolled,
                            ClockBridge.gameMillis()
                                    + (respawnRandom > 0
                                            ? ClockBridge.random().nextInt(minDelay, maxDelay + 1)
                                            : minDelay));
        }
    }

    /** @return {@code null} when the slot belongs to another group */
    public SpawnGroupEntry getEntryBySlot(Spawn spawn) {
        WorldPartition.checkOwner();
        for (SpawnGroupEntry entry : entries) {
            if (entry.hasSlot(spawn)) {
                return entry;
            }
        }

        return null;
    }

    /** Materializes already reserved units, rolling a territory slot per unit. */
    public int spawnUnits(SpawnGroupEntry entry, int count) {
        WorldPartition.checkOwner();
        if (!active) {
            // A pending replacement dequeued while the group was being stopped must not materialize.
            release(entry, count);
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            final Spawn slot = entry.getSlot(ClockBridge.random().nextInt(entry.getSlotCount()));
            if (slot.doSpawn(false) == null) {
                release(entry, 1);
            } else {
                spawned++;
            }
        }

        return spawned;
    }
}
