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
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;

/**
 * Physical Attack HP Link effect implementation.
 * @author Adry_85
 */
public class PhysicalDamageHpLink extends AbstractEffect {
    public PhysicalDamageHpLink(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);
    }

    @Override
    public boolean calcSuccess(Creature effector, Creature effected, Skill skill) {
        return !Formulas.calcPhysicalSkillEvasion(effector, effected, skill);
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.PHYSICAL_ATTACK_HP_LINK;
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (effector.isAlikeDead()) {
            return;
        }

        if (effector.isMovementDisabled()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
            sm.addSkillName(skill);
            effector.sendPacket(sm);
            return;
        }

        final byte shld = Formulas.calcShldUse(effector, effected, skill);

        // Physical damage critical rate is only affected by STR.
        boolean crit = false;
        if (skill.getBaseCritRate() > 0) {
            crit = Formulas.calcCrit(effector, effected, skill);
        }

        int damage = 0;
        final boolean ss = skill.isPhysical() && effector.isChargedShot(ShotType.SOULSHOTS);
        damage = (int) Formulas.calcPhysDam(effector, effected, skill, shld, crit, ss);
        if (damage > 0) {
            effector.sendDamageMessage(effected, damage, false, crit, false);
            effected.reduceCurrentHp(damage, effector, skill);
            effected.notifyDamageReceived(damage, effector, skill, crit, false);

            // Check if damage should be reflected.
            Formulas.calcDamageReflected(effector, effected, skill, crit);
        } else {
            effector.sendPacket(SystemMessageId.YOUR_ATTACK_HAS_FAILED);
        }
    }
}
