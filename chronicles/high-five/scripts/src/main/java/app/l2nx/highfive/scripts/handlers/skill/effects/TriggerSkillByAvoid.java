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

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.handler.TargetHandler;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureAttackAvoid;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.util.StatSet;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger Skill By Avoid effect implementation.
 * @author Zealar
 */
@Slf4j
public class TriggerSkillByAvoid extends AbstractEffect {
    private final int chance;
    private final SkillHolder skill;
    private final TargetType targetType;

    /**
     * @param attachCond
     * @param applyCond
     * @param set
     * @param params
     */
    public TriggerSkillByAvoid(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        chance = params.getInt("chance", 100);
        skill = new SkillHolder(params.getInt("skillId", 0), params.getInt("skillLevel", 0));
        targetType = params.getEnum("targetType", TargetType.class, TargetType.ONE);
    }

    public void onAvoidEvent(OnCreatureAttackAvoid event) {
        if (event.damageOverTime() || (chance == 0) || ((skill.getSkillId() == 0) || (skill.getSkillLevel() == 0))) {
            return;
        }

        final ITargetTypeHandler targetHandler = TargetHandler.getInstance().getHandler(targetType);
        if (targetHandler == null) {
            log.warn("Handler for target type: {} does not exist.", targetType);
            return;
        }

        if (ThreadLocalRandom.current().nextInt(100) > chance) {
            return;
        }

        final Skill triggerSkill = skill.getSkill();
        for (WorldObject triggerTarget :
                targetHandler.getTargetList(triggerSkill, event.target(), false, event.attacker())) {
            if ((triggerTarget == null) || !triggerTarget.isCreature()) {
                continue;
            }

            final Creature targetChar = triggerTarget.asCreature();
            if (!targetChar.isInvul()) {
                event.target().makeTriggerCast(triggerSkill, targetChar);
            }
        }
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        effected.removeListenerIf(EventType.ON_CREATURE_ATTACK_AVOID, listener -> listener.getOwner() == this);
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        effected.addListener(new ConsumerEventListener(
                effected,
                EventType.ON_CREATURE_ATTACK_AVOID,
                (OnCreatureAttackAvoid event) -> onAvoidEvent(event),
                this));
    }
}
