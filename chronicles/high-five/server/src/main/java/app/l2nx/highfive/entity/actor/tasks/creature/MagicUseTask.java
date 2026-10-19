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
package app.l2nx.highfive.entity.actor.tasks.creature;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.skill.Skill;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Task dedicated to magic use of character
 * @author xban1x
 */
public class MagicUseTask implements Runnable {
    private final Creature creature;

    @Getter
    @Setter
    private List<WorldObject> targets;

    @Getter
    private final Skill skill;

    @Getter
    @Setter
    private int count;

    @Getter
    @Setter
    private int skillTime;

    @Getter
    @Setter
    private int phase;

    @Getter
    private final boolean simultaneous;

    public MagicUseTask(Creature creature, List<WorldObject> targets, Skill s, int hit, boolean simultaneous) {
        this.creature = creature;
        this.targets = targets;
        skill = s;
        count = 0;
        phase = 1;
        skillTime = hit;
        this.simultaneous = simultaneous;
    }

    @Override
    public void run() {
        if (creature == null) {
            return;
        }

        switch (phase) {
            case 1: {
                creature.onMagicLaunchedTimer(this);
                break;
            }
            case 2: {
                creature.onMagicHitTimer(this);
                break;
            }
            case 3: {
                creature.onMagicFinalizer(this);
                break;
            }
        }
    }
}
