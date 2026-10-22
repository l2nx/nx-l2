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
package app.l2nx.highfive.taskmanagers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.spawns.SpawnGroup;
import app.l2nx.highfive.entity.spawns.SpawnGroupEntry;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Materializes pending spawn group replacements whose units were already reserved on death.<br>
 * A queue is used instead of a map because the same entry can have several replacements pending.
 * @author Altur
 */
public class SpawnGroupTaskManager implements Runnable {
    private static final Queue<PendingGroupSpawn> PENDING_SPAWNS = new ConcurrentLinkedQueue<>();
    private static boolean working = false;

    protected SpawnGroupTaskManager() {
        WorldPartition.loop().scheduleAtFixedRate(this, 0, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        if (working) {
            return;
        }

        working = true;

        if (!PENDING_SPAWNS.isEmpty()) {
            final long currentTime = ClockBridge.gameMillis();
            final Iterator<PendingGroupSpawn> iterator = PENDING_SPAWNS.iterator();
            while (iterator.hasNext()) {
                final PendingGroupSpawn pending = iterator.next();
                if (currentTime > pending.time) {
                    iterator.remove();
                    pending.group.spawnUnits(pending.entry, 1);
                }
            }
        }

        working = false;
    }

    public void add(SpawnGroup group, SpawnGroupEntry entry, long time) {
        PENDING_SPAWNS.add(new PendingGroupSpawn(group, entry, time));
    }

    public void clear() {
        PENDING_SPAWNS.clear();
    }

    private static class PendingGroupSpawn {
        final SpawnGroup group;
        final SpawnGroupEntry entry;
        final long time;

        PendingGroupSpawn(SpawnGroup group, SpawnGroupEntry entry, long time) {
            this.group = group;
            this.entry = entry;
            this.time = time;
        }
    }

    public static SpawnGroupTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final SpawnGroupTaskManager INSTANCE = new SpawnGroupTaskManager();
    }
}
