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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ChangeWaitType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.Revive;
import app.l2nx.highfive.util.StatSet;

/**
 * Fake Death effect implementation.
 * @author mkizub
 */
public class FakeDeath extends AbstractEffect {
    private final double power;

    public FakeDeath(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        power = params.getDouble("power", 0);
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.FAKE_DEATH;
    }

    @Override
    public boolean onActionTime(Creature effector, Creature effected, Skill skill) {
        if (effected.isDead()) {
            return false;
        }

        final double manaDam = power * getTicksMultiplier();
        if ((manaDam > effected.getCurrentMp()) && skill.isToggle()) {
            effected.sendPacket(SystemMessageId.YOUR_SKILL_WAS_DEACTIVATED_DUE_TO_LACK_OF_MP);
            return false;
        }

        effected.reduceCurrentMp(manaDam);

        return skill.isToggle();
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        if (effected.isPlayer()) {
            final Player player = effected.asPlayer();
            player.setFakeDeath(false);
            player.setRecentFakeDeath(true);
        }

        effected.broadcastPacket(new ChangeWaitType(effected, ChangeWaitType.WT_STOP_FAKEDEATH));
        effected.broadcastPacket(new Revive(effected));
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        effected.startFakeDeath();
    }
}
