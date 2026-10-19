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
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.handler.TargetHandler;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureDamageDealt;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.util.StatSet;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger skill by damage dealt effect implementation.
 * @author Zealar
 */
@Slf4j
public class TriggerSkillByDamageDealt extends AbstractEffect {
    private final int minAttackerLevel;
    private final int maxAttackerLevel;
    private final int minDamage;
    private final int chance;
    private final SkillHolder skill;
    private final TargetType targetType;
    private final InstanceType attackerType;
    private int allowWeapons;
    private final boolean critical;
    private final boolean allowNormalAttack;
    private final boolean allowSkillAttack;

    public TriggerSkillByDamageDealt(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        minAttackerLevel = params.getInt("minAttackerLevel", 1);
        maxAttackerLevel = params.getInt("maxAttackerLevel", 127);
        minDamage = params.getInt("minDamage", 1);
        chance = params.getInt("chance", 100);
        skill = new SkillHolder(params.getInt("skillId"), params.getInt("skillLevel", 1));
        targetType = params.getEnum("targetType", TargetType.class, TargetType.SELF);
        attackerType = params.getEnum("attackerType", InstanceType.class, InstanceType.Creature);
        critical = params.getBoolean("isCritical", false);
        allowNormalAttack = params.getBoolean("allowNormalAttack", true);
        allowSkillAttack = params.getBoolean("allowSkillAttack", false);

        if (params.getString("allowWeapons", "ALL").equalsIgnoreCase("ALL")) {
            allowWeapons = 0;
        } else {
            for (String s : params.getString("allowWeapons").split(",")) {
                allowWeapons |= WeaponType.valueOf(s).mask();
            }
        }
    }

    private void onAttackEvent(OnCreatureDamageDealt event) {
        if (event.damageOverTime()
                || (chance == 0)
                || ((skill.getSkillId() == 0) || (skill.getSkillLevel() == 0))
                || (!allowNormalAttack && !allowSkillAttack)) {
            return;
        }

        // Check if there is dependancy on critical.
        if (critical != event.critical()) {
            return;
        }

        final ITargetTypeHandler targetHandler = TargetHandler.getInstance().getHandler(targetType);
        if (targetHandler == null) {
            log.warn("Handler for target type: {} does not exist.", targetType);
            return;
        }

        // When no skill attacks are allowed.
        if (!allowSkillAttack && (event.skill() != null)) {
            return;
        }

        // When no normal attacks are allowed.
        if (!allowNormalAttack && (event.skill() == null)) {
            return;
        }

        if (event.attacker() == event.target()) {
            return;
        }

        if ((event.attacker().getLevel() < minAttackerLevel)
                || (event.attacker().getLevel() > maxAttackerLevel)) {
            return;
        }

        if ((event.damage() < minDamage)
                || (ThreadLocalRandom.current().nextInt(100) > chance)
                || !event.attacker().getInstanceType().isType(attackerType)) {
            return;
        }

        if (allowWeapons > 0) {
            final Weapon weapon = event.attacker().getActiveWeaponItem();
            if ((weapon == null) || ((weapon.getItemType().mask() & allowWeapons) == 0)) {
                return;
            }
        }

        final Skill triggerSkill = skill.getSkill();
        for (WorldObject triggerTarget :
                targetHandler.getTargetList(triggerSkill, event.attacker(), false, event.target())) {
            if ((triggerTarget == null) || !triggerTarget.isCreature()) {
                continue;
            }

            final Creature targetChar = triggerTarget.asCreature();
            if (!targetChar.isInvul()) {
                event.attacker().makeTriggerCast(triggerSkill, targetChar);
            }
        }
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        effected.removeListenerIf(EventType.ON_CREATURE_DAMAGE_DEALT, listener -> listener.getOwner() == this);
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        effected.addListener(new ConsumerEventListener(
                effected,
                EventType.ON_CREATURE_DAMAGE_DEALT,
                (OnCreatureDamageDealt event) -> onAttackEvent(event),
                this));
    }
}
