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
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Resist Skill effect implementation.
 * @author UnAfraid
 */
public class ResistSkill extends AbstractEffect {
    private final List<SkillHolder> skills = new ArrayList<>();

    public ResistSkill(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        for (int i = 1; ; i++) {
            final int skillId = params.getInt("skillId" + i, 0);
            if (skillId == 0) {
                break;
            }

            final int skillLevel = params.getInt("skillLevel" + i, 0);
            skills.add(new SkillHolder(skillId, skillLevel));
        }

        if (skills.isEmpty()) {
            throw new IllegalArgumentException(getClass().getSimpleName() + ": Without parameters!");
        }
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        for (SkillHolder holder : skills) {
            effected.addInvulAgainst(holder);
        }
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        for (SkillHolder holder : skills) {
            effected.removeInvulAgainst(holder);
        }
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.BUFF;
    }
}
