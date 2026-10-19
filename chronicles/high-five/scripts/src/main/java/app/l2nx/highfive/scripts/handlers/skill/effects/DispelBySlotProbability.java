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
import app.l2nx.highfive.entity.actor.holders.creature.EffectList;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dispel By Slot Probability effect implementation.
 * @author Adry_85, Zoey76, Naker
 */
public class DispelBySlotProbability extends AbstractEffect {
    private final String dispel;
    private final Map<AbnormalType, Short> dispelAbnormals;
    private final int rate;

    public DispelBySlotProbability(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        dispel = params.getString("dispel", null);
        rate = params.getInt("rate", 0);

        if ((dispel != null) && !dispel.isEmpty()) {
            dispelAbnormals = new EnumMap<>(AbnormalType.class);

            for (String ngtStack : dispel.split(";")) {
                final String[] ngt = ngtStack.split(",");
                dispelAbnormals.put(AbnormalType.getAbnormalType(ngt[0]), Short.MAX_VALUE);
            }
        } else {
            dispelAbnormals = Collections.<AbnormalType, Short>emptyMap();
        }
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
        if (dispelAbnormals.isEmpty() || (effected == null) || effected.isRaid()) {
            return;
        }

        if (!CancelReturnConfig.CANCEL_RETURN_ON) {
            normalCancel(effected);
            return;
        }

        if ((effector.isPlayer() && !CancelReturnConfig.CANCEL_RETURN_PLAYER)
                || ((effector.isMonster() || effector.isRaid()) && !CancelReturnConfig.CANCEL_RETURN_MOB)) {
            normalCancel(effected);
            return;
        }

        if (!CancelReturnConfig.CANCEL_RETURN_PLAYER_OLYS
                && effected.isPlayer()
                && effected.asPlayer().isInOlympiadMode()) {
            normalCancel(effected);
            return;
        }

        final EffectList effectList = effected.getEffectList();
        final List<BuffInfo> canceled = new ArrayList<>();
        for (Entry<AbnormalType, Short> entry : dispelAbnormals.entrySet()) {
            if (ThreadLocalRandom.current().nextInt(100) < rate) {
                // Dispel transformations (buff and by GM).
                if ((entry.getKey() == AbnormalType.TRANSFORM)
                        && (effected.isTransformed()
                                || (effected.isPlayer()
                                        || (entry.getValue()
                                                == effected.asPlayer().getTransformationId())
                                        || (entry.getValue() < 0)))) {
                    effected.stopTransformation(true);
                }

                final BuffInfo toDispel = effectList.getBuffInfoByAbnormalType(entry.getKey());
                if ((toDispel != null)
                        && ((entry.getValue() < 0)
                                || (entry.getValue() >= toDispel.getSkill().getAbnormalLevel()))) {
                    canceled.add(toDispel);
                    effectList.stopSkillEffects(SkillFinishType.REMOVED, entry.getKey());
                }
            }
        }

        if (canceled.isEmpty()) {
            return;
        }

        ThreadPool.schedule(
                () -> {
                    if (!effected.isPlayer()
                            || effected.isDead()
                            || !effected.asPlayer().isOnline()) {
                        return;
                    }

                    for (BuffInfo oldInfo : canceled) {
                        final Skill sk = oldInfo.getSkill();
                        final int timeLeft = oldInfo.getTime();
                        if ((sk == null) || (timeLeft <= 0)) {
                            continue;
                        }

                        if (effected.getEffectList().getBuffInfoBySkillId(sk.getId()) != null) {
                            continue;
                        }

                        sk.applyEffects(effected, effected);

                        final BuffInfo newInfo = effected.getEffectList().getBuffInfoBySkillId(sk.getId());
                        if (newInfo != null) {
                            newInfo.setAbnormalTime(timeLeft);
                        }
                    }
                },
                CancelReturnConfig.TIME_TO_RETURN);
    }

    private void normalCancel(Creature effected) {
        final EffectList effectList = effected.getEffectList();
        for (Entry<AbnormalType, Short> entry : dispelAbnormals.entrySet()) {
            if (ThreadLocalRandom.current().nextInt(100) < rate) {
                // Dispel transformations (buff and by GM).
                if ((entry.getKey() == AbnormalType.TRANSFORM)
                        && (effected.isTransformed()
                                || (effected.isPlayer()
                                        || (entry.getValue()
                                                == effected.asPlayer().getTransformationId())
                                        || (entry.getValue() < 0)))) {
                    effected.stopTransformation(true);
                }

                final BuffInfo toDispel = effectList.getBuffInfoByAbnormalType(entry.getKey());
                if (toDispel != null) {
                    effectList.stopSkillEffects(SkillFinishType.REMOVED, entry.getKey());
                }
            }
        }
    }
}
