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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Mobius
 */
public class RandomAnimationTaskManager implements Runnable {
    private static final Map<Npc, Long> PENDING_ANIMATIONS = new ConcurrentHashMap<>();
    private static boolean working = false;

    protected RandomAnimationTaskManager() {
        ThreadPool.scheduleAtFixedRate(this, 0, 1000);
    }

    @Override
    public void run() {
        if (working) {
            return;
        }

        working = true;

        final long currentTime = System.currentTimeMillis();
        for (Entry<Npc, Long> entry : PENDING_ANIMATIONS.entrySet()) {
            if (currentTime > entry.getValue().longValue()) {
                final Npc npc = entry.getKey();
                if (npc.isInActiveRegion()
                        && !npc.isDead()
                        && !npc.isInCombat()
                        && !npc.isMoving()
                        && !npc.isStunned()
                        && !npc.isSleeping()
                        && !npc.isParalyzed()) {
                    npc.onRandomAnimation(ThreadLocalRandom.current().nextInt(2, 4));
                }

                PENDING_ANIMATIONS.put(npc, currentTime + getAnimationDelay(npc));
            }
        }

        working = false;
    }

    public void add(Npc npc) {
        if (npc.hasRandomAnimation()) {
            PENDING_ANIMATIONS.putIfAbsent(npc, System.currentTimeMillis() + getAnimationDelay(npc));
        }
    }

    private static long getAnimationDelay(Npc npc) {
        final int minDelay = npc.isAttackable() ? GeneralConfig.MIN_MONSTER_ANIMATION : GeneralConfig.MIN_NPC_ANIMATION;
        final int maxDelay = npc.isAttackable() ? GeneralConfig.MAX_MONSTER_ANIMATION : GeneralConfig.MAX_NPC_ANIMATION;
        return (minDelay < maxDelay ? ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1) : minDelay) * 1000;
    }

    public void remove(Npc npc) {
        PENDING_ANIMATIONS.remove(npc);
    }

    public static RandomAnimationTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final RandomAnimationTaskManager INSTANCE = new RandomAnimationTaskManager();
    }
}
