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
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mobius
 */
public class RespawnTaskManager implements Runnable {
    private static final Map<Npc, Long> PENDING_RESPAWNS = new ConcurrentHashMap<>();
    private static boolean working = false;

    protected RespawnTaskManager() {
        ThreadPool.scheduleAtFixedRate(this, 0, 1000);
    }

    @Override
    public void run() {
        if (working) {
            return;
        }

        working = true;

        if (!PENDING_RESPAWNS.isEmpty()) {
            final long currentTime = ClockBridge.gameMillis();
            final Iterator<Entry<Npc, Long>> iterator =
                    PENDING_RESPAWNS.entrySet().iterator();
            Entry<Npc, Long> entry;

            while (iterator.hasNext()) {
                entry = iterator.next();
                if (currentTime > entry.getValue()) {
                    iterator.remove();

                    final Npc npc = entry.getKey();
                    final Spawn spawn = npc.getSpawn();
                    if (spawn != null) {
                        spawn.respawnNpc(npc);
                        spawn.scheduledCount--;
                    }
                }
            }
        }

        working = false;
    }

    public void add(Npc npc, long time) {
        PENDING_RESPAWNS.put(npc, time);
    }

    public static RespawnTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final RespawnTaskManager INSTANCE = new RespawnTaskManager();
    }
}
