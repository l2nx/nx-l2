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

import app.l2nx.highfive.ai.AttackableAI;
import app.l2nx.highfive.ai.DistrustAI;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Chest;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.util.StatSet;

/**
 * @author Naker
 */
public class Distrust extends AbstractEffect {
    public Distrust(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (effected.isDead()
                || !effected.isMonster()
                || (effected instanceof Chest)
                || effected.isRaid()
                || effected.isRaidMinion()) {
            effector.sendPacket(SystemMessageId.INVALID_TARGET);
            return;
        }

        final Monster targetMonster = effected.asMonster();
        final Monster newTarget = World.getRandomVisibleObjectInRange(
                targetMonster,
                Monster.class,
                1100,
                m -> (m != targetMonster)
                        && !m.isDead()
                        && m.isAttackable()
                        && !(m instanceof Chest)
                        && !m.isRaid()
                        && !m.isRaidMinion());
        if (newTarget == null) {
            return;
        }

        targetMonster.setAI(new DistrustAI(effected.asAttackable(), newTarget));
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        final Attackable monster = effected.asAttackable();
        if ((monster == null) || monster.isDead()) {
            return;
        }

        monster.setAI(new AttackableAI(monster));
        monster.setTarget(null);
        monster.setWalking();
        monster.getAI().setIntentionActive();
    }
}
