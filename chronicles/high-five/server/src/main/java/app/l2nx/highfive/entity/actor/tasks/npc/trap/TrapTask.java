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
package app.l2nx.highfive.entity.actor.tasks.npc.trap;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Trap;
import app.l2nx.highfive.network.serverpackets.SocialAction;
import lombok.extern.slf4j.Slf4j;

/**
 * Trap task.
 * @author Zoey76
 */
@Slf4j
public class TrapTask implements Runnable {
    private static final int TICK = 1000; // 1s
    private final Trap trap;

    public TrapTask(Trap trap) {
        this.trap = trap;
    }

    @Override
    public void run() {
        try {
            if (!trap.isTriggered()) {
                if (trap.hasLifeTime()) {
                    trap.setRemainingTime(trap.getRemainingTime() - TICK);
                    if (trap.getRemainingTime() < (trap.getLifeTime() - 15000)) {
                        trap.broadcastPacket(new SocialAction(trap.getObjectId(), 2));
                    }

                    if (trap.getRemainingTime() <= 0) {
                        switch (trap.getSkill().getTargetType()) {
                            case AURA:
                            case FRONT_AURA:
                            case BEHIND_AURA: {
                                trap.triggerTrap(trap);
                                break;
                            }
                            default: {
                                trap.unSummon();
                            }
                        }
                        return;
                    }
                }

                World.forFirstVisibleObject(trap, Creature.class, target -> trap.checkTarget(target), target -> {
                    trap.triggerTrap(target);
                });
            }
        } catch (Exception e) {
            log.error("{}", e.getMessage());
            trap.unSummon();
        }
    }
}
