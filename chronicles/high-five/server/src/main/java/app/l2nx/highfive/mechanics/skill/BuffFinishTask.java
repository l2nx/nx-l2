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
package app.l2nx.highfive.mechanics.skill;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Mobius
 */
public class BuffFinishTask {
    private final Set<BuffInfo> buffInfos = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> task = null;
    private boolean stopped = false;

    private class BuffFinishRunnable implements Runnable {
        @Override
        public void run() {
            for (BuffInfo info : buffInfos) {
                final Creature effected = info.getEffected();
                if ((effected != null) && info.isExpired()) {
                    // Stop this exact buff once, a newer buff of the same skill must not be affected.
                    WorldPartition.loop().execute(() -> effected.getEffectList().remove(SkillFinishType.NORMAL, info));
                    removeBuffInfo(info);
                }
            }
        }
    }

    public synchronized void removeBuffInfo(BuffInfo info) {
        buffInfos.remove(info);

        if (buffInfos.isEmpty() && (task != null)) {
            task.cancel(true);
            task = null;
        }
    }

    public synchronized void addBuffInfo(BuffInfo info) {
        buffInfos.add(info);

        if ((task == null) && !stopped) {
            task = WorldPartition.loop().scheduleAtFixedRate(new BuffFinishRunnable(), 0, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void start() {
        stopped = false;

        if (!buffInfos.isEmpty() && (task == null)) {
            task = WorldPartition.loop().scheduleAtFixedRate(new BuffFinishRunnable(), 0, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        stopped = true;

        if (task != null) {
            task.cancel(true);
        }
    }
}
