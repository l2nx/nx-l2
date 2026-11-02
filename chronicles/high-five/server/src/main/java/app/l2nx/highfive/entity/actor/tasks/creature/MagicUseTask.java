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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Collections;
import java.util.List;

/**
 * Task dedicated to magic use of character
 * @author xban1x
 */
@SingleWriter
public class MagicUseTask implements Runnable {
    private final Creature creature;

    private List<WorldObject> targets;

    private final Skill skill;

    private int count;

    private int skillTime;

    private int phase;

    private final boolean simultaneous;

    public MagicUseTask(Creature creature, List<WorldObject> targets, Skill s, int hit, boolean simultaneous) {
        this.creature = creature;
        this.targets = readOnly(targets);
        skill = s;
        count = 0;
        phase = 1;
        skillTime = hit;
        this.simultaneous = simultaneous;
    }

    // A read-only view, not a copy: the cast hands over a target list it built for this task alone.
    private static List<WorldObject> readOnly(List<WorldObject> targets) {
        return targets == null ? null : Collections.unmodifiableList(targets);
    }

    /** Read-only, or {@code null} when the cast has no target list. */
    public List<WorldObject> getTargets() {
        WorldPartition.checkOwner();
        return targets;
    }

    public void setTargets(List<WorldObject> targets) {
        WorldPartition.checkOwner();
        this.targets = readOnly(targets);
    }

    public Skill getSkill() {
        WorldPartition.checkOwner();
        return skill;
    }

    public int getCount() {
        WorldPartition.checkOwner();
        return count;
    }

    public void setCount(int count) {
        WorldPartition.checkOwner();
        this.count = count;
    }

    public int getSkillTime() {
        WorldPartition.checkOwner();
        return skillTime;
    }

    public void setSkillTime(int skillTime) {
        WorldPartition.checkOwner();
        this.skillTime = skillTime;
    }

    public int getPhase() {
        WorldPartition.checkOwner();
        return phase;
    }

    public void setPhase(int phase) {
        WorldPartition.checkOwner();
        this.phase = phase;
    }

    public boolean isSimultaneous() {
        WorldPartition.checkOwner();
        return simultaneous;
    }

    @Override
    public void run() {
        WorldPartition.checkOwner();
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
