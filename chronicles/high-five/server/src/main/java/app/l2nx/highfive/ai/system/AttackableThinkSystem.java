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
package app.l2nx.highfive.ai.system;

import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Mobius
 */
public class AttackableThinkSystem {
    private static final Set<Set<Attackable>> POOLS = ConcurrentHashMap.newKeySet();
    private static final int POOL_SIZE = 1000;
    private static final int TASK_DELAY = 1000;

    protected AttackableThinkSystem() {}

    private class AttackableThink implements Runnable {
        private final Set<Attackable> attackables;

        public AttackableThink(Set<Attackable> attackables) {
            this.attackables = attackables;
        }

        @Override
        public void run() {
            if (attackables.isEmpty()) {
                return;
            }

            CreatureAI ai;
            Attackable attackable;
            final Iterator<Attackable> iterator = attackables.iterator();
            while (iterator.hasNext()) {
                attackable = iterator.next();
                if (attackable.hasAI()) {
                    ai = attackable.getAI();
                    if (ai != null) {
                        ai.notifyActionThink();
                    } else {
                        iterator.remove();
                    }
                } else {
                    iterator.remove();
                }
            }
        }
    }

    public synchronized void add(Attackable attackable) {
        for (Set<Attackable> pool : POOLS) {
            if (pool.contains(attackable)) {
                return;
            }
        }

        for (Set<Attackable> pool : POOLS) {
            if (pool.size() < POOL_SIZE) {
                pool.add(attackable);
                return;
            }
        }

        final Set<Attackable> pool = ConcurrentHashMap.newKeySet(POOL_SIZE);
        pool.add(attackable);
        WorldPartition.loop()
                .scheduleAtFixedRate(new AttackableThink(pool), TASK_DELAY, TASK_DELAY, TimeUnit.MILLISECONDS);
        POOLS.add(pool);
    }

    public void remove(Attackable attackable) {
        for (Set<Attackable> pool : POOLS) {
            if (pool.remove(attackable)) {
                return;
            }
        }
    }

    public static AttackableThinkSystem getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AttackableThinkSystem INSTANCE = new AttackableThinkSystem();
    }
}
