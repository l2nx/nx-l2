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
package app.l2nx.highfive.ai;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Defender;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * @author BiggBoss
 */
public class SpecialSiegeGuardAI extends SiegeGuardAI {
    @Getter
    private final List<Integer> ally = new ArrayList<>();

    /**
     * @param creature
     */
    public SpecialSiegeGuardAI(Defender creature) {
        super(creature);
    }

    @Override
    protected boolean autoAttackCondition(Creature target) {
        if (ally.contains(target.getObjectId())) {
            return false;
        }

        return super.autoAttackCondition(target);
    }
}
