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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.enums.creature.TrapAction;
import app.l2nx.highfive.entity.actor.instance.Trap;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.trap.OnTrapAction;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.util.StatSet;

/**
 * Trap Remove effect implementation.
 * @author UnAfraid
 */
public class TrapRemove extends AbstractEffect {
    private final int _power;

    public TrapRemove(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        if (params.isEmpty()) {
            throw new IllegalArgumentException(getClass().getSimpleName() + ": effect without power!");
        }

        _power = params.getInt("power");
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (!effected.isTrap()) {
            return;
        }

        if (effected.isAlikeDead()) {
            return;
        }

        final Trap trap = (Trap) effected;
        if (!trap.canBeSeen(effector)) {
            if (effector.isPlayer()) {
                effector.sendPacket(SystemMessageId.INVALID_TARGET);
            }
            return;
        }

        if (trap.getLevel() > _power) {
            return;
        }

        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_TRAP_ACTION, trap)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnTrapAction(trap, effector, TrapAction.TRAP_DISARMED), trap);
        }

        trap.unSummon();
        if (effector.isPlayer()) {
            effector.sendPacket(SystemMessageId.THE_TRAP_DEVICE_HAS_BEEN_STOPPED);
        }
    }
}
