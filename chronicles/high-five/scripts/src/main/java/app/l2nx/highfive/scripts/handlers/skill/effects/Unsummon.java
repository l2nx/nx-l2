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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.util.StatSet;

/**
 * Unsummon effect implementation.
 * @author Adry_85
 */
public class Unsummon extends AbstractEffect {
    private final int chance;

    public Unsummon(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        chance = params.getInt("chance", 100);
    }

    @Override
    public boolean calcSuccess(Creature effector, Creature effected, Skill skill) {
        final int magicLevel = skill.getMagicLevel();
        if ((magicLevel <= 0) || ((effected.getLevel() - 9) <= magicLevel)) {
            final double chance = this.chance
                    * Formulas.calcAttributeBonus(effector, effected, skill)
                    * Formulas.calcGeneralTraitBonus(effector, effected, skill.getTraitType(), false);
            if (chance > (ClockBridge.random().nextDouble() * 100)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        final Summon summon = effected.asSummon();
        if (summon != null) {
            summon.abortAttack();
            summon.abortCast();
            summon.stopAllEffects();

            final Player player = summon.getOwner();
            summon.unSummon(player);
            player.sendPacket(SystemMessageId.YOUR_SERVITOR_HAS_VANISHED_YOU_LL_NEED_TO_SUMMON_A_NEW_ONE);
        }
    }
}
