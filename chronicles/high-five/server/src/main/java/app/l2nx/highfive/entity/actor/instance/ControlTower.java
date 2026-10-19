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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Tower;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.spawns.Spawn;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Class for Control Tower instance.
 */
@Slf4j
public class ControlTower extends Tower {
    private Collection<Spawn> guards;

    /**
     * Creates a control tower.
     * @param template the control tower NPC template
     */
    public ControlTower(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.ControlTower);
    }

    @Override
    public boolean doDie(Creature killer) {
        if (getCastle().getSiege().isInProgress()) {
            getCastle().getSiege().killedCT();

            if ((guards != null) && !guards.isEmpty()) {
                for (Spawn spawn : guards) {
                    try {
                        spawn.stopRespawn();
                        // spawn.getLastSpawn().doDie(spawn.getLastSpawn());
                    } catch (Exception e) {
                        log.warn("Error at ControlTower", e);
                    }
                }

                guards.clear();
            }
        }

        return super.doDie(killer);
    }

    public void registerGuard(Spawn guard) {
        getGuards().add(guard);
    }

    private Collection<Spawn> getGuards() {
        if (guards == null) {
            synchronized (this) {
                if (guards == null) {
                    guards = ConcurrentHashMap.newKeySet();
                }
            }
        }

        return guards;
    }
}
