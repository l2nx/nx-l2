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
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;

/**
 * A single NPC entry of a spawn group, holding its quota and one spawn slot per territory.
 * @author Altur
 */
@SingleWriter
public class SpawnGroupEntry {
    private final int total;
    private final int respawnDelay;
    private final int respawnRandom;
    private final ObjectArrayList<Spawn> slots = new ObjectArrayList<>(1);
    private int aliveOrReserved = 0;

    public SpawnGroupEntry(int total, int respawnDelay, int respawnRandom) {
        this.total = total;
        this.respawnDelay = respawnDelay;
        this.respawnRandom = respawnRandom;
    }

    public int getTotal() {
        WorldPartition.checkOwner();
        return total;
    }

    public int getRespawnDelay() {
        WorldPartition.checkOwner();
        return respawnDelay;
    }

    public int getRespawnRandom() {
        WorldPartition.checkOwner();
        return respawnRandom;
    }

    public int getAliveOrReserved() {
        WorldPartition.checkOwner();
        return aliveOrReserved;
    }

    public void setAliveOrReserved(int aliveOrReserved) {
        WorldPartition.checkOwner();
        this.aliveOrReserved = aliveOrReserved;
    }

    public void addSlot(Spawn spawn) {
        WorldPartition.checkOwner();
        slots.add(spawn);
    }

    public List<Spawn> getSlots() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(slots));
    }

    public int getSlotCount() {
        WorldPartition.checkOwner();
        return slots.size();
    }

    public Spawn getSlot(int index) {
        WorldPartition.checkOwner();
        return slots.get(index);
    }

    public boolean hasSlot(Spawn spawn) {
        WorldPartition.checkOwner();
        return slots.contains(spawn);
    }
}
