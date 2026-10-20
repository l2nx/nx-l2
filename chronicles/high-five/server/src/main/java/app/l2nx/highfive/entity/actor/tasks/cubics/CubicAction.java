/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.actor.tasks.cubics;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.cubic.CubicSkill;
import app.l2nx.highfive.entity.cubic.CubicTargetType;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.serverpackets.MagicSkillUse;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes offensive and mixed cubic actions.<br>
 * Supports template-level and skill-level target routing.
 * @author UnAfraid, Mobius
 */
@Slf4j
public class CubicAction implements Runnable {

    private final Cubic cubic;
    private final AtomicInteger currentCount = new AtomicInteger();
    private final int chance;

    public CubicAction(Cubic cubic, int chance) {
        this.cubic = cubic;
        this.chance = chance;
    }

    @Override
    public void run() {
        if (cubic == null) {
            return;
        }

        if (cubic.getOwner().isDead() || !cubic.getOwner().isOnline()) {
            cubic.getOwner().removeCubic(cubic.getId());
            cubic.getOwner().broadcastUserInfo();
            return;
        }

        try {
            if (!AttackStanceTaskManager.getInstance().hasAttackStanceTask(cubic.getOwner())) {
                if (cubic.getOwner().hasSummon()) {
                    if (!AttackStanceTaskManager.getInstance()
                            .hasAttackStanceTask(cubic.getOwner().getSummon())) {
                        cubic.stopAction();
                        return;
                    }
                } else {
                    cubic.stopAction();
                    return;
                }
            }

            if ((cubic.getCubicMaxCount() > -1) && (currentCount.get() >= cubic.getCubicMaxCount())) {
                cubic.stopAction();
                if ((cubic.getTemplate() != null) && (cubic.getTemplate().getUseUp() > 0)) {
                    cubic.getOwner().removeCubic(cubic.getId());
                    cubic.getOwner().broadcastUserInfo();
                }
                return;
            }

            if (tryUsePrioritySkill()) {
                currentCount.incrementAndGet();
                return;
            }

            if (ClockBridge.random().nextInt(100) >= chance) {
                return;
            }

            if (tryUseSkill(cubic.getCubicSkill())) {
                currentCount.incrementAndGet();
            }
        } catch (Exception e) {
            log.error(
                    "Cubic action failed. cubicId: {} ownerId: {}",
                    cubic.getId(),
                    cubic.getOwner().getObjectId(),
                    e);
        }
    }

    private boolean tryUsePrioritySkill() {
        for (CubicSkill cubicSkill : cubic.getCubicSkills()) {
            if (cubicSkill.isPriority() && tryUseSkill(cubicSkill)) {
                return true;
            }
        }

        return false;
    }

    private boolean tryUseSkill(CubicSkill cubicSkill) {
        if (cubicSkill == null) {
            return false;
        }

        final Skill skill = cubicSkill.getSkill();
        if (skill == null) {
            return false;
        }

        final Creature target = resolveTarget(cubicSkill);
        if ((target == null) || target.isDead()) {
            return false;
        }

        if ((cubic.getTemplate() != null) && !cubic.getTemplate().validateConditions(cubic, cubic.getOwner(), target)) {
            return false;
        }

        if (!cubicSkill.validateConditions(cubic, cubic.getOwner(), target)) {
            return false;
        }

        activateSkill(skill, target);
        return true;
    }

    private Creature resolveTarget(CubicSkill cubicSkill) {
        CubicTargetType targetType =
                cubic.getTemplate() != null ? cubic.getTemplate().getTargetType() : CubicTargetType.TARGET;
        if (targetType == CubicTargetType.BY_SKILL) {
            targetType = cubicSkill.getTargetType();
        }

        switch (targetType) {
            case HEAL: {
                cubic.cubicTargetForHeal();
                return cubic.getTarget();
            }
            case MASTER: {
                cubic.setTarget(cubic.getOwner());
                return cubic.getOwner();
            }
            case TARGET:
            default: {
                cubic.getCubicTarget();
                if (!Cubic.isInCubicRange(cubic.getOwner(), cubic.getTarget())) {
                    cubic.setTarget(null);
                }
                return cubic.getTarget();
            }
        }
    }

    private void activateSkill(Skill skill, Creature target) {
        cubic.getOwner()
                .broadcastSkillPacket(
                        new MagicSkillUse(cubic.getOwner(), target, skill.getId(), skill.getLevel(), 0, 0), target);
        if (skill.isContinuous()) {
            cubic.useCubicContinuous(skill, Collections.singletonList(target));
        } else {
            if (skill.hasEffectType(EffectType.MAGICAL_ATTACK)) {
                cubic.useCubicMdam(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.HP_DRAIN)) {
                cubic.useCubicDrain(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.STUN, EffectType.ROOT, EffectType.PARALYZE)) {
                cubic.useCubicDisabler(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.DMG_OVER_TIME, EffectType.DMG_OVER_TIME_PERCENT)) {
                cubic.useCubicContinuous(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.AGGRESSION)) {
                cubic.useCubicDisabler(skill, Collections.singletonList(target));
            } else {
                skill.activateSkill(cubic, Collections.singletonList(target));
            }
        }
    }
}
