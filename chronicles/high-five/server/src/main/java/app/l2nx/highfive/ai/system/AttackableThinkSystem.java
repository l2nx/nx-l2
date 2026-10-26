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

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.periodic.PeriodicSystem;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.partition.WorldPartition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Mobius
 */
@Slf4j
public class AttackableThinkSystem {
    static final String SYSTEM = "ai-think";

    private final Set<Attackable> attackables = new LinkedHashSet<>();
    private final List<Attackable> thinking = new ArrayList<>();
    private final PeriodicSystem think = (loop, now) -> thinkAll();

    protected AttackableThinkSystem() {}

    /** The singleton outlives a world loop: a restarted world starts without the attackables of the previous one. */
    void install(Loop world, Duration cadence) {
        WorldPartition.checkOwner();
        attackables.clear();
        world.addSystem(SYSTEM, cadence, think);
    }

    // A think may add or remove attackables (a death, a spawn): the run walks a copy and skips the removed ones.
    private void thinkAll() {
        thinking.addAll(attackables);
        try {
            for (int i = 0; i < thinking.size(); i++) {
                final Attackable attackable = thinking.get(i);
                if (attackables.contains(attackable)) {
                    think(attackable);
                }
            }
        } finally {
            thinking.clear();
        }
    }

    public void add(Attackable attackable) {
        WorldPartition.checkOwner();
        attackables.add(attackable);
    }

    public void remove(Attackable attackable) {
        WorldPartition.checkOwner();
        attackables.remove(attackable);
    }

    private void think(Attackable attackable) {
        final CreatureAI ai = attackable.hasAI() ? attackable.getAI() : null;
        if (ai == null) {
            attackables.remove(attackable);
            return;
        }
        try {
            ai.notifyActionThink();
        } catch (RuntimeException e) {
            log.error("Think of {} failed", attackable, e);
        }
    }

    public static AttackableThinkSystem getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AttackableThinkSystem INSTANCE = new AttackableThinkSystem();
    }
}
