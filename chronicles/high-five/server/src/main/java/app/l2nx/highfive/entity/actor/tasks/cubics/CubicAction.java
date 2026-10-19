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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.cubic.CubicSkill;
import app.l2nx.highfive.entity.cubic.CubicTargetType;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.serverpackets.MagicSkillUse;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes offensive and mixed cubic actions.<br>
 * Supports template-level and skill-level target routing.
 * @author UnAfraid, Mobius
 */
public class CubicAction implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(CubicAction.class.getName());

    private final Cubic _cubic;
    private final AtomicInteger _currentCount = new AtomicInteger();
    private final int _chance;

    public CubicAction(Cubic cubic, int chance) {
        _cubic = cubic;
        _chance = chance;
    }

    @Override
    public void run() {
        if (_cubic == null) {
            return;
        }

        if (_cubic.getOwner().isDead() || !_cubic.getOwner().isOnline()) {
            _cubic.getOwner().removeCubic(_cubic.getId());
            _cubic.getOwner().broadcastUserInfo();
            return;
        }

        try {
            if (!AttackStanceTaskManager.getInstance().hasAttackStanceTask(_cubic.getOwner())) {
                if (_cubic.getOwner().hasSummon()) {
                    if (!AttackStanceTaskManager.getInstance()
                            .hasAttackStanceTask(_cubic.getOwner().getSummon())) {
                        _cubic.stopAction();
                        return;
                    }
                } else {
                    _cubic.stopAction();
                    return;
                }
            }

            if ((_cubic.getCubicMaxCount() > -1) && (_currentCount.get() >= _cubic.getCubicMaxCount())) {
                _cubic.stopAction();
                if ((_cubic.getTemplate() != null) && (_cubic.getTemplate().getUseUp() > 0)) {
                    _cubic.getOwner().removeCubic(_cubic.getId());
                    _cubic.getOwner().broadcastUserInfo();
                }
                return;
            }

            if (tryUsePrioritySkill()) {
                _currentCount.incrementAndGet();
                return;
            }

            if (ThreadLocalRandom.current().nextInt(100) >= _chance) {
                return;
            }

            if (tryUseSkill(_cubic.getCubicSkill())) {
                _currentCount.incrementAndGet();
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Cubic action failed. cubicId: " + _cubic.getId() + " ownerId: "
                            + _cubic.getOwner().getObjectId(),
                    e);
        }
    }

    private boolean tryUsePrioritySkill() {
        for (CubicSkill cubicSkill : _cubic.getCubicSkills()) {
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

        if ((_cubic.getTemplate() != null)
                && !_cubic.getTemplate().validateConditions(_cubic, _cubic.getOwner(), target)) {
            return false;
        }

        if (!cubicSkill.validateConditions(_cubic, _cubic.getOwner(), target)) {
            return false;
        }

        activateSkill(skill, target);
        return true;
    }

    private Creature resolveTarget(CubicSkill cubicSkill) {
        CubicTargetType targetType =
                _cubic.getTemplate() != null ? _cubic.getTemplate().getTargetType() : CubicTargetType.TARGET;
        if (targetType == CubicTargetType.BY_SKILL) {
            targetType = cubicSkill.getTargetType();
        }

        switch (targetType) {
            case HEAL: {
                _cubic.cubicTargetForHeal();
                return _cubic.getTarget();
            }
            case MASTER: {
                _cubic.setTarget(_cubic.getOwner());
                return _cubic.getOwner();
            }
            case TARGET:
            default: {
                _cubic.getCubicTarget();
                if (!Cubic.isInCubicRange(_cubic.getOwner(), _cubic.getTarget())) {
                    _cubic.setTarget(null);
                }
                return _cubic.getTarget();
            }
        }
    }

    private void activateSkill(Skill skill, Creature target) {
        _cubic.getOwner()
                .broadcastSkillPacket(
                        new MagicSkillUse(_cubic.getOwner(), target, skill.getId(), skill.getLevel(), 0, 0), target);
        if (skill.isContinuous()) {
            _cubic.useCubicContinuous(skill, Collections.singletonList(target));
        } else {
            if (skill.hasEffectType(EffectType.MAGICAL_ATTACK)) {
                _cubic.useCubicMdam(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.HP_DRAIN)) {
                _cubic.useCubicDrain(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.STUN, EffectType.ROOT, EffectType.PARALYZE)) {
                _cubic.useCubicDisabler(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.DMG_OVER_TIME, EffectType.DMG_OVER_TIME_PERCENT)) {
                _cubic.useCubicContinuous(skill, Collections.singletonList(target));
            } else if (skill.hasEffectType(EffectType.AGGRESSION)) {
                _cubic.useCubicDisabler(skill, Collections.singletonList(target));
            } else {
                skill.activateSkill(_cubic, Collections.singletonList(target));
            }
        }
    }
}
