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
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Crystal Grade Modify effect implementation.
 * @author Zoey76
 */
public class CrystalGradeModify extends AbstractEffect {
    private final int _grade;

    public CrystalGradeModify(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        _grade = params.getInt("grade", 0);
    }

    @Override
    public boolean canStart(Creature effector, Creature effected, Skill skill) {
        return effected.isPlayer();
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        final Player player = effected.asPlayer();
        if (player != null) {
            player.setExpertisePenaltyBonus(0);
        }
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        effected.asPlayer().setExpertisePenaltyBonus(_grade);
    }
}
