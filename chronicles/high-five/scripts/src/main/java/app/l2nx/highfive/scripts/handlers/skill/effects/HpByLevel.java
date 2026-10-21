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
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;

/**
 * Hp By Level effect implementation.
 * @author Zoey76
 */
public class HpByLevel extends AbstractEffect {
    private final double power;

    public HpByLevel(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        power = params.getDouble("power", 0);
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.BUFF;
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (effector == null) {
            return;
        }

        // Calculation
        final double abs = power;
        final double absorb = ((effector.getCurrentHp() + abs) > effector.getMaxHp()
                ? effector.getMaxHp()
                : (effector.getCurrentHp() + abs));
        final int restored = (int) (absorb - effector.getCurrentHp());
        effector.setCurrentHp(absorb);

        // System message
        final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HP_HAS_BEEN_RESTORED);
        sm.addInt(restored);
        effector.sendPacket(sm);
    }
}
