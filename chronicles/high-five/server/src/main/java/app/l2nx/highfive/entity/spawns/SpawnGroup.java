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

import app.l2nx.highfive.taskmanagers.SpawnGroupTaskManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A group of NPC spawn entries sharing an entry selection policy, the runtime equivalent of a retail npcmaker.<br>
 * Reservations are made under the group monitor and the actual spawning happens outside of it.
 * @author Altur
 */
@Slf4j
public class SpawnGroup {

    @Getter
    private final String name;

    @Getter
    private final SpawnSelection selection;

    @Getter
    private final int maximumNpc;

    @Getter
    private final List<SpawnGroupEntry> entries = new ArrayList<>();

    private int aliveOrReserved = 0;

    @Getter
    @Setter
    private boolean active = true;

    public SpawnGroup(String name, SpawnSelection selection, int maximumNpc) {
        this.name = name;
        this.selection = selection;
        this.maximumNpc = maximumNpc;
    }

    public void addEntry(SpawnGroupEntry entry) {
        entries.add(entry);
    }

    public synchronized int getAliveOrReserved() {
        return aliveOrReserved;
    }

    /**
     * Reserves the given amount of units on an entry, honoring the entry quota and the group cap.<br>
     * The group cap is deliberately not checked for RANDOM selection, retail random_spawn never reads it.
     * @param entry the entry to reserve on
     * @param count the amount of units to reserve
     * @return {@code true} when the reservation was made, {@code false} when quota or cap would be exceeded
     */
    public synchronized boolean tryReserve(SpawnGroupEntry entry, int count) {
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

    /**
     * Releases previously reserved units, used when the actual spawn could not be created.
     * @param entry the entry to release from
     * @param count the amount of units to release
     */
    public synchronized void release(SpawnGroupEntry entry, int count) {
        entry.setAliveOrReserved(entry.getAliveOrReserved() - count);
        aliveOrReserved -= count;
    }

    /**
     * Performs the initial spawn of the group according to its selection policy.
     * @return the number of NPC units spawned
     */
    public int initialSpawn() {
        if (entries.isEmpty()) {
            log.warn("{} has no valid entries to spawn.", name);
            return 0;
        }

        int spawned = 0;
        switch (selection) {
            case ALL: {
                for (SpawnGroupEntry entry : entries) {
                    if (tryReserve(entry, entry.getTotal())) {
                        spawned += spawnUnits(entry, entry.getTotal());
                    }
                }
                break;
            }
            case RANDOM: {
                // A single roll over the entry index, the whole quota of the rolled entry spawns. Nothing is filled up
                // and the cap is not read.
                final SpawnGroupEntry entry =
                        entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
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
                            entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
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
     * Handles the death of a unit of this group.<br>
     * A new entry is rolled ignoring which one died; when the rolled entry is at its quota nothing is replaced this cycle.
     * @param spawn the spawn slot the dead unit belonged to
     */
    public void onUnitDeath(Spawn spawn) {
        if (selection == SpawnSelection.ALL) {
            // ALL slots respawn themselves with their fixed species, the unit stays reserved.
            return;
        }

        final SpawnGroupEntry deadEntry = getEntryBySlot(spawn);
        if (deadEntry == null) {
            return;
        }

        synchronized (this) {
            deadEntry.setAliveOrReserved(deadEntry.getAliveOrReserved() - 1);
            aliveOrReserved--;
        }

        if (!active) {
            // Deactivated groups keep their census truthful but stop replacing dead units.
            return;
        }

        final SpawnGroupEntry rolled = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
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
                            System.currentTimeMillis()
                                    + (respawnRandom > 0
                                            ? ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1)
                                            : minDelay));
        }
    }

    /**
     * @param spawn the spawn slot to look up
     * @return the entry owning the given slot, or {@code null} when it belongs to another group
     */
    public SpawnGroupEntry getEntryBySlot(Spawn spawn) {
        for (SpawnGroupEntry entry : entries) {
            for (Spawn slot : entry.getSlots()) {
                // Identity, not Location equality: slot coordinates mutate and can coincide.
                if (slot == spawn) {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Materializes the given amount of already reserved units of an entry, rolling a territory slot per unit.
     * @param entry the entry to spawn from
     * @param count the amount of units to spawn
     * @return the number of NPC units spawned
     */
    public int spawnUnits(SpawnGroupEntry entry, int count) {
        if (!active) {
            // A pending replacement dequeued while the group was being stopped must not materialize.
            release(entry, count);
            return 0;
        }

        int spawned = 0;
        final List<Spawn> slots = entry.getSlots();
        for (int i = 0; i < count; i++) {
            final Spawn slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
            if (slot.doSpawn(false) == null) {
                release(entry, 1);
            } else {
                spawned++;
            }
        }

        return spawned;
    }
}
