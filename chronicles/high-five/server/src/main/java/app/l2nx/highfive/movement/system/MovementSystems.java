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
package app.l2nx.highfive.movement.system;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.periodic.PeriodicSystem;
import app.l2nx.highfive.entity.actor.Creature;
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
public final class MovementSystems {
    static final String PLAYER_SYSTEM = "player-movement";
    static final String NPC_SYSTEM = "npc-movement";

    private final Set<Creature> players = new LinkedHashSet<>();
    private final Set<Creature> npcs = new LinkedHashSet<>();
    private final List<Creature> moving = new ArrayList<>();
    private final PeriodicSystem playerMovement = (loop, now) -> move(players);
    private final PeriodicSystem npcMovement = (loop, now) -> move(npcs);

    MovementSystems() {}

    /** Outlives a world loop, so a restart must not inherit the previous world's creatures. */
    void install(Loop world, Duration playerCadence, Duration npcCadence) {
        WorldPartition.checkOwner();
        players.clear();
        npcs.clear();
        world.addSystem(PLAYER_SYSTEM, playerCadence, playerMovement);
        world.addSystem(NPC_SYSTEM, npcCadence, npcMovement);
    }

    public void registerMovingObject(Creature creature) {
        WorldPartition.checkOwner();
        (creature.isPlayer() ? players : npcs).add(creature);
    }

    // An arrival may register the creature again or start another one moving: the run walks a copy. Systems run one
    // after another, so the shared copy is never in use by a second run.
    private void move(Set<Creature> creatures) {
        moving.addAll(creatures);
        try {
            for (int i = 0; i < moving.size(); i++) {
                final Creature creature = moving.get(i);
                if (creatures.contains(creature)) {
                    update(creatures, creature);
                }
            }
        } finally {
            moving.clear();
        }
    }

    private static void update(Set<Creature> creatures, Creature creature) {
        try {
            if (creature.updatePosition()) {
                creatures.remove(creature);
                creature.getAI().notifyActionArrived();
            }
        } catch (RuntimeException e) {
            creatures.remove(creature);
            log.warn("Problem updating position of {}", creature, e);
        }
    }
}
