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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.config.custom.CancelReturnConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.StatSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dispel By Category effect implementation.
 * @author DS, Adry_85, Naker
 */
public class DispelByCategory extends AbstractEffect {
    private final String slot;
    private final int rate;
    private final int max;

    public DispelByCategory(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        slot = params.getString("slot", null);
        rate = params.getInt("rate", 0);
        max = params.getInt("max", 0);
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.DISPEL;
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if ((effected == null) || effected.isDead() || effected.isRaid()) {
            return;
        }

        if (!CancelReturnConfig.CANCEL_RETURN_ON) {
            normalCancel(effector, effected, skill);
            return;
        }

        if ((effector.isPlayer() && !CancelReturnConfig.CANCEL_RETURN_PLAYER)
                || ((effector.isMonster() || effector.isRaid()) && !CancelReturnConfig.CANCEL_RETURN_MOB)) {
            normalCancel(effector, effected, skill);
            return;
        }

        if (!CancelReturnConfig.CANCEL_RETURN_PLAYER_OLYS
                && effected.isPlayer()
                && effected.asPlayer().isInOlympiadMode()) {
            normalCancel(effector, effected, skill);
            return;
        }

        final List<BuffInfo> canceled = Formulas.calcCancelStealEffects(effector, effected, skill, slot, rate, max);
        if (canceled.isEmpty()) {
            return;
        }

        for (BuffInfo info : canceled) {
            effected.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, info.getSkill());
        }

        WorldPartition.loop()
                .schedule(
                        () -> {
                            if (effected.isMonster()
                                    || effected.isDead()
                                    || !effected.asPlayer().isOnline()) {
                                return;
                            }

                            for (BuffInfo info : canceled) {
                                final Skill sk = info.getSkill();
                                final int timeLeft = info.getTime();

                                if ((sk == null) || (timeLeft <= 0)) {
                                    continue;
                                }

                                if (effected.getEffectList().getBuffInfoBySkillId(sk.getId()) != null) {
                                    continue;
                                }

                                sk.applyEffects(effected, effected);

                                final BuffInfo newInfo =
                                        effected.getEffectList().getBuffInfoBySkillId(sk.getId());
                                if (newInfo != null) {
                                    newInfo.setAbnormalTime(timeLeft);
                                }
                            }
                        },
                        CancelReturnConfig.TIME_TO_RETURN,
                        TimeUnit.MILLISECONDS);
    }

    private void normalCancel(Creature effector, Creature effected, Skill skill) {
        final List<BuffInfo> canceled = Formulas.calcCancelStealEffects(effector, effected, skill, slot, rate, max);
        for (BuffInfo info : canceled) {
            effected.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, info.getSkill());
        }
    }
}
