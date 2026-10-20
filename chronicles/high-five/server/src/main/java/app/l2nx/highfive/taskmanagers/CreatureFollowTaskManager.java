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

import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mobius
 */
public class CreatureFollowTaskManager {
    protected static final Map<Creature, Integer> NORMAL_FOLLOW_CREATURES = new ConcurrentHashMap<>();
    protected static final Map<Creature, Integer> ATTACK_FOLLOW_CREATURES = new ConcurrentHashMap<>();
    protected static boolean workingNormal = false;
    protected static boolean workingAttack = false;

    protected CreatureFollowTaskManager() {
        ThreadPool.schedulePriorityTaskAtFixedRate(new CreatureFollowNormalTask(), 1000, 1000);
        ThreadPool.schedulePriorityTaskAtFixedRate(new CreatureFollowAttackTask(), 500, 500);
    }

    protected class CreatureFollowNormalTask implements Runnable {
        @Override
        public void run() {
            if (workingNormal) {
                return;
            }

            workingNormal = true;

            if (!NORMAL_FOLLOW_CREATURES.isEmpty()) {
                for (Entry<Creature, Integer> entry : NORMAL_FOLLOW_CREATURES.entrySet()) {
                    follow(entry.getKey(), entry.getValue());
                }
            }

            workingNormal = false;
        }
    }

    protected class CreatureFollowAttackTask implements Runnable {
        @Override
        public void run() {
            if (workingAttack) {
                return;
            }

            workingAttack = true;

            if (!ATTACK_FOLLOW_CREATURES.isEmpty()) {
                for (Entry<Creature, Integer> entry : ATTACK_FOLLOW_CREATURES.entrySet()) {
                    follow(entry.getKey(), entry.getValue());
                }
            }

            workingAttack = false;
        }
    }

    protected void follow(Creature creature, int range) {
        try {
            if (creature.hasAI()) {
                final CreatureAI ai = creature.getAI();
                if (ai != null) {
                    final WorldObject followTarget = ai.getFollowTarget();
                    if (followTarget == null) {
                        if (creature.isSummon()) {
                            creature.asSummon().setFollowStatus(false);
                        }

                        ai.setIntentionIdle();
                        return;
                    }

                    final int followRange = range == -1 ? ClockBridge.random().nextInt(50, 101) : range;
                    if (!creature.isInsideRadius3D(followTarget, followRange)) {
                        if (!creature.isInsideRadius3D(followTarget, 3000)) {
                            // If the target is too far (maybe also teleported).
                            if (creature.isSummon()) {
                                creature.asSummon().setFollowStatus(false);
                            }

                            ai.setIntentionIdle();
                            return;
                        }

                        ai.moveToPawn(followTarget, followRange);
                        // GeoEngine can reject or truncate a route without ever raising ARRIVED.
                        //  Revalidate rather than leave the action waiting on an event that never comes.
                        if (!creature.isMoving()
                                && ((ai.getIntention() == Intention.ATTACK) || (ai.getIntention() == Intention.CAST))) {
                            ai.notifyActionArrived();
                        }
                    } else if (!creature.isMoving()
                            && ((ai.getIntention() == Intention.ATTACK) || (ai.getIntention() == Intention.CAST))) {
                        // This task can observe arrival before the movement manager does.
                        ai.notifyActionArrived();
                    }
                } else {
                    remove(creature);
                }
            } else {
                remove(creature);
            }
        } catch (Exception e) {
            // Ignore.
        }
    }

    public boolean isFollowing(Creature creature) {
        return NORMAL_FOLLOW_CREATURES.containsKey(creature) || ATTACK_FOLLOW_CREATURES.containsKey(creature);
    }

    public void addNormalFollow(Creature creature, int range) {
        NORMAL_FOLLOW_CREATURES.putIfAbsent(creature, range);
        follow(creature, range);
    }

    public void addAttackFollow(Creature creature, int range) {
        ATTACK_FOLLOW_CREATURES.putIfAbsent(creature, range);
        follow(creature, range);
    }

    public void remove(Creature creature) {
        NORMAL_FOLLOW_CREATURES.remove(creature);
        ATTACK_FOLLOW_CREATURES.remove(creature);
    }

    public static CreatureFollowTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final CreatureFollowTaskManager INSTANCE = new CreatureFollowTaskManager();
    }
}
