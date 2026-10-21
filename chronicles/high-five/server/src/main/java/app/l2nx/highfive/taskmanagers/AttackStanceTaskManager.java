/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.taskmanagers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AutoAttackStop;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Attack stance task manager.
 * @author Luca Baldi
 */
@Slf4j
public class AttackStanceTaskManager implements Runnable {

    public static final long COMBAT_TIME = 15000;

    private static final Map<Creature, Long> CREATURE_ATTACK_STANCES = new ConcurrentHashMap<>();
    private static boolean working = false;

    protected AttackStanceTaskManager() {
        ThreadPool.schedulePriorityTaskAtFixedRate(this, 0, 1000);
    }

    @Override
    public void run() {
        if (working) {
            return;
        }

        working = true;

        if (!CREATURE_ATTACK_STANCES.isEmpty()) {
            try {
                final long currentTime = ClockBridge.gameMillis();
                final Iterator<Entry<Creature, Long>> iterator =
                        CREATURE_ATTACK_STANCES.entrySet().iterator();
                Entry<Creature, Long> entry;
                Creature creature;

                while (iterator.hasNext()) {
                    entry = iterator.next();
                    if ((currentTime - entry.getValue()) > COMBAT_TIME) {
                        creature = entry.getKey();
                        if (creature != null) {
                            creature.broadcastPacket(new AutoAttackStop(creature.getObjectId()));
                            creature.getAI().setAutoAttacking(false);
                            if (creature.isPlayer()) {
                                final Player player = creature.asPlayer();
                                if (player.hasSummon()) {
                                    player.getSummon()
                                            .broadcastPacket(new AutoAttackStop(
                                                    player.getSummon().getObjectId()));
                                }
                            }
                        }

                        iterator.remove();
                    }
                }
            } catch (Exception e) {
                // Unless caught here, players remain in attack positions.
                log.warn("Error in AttackStanceTaskManager: {}", e.getMessage(), e);
            }
        }

        working = false;
    }

    /**
     * Adds the attack stance task.
     * @param creature the actor
     */
    public void addAttackStanceTask(Creature creature) {
        if (creature == null) {
            return;
        }

        if (creature.isPlayable()) {
            for (Cubic cubic : creature.asPlayer().getCubics().values()) {
                if (cubic.getId() != Cubic.LIFE_CUBIC) {
                    cubic.doAction();
                }
            }
        }

        CREATURE_ATTACK_STANCES.put(creature, ClockBridge.gameMillis());
    }

    /**
     * Removes the attack stance task.
     * @param creature the actor
     */
    public void removeAttackStanceTask(Creature creature) {
        Creature actor = creature;
        if (actor != null) {
            if (actor.isSummon()) {
                actor = actor.asPlayer();
            }

            CREATURE_ATTACK_STANCES.remove(actor);
        }
    }

    /**
     * Checks for attack stance task.
     * @param creature the actor
     * @return {@code true} if the character has an attack stance task, {@code false} otherwise
     */
    public boolean hasAttackStanceTask(Creature creature) {
        Creature actor = creature;
        if (actor != null) {
            if (actor.isSummon()) {
                actor = actor.asPlayer();
            }

            return CREATURE_ATTACK_STANCES.containsKey(actor);
        }

        return false;
    }

    /**
     * Gets the single instance of AttackStanceTaskManager.
     * @return single instance of AttackStanceTaskManager
     */
    public static AttackStanceTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AttackStanceTaskManager INSTANCE = new AttackStanceTaskManager();
    }
}
