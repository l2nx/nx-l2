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
import app.l2nx.highfive.entity.actor.stat.CreatureStat;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.TraitType;
import app.l2nx.highfive.util.StatSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

/**
 * Attack Trait effect implementation.
 * @author NosBit
 */
@Slf4j
public class AttackTrait extends AbstractEffect {
    private final Map<TraitType, Float> attackTraits = new EnumMap<>(TraitType.class);

    public AttackTrait(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        if (params.isEmpty()) {
            log.warn("This effect must have parameters!");
            return;
        }

        for (Entry<String, Object> param : params.getSet().entrySet()) {
            attackTraits.put(
                    TraitType.valueOf(param.getKey()), (Float.parseFloat((String) param.getValue()) + 100) / 100);
        }
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        final CreatureStat charStat = effected.getStat();
        for (Entry<TraitType, Float> trait : attackTraits.entrySet()) {
            charStat.removeAttackTrait(trait.getKey(), trait.getValue());
        }
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        final CreatureStat charStat = effected.getStat();
        for (Entry<TraitType, Float> trait : attackTraits.entrySet()) {
            charStat.addAttackTrait(trait.getKey(), trait.getValue());
        }
    }
}
