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
package app.l2nx.highfive.mechanics.events.holders.actor.npc;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;
import app.l2nx.highfive.mechanics.skill.Skill;
import java.util.List;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class OnNpcSkillSee implements IBaseEvent {
    @Getter
    private final Npc target;

    @Getter
    private final Player caster;

    @Getter
    private final Skill skill;

    @Getter
    private final List<WorldObject> targets;

    @Getter
    private final boolean summon;

    public OnNpcSkillSee(Npc npc, Player caster, Skill skill, List<WorldObject> targets, boolean isSummon) {
        target = npc;
        this.caster = caster;
        this.skill = skill;
        this.targets = targets;
        summon = isSummon;
    }

    @Override
    public EventType getType() {
        return EventType.ON_NPC_SKILL_SEE;
    }
}
