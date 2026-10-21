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
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes heal cubic actions.<br>
 * Tracks successful heal usage so consumable heal cubics respect template limits.
 * @author UnAfraid, BazookaRpm
 */
@Slf4j
public class CubicHeal implements Runnable {

    private final Cubic cubic;
    private final AtomicInteger currentCount = new AtomicInteger();

    public CubicHeal(Cubic cubic) {
        this.cubic = cubic;
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
            if ((cubic.getCubicMaxCount() > -1) && (currentCount.get() >= cubic.getCubicMaxCount())) {
                cubic.stopAction();
                if ((cubic.getTemplate() != null) && (cubic.getTemplate().getUseUp() > 0)) {
                    cubic.getOwner().removeCubic(cubic.getId());
                    cubic.getOwner().broadcastUserInfo();
                }
                return;
            }

            cubic.cubicTargetForHeal();
            final Creature target = cubic.getTarget();
            if ((target == null) || target.isDead()) {
                return;
            }

            final CubicSkill cubicSkill = getHealSkill(target);
            if (cubicSkill == null) {
                return;
            }

            final Skill skill = cubicSkill.getSkill();
            if ((skill == null) || ((target.getMaxHp() - target.getCurrentHp()) <= skill.getPower())) {
                return;
            }

            skill.activateSkill(cubic, Collections.singletonList(target));
            cubic.getOwner()
                    .broadcastPacket(
                            new MagicSkillUse(cubic.getOwner(), target, skill.getId(), skill.getLevel(), 0, 0));
            currentCount.incrementAndGet();
        } catch (Exception e) {
            log.error(
                    "Cubic heal failed. cubicId: {} ownerId: {}",
                    cubic.getId(),
                    cubic.getOwner().getObjectId(),
                    e);
        }
    }

    private CubicSkill getHealSkill(Creature target) {
        if ((cubic.getTemplate() != null) && !cubic.getTemplate().validateConditions(cubic, cubic.getOwner(), target)) {
            return null;
        }

        for (CubicSkill cubicSkill : cubic.getCubicSkills()) {
            final Skill skill = cubicSkill.getSkill();
            if ((skill != null)
                    && (skill.getId() == Cubic.SKILL_CUBIC_HEAL)
                    && cubicSkill.validateConditions(cubic, cubic.getOwner(), target)) {
                return cubicSkill;
            }
        }

        return null;
    }
}
