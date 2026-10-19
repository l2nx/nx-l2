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
package app.l2nx.highfive.entity.actor.templates;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.cubic.CubicSelectionMode;
import app.l2nx.highfive.entity.cubic.CubicSkill;
import app.l2nx.highfive.entity.cubic.CubicTargetType;
import app.l2nx.highfive.entity.cubic.ICubicConditionHolder;
import app.l2nx.highfive.entity.cubic.conditions.ICubicCondition;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Cubic template loaded from datapack XML.<br>
 * Keeps static cubic runtime data and condition lists.
 * <ul>
 * <li>Stores activation timing.</li>
 * <li>Stores cubic skills.</li>
 * <li>Validates template-level conditions.</li>
 * </ul>
 * @author UnAfraid
 */
public class CubicTemplate extends CreatureTemplate implements ICubicConditionHolder {
    @Getter
    private final int id;

    @Getter
    private final int level;

    @Getter
    private final int slot;

    @Getter
    private final int duration;

    @Getter
    private final int delay;

    @Getter
    private final int maxCount;

    @Getter
    private final int useUp;

    private final double power;

    @Getter
    private final CubicTargetType targetType;

    @Getter
    private final CubicSelectionMode selectionMode;

    private final List<ICubicCondition> conditions = new ArrayList<>();
    private final List<CubicSkill> skills = new ArrayList<>();

    public CubicTemplate(StatSet set) {
        super(set);
        id = set.getInt("id");
        level = set.getInt("level");
        slot = set.getInt("slot");
        duration = set.getInt("duration");
        delay = set.getInt("delay");
        maxCount = set.getInt("maxCount");
        useUp = set.getInt("useUp");
        power = set.getDouble("power", 0);
        targetType = set.getEnum("targetType", CubicTargetType.class, CubicTargetType.TARGET);
        selectionMode = set.getEnum("selectionMode", CubicSelectionMode.class, CubicSelectionMode.RANDOM);
    }

    public List<CubicSkill> getCubicSkills() {
        return Collections.unmodifiableList(skills);
    }

    public void addCubicSkill(CubicSkill skill) {
        skills.add(skill);
    }

    @Override
    public int getBasePAtk() {
        return (int) power;
    }

    @Override
    public int getBaseMAtk() {
        return (int) power;
    }

    @Override
    public boolean validateConditions(Cubic cubic, Creature owner, WorldObject target) {
        if (conditions.isEmpty()) {
            return true;
        }

        for (ICubicCondition condition : conditions) {
            if (!condition.test(cubic, owner, target)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void addCondition(ICubicCondition condition) {
        conditions.add(condition);
    }
}
