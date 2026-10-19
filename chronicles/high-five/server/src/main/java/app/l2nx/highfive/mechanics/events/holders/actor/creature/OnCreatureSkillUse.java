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
package app.l2nx.highfive.mechanics.events.holders.actor.creature;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;
import app.l2nx.highfive.mechanics.skill.Skill;
import java.util.Collection;
import lombok.Getter;

/**
 * An instantly executed event when Creature is attacked by Creature.
 * @author UnAfraid
 */
public class OnCreatureSkillUse implements IBaseEvent {
    @Getter
    private Creature caster;

    @Getter
    private Skill skill;

    @Getter
    private boolean simultaneously;

    @Getter
    private Creature target;

    @Getter
    private Collection<WorldObject> targets;

    public OnCreatureSkillUse() {}

    public synchronized void setCaster(Creature caster) {
        this.caster = caster;
    }

    public synchronized void setSkill(Skill skill) {
        this.skill = skill;
    }

    public synchronized void setSimultaneously(boolean simultaneously) {
        this.simultaneously = simultaneously;
    }

    public synchronized void setTarget(Creature target) {
        this.target = target;
    }

    public synchronized void setTargets(Collection<WorldObject> targets) {
        this.targets = targets;
    }

    @Override
    public EventType getType() {
        return EventType.ON_CREATURE_SKILL_USE;
    }
}
