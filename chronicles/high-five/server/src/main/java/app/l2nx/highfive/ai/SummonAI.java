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
package app.l2nx.highfive.ai;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeoEngineConfig;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.geoengine.pathfinding.PathFinding;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.Future;

public class SummonAI extends PlayableAI implements Runnable {
    private static final int AVOID_RADIUS = 70;

    private volatile boolean thinking; // To prevent recursive thinking.
    private volatile boolean startFollow = actor.asSummon().getFollowStatus();
    private Creature lastAttack = null;

    private volatile boolean startAvoid = false;
    private Future<?> avoidTask = null;

    public SummonAI(Summon creature) {
        super(creature);
    }

    @Override
    public Intention getNextIntention() {
        return lastAttack != null ? Intention.ATTACK : null;
    }

    @Override
    public void setIntentionAttack(WorldObject target) {
        if (target == null) {
            return;
        }

        final Creature creatureTarget = target.asCreature();
        if (creatureTarget == null) {
            return;
        }

        if ((GeoEngineConfig.PATHFINDING > 0)
                && (PathFinding.getInstance()
                                .findPath(
                                        actor.getX(),
                                        actor.getY(),
                                        actor.getZ(),
                                        creatureTarget.getX(),
                                        creatureTarget.getY(),
                                        creatureTarget.getZ(),
                                        actor.getInstanceId(),
                                        false)
                        == null)) {
            return;
        }

        stopAvoidTask();
        super.setIntentionAttack(target);
    }

    @Override
    public void setIntentionIdle() {
        stopFollow();
        startFollow = false;
        stopAvoidTask();
        setIntentionActive();
    }

    @Override
    public void setIntentionActive() {
        if (startFollow) {
            startAvoidTask();
            setIntentionFollow(actor.asSummon().getOwner());
        } else {
            startAvoidTask();
            super.setIntentionActive();
        }
    }

    @Override
    public void setIntentionFollow(WorldObject target) {
        if (target == null) {
            clientActionFailed();
            return;
        }

        final Creature creatureTarget = target.asCreature();
        if (creatureTarget == null) {
            clientActionFailed();
            return;
        }

        if ((GeoEngineConfig.PATHFINDING > 0)
                && (PathFinding.getInstance()
                                .findPath(
                                        actor.getX(),
                                        actor.getY(),
                                        actor.getZ(),
                                        creatureTarget.getX(),
                                        creatureTarget.getY(),
                                        creatureTarget.getZ(),
                                        actor.getInstanceId(),
                                        false)
                        == null)) {
            clientActionFailed();
            return;
        }

        startAvoidTask();
        super.setIntentionFollow(target);
    }

    private void thinkAttack() {
        if (checkTargetLostOrDead(getAttackTarget())) {
            setAttackTarget(null);
            return;
        }

        if (maybeMoveToPawn(getAttackTarget(), actor.getPhysicalAttackRange())) {
            return;
        }

        clientStopMoving(null);
        actor.doAttack(getAttackTarget());
    }

    private void thinkCast() {
        if (checkTargetLost(getCastTarget())) {
            setCastTarget(null);
            return;
        }

        final boolean val = startFollow;
        if (maybeMoveToPawn(getCastTarget(), actor.getMagicalAttackRange(skill))) {
            return;
        }

        clientStopMoving(null);
        final Summon summon = actor.asSummon();
        summon.setFollowStatus(false);
        setIntentionIdle();
        startFollow = val;
        actor.doCast(skill);
    }

    private void thinkPickUp() {
        if (checkTargetLost(getTarget()) || maybeMoveToPawn(getTarget(), 36)) {
            return;
        }

        setIntentionIdle();
        actor.asSummon().doPickupItem(getTarget());
    }

    private void thinkInteract() {
        if (checkTargetLost(getTarget()) || maybeMoveToPawn(getTarget(), 36)) {
            return;
        }

        setIntentionIdle();
    }

    @Override
    public void notifyActionThink() {
        if (thinking || actor.isCastingNow() || actor.isAllSkillsDisabled()) {
            return;
        }

        thinking = true;

        try {
            switch (getIntention()) {
                case ATTACK: {
                    thinkAttack();
                    break;
                }
                case CAST: {
                    thinkCast();
                    break;
                }
                case PICK_UP: {
                    thinkPickUp();
                    break;
                }
                case INTERACT: {
                    thinkInteract();
                    break;
                }
            }
        } finally {
            thinking = false;
        }
    }

    @Override
    public void notifyActionFinishCasting() {
        if (lastAttack == null) {
            actor.asSummon().setFollowStatus(startFollow);
        } else {
            final Creature replayTarget = lastAttack;
            lastAttack = null;
            setIntentionAttack(replayTarget);
        }

        super.notifyActionFinishCasting();
    }

    @Override
    public void notifyActionAttacked(WorldObject attacker) {
        super.notifyActionAttacked(attacker);

        if (attacker != null) {
            final Creature attackerCreature = attacker.asCreature();
            if (attackerCreature != null) {
                avoidAttack(attackerCreature);
            }
        }
    }

    @Override
    public void notifyActionEvaded(WorldObject attacker) {
        super.notifyActionEvaded(attacker);

        if (attacker != null) {
            final Creature attackerCreature = attacker.asCreature();
            if (attackerCreature != null) {
                avoidAttack(attackerCreature);
            }
        }
    }

    private void avoidAttack(Creature attacker) {
        // Trying to avoid if summon near owner.
        if ((actor.asSummon().getOwner() != null)
                && (actor.asSummon().getOwner() != attacker)
                && actor.asSummon().getOwner().isInsideRadius3D(actor, 2 * AVOID_RADIUS)) {
            startAvoid = true;
        }
    }

    @Override
    public void run() {
        if (!startAvoid) {
            return;
        }

        startAvoid = false;

        if (actor.isMoving() || actor.isDead() || actor.isMovementDisabled()) {
            return;
        }

        final int ownerX = actor.asSummon().getOwner().getX();
        final int ownerY = actor.asSummon().getOwner().getY();
        final double angle = Math.toRadians(ClockBridge.random().nextInt(-90, 91))
                + Math.atan2(ownerY - actor.getY(), ownerX - actor.getX());
        final int targetX = ownerX + (int) (AVOID_RADIUS * Math.cos(angle));
        final int targetY = ownerY + (int) (AVOID_RADIUS * Math.sin(angle));
        if (GeoEngine.getInstance()
                .canMoveToTarget(
                        actor.getX(),
                        actor.getY(),
                        actor.getZ(),
                        targetX,
                        targetY,
                        actor.getZ(),
                        actor.getInstanceId())) {
            moveTo(targetX, targetY, actor.getZ());
        }
    }

    public void notifyFollowStatusChange() {
        startFollow = !startFollow;
        switch (getIntention()) {
            case ACTIVE:
            case FOLLOW:
            case IDLE:
            case MOVE_TO:
            case PICK_UP: {
                actor.asSummon().setFollowStatus(startFollow);
            }
        }
    }

    public void setStartFollowController(boolean value) {
        startFollow = value;
    }

    @Override
    public void setIntentionCast(Skill skill, WorldObject target) {
        if (getIntention() == Intention.ATTACK) {
            lastAttack = getAttackTarget();
        } else {
            lastAttack = null;
        }

        stopAvoidTask();
        super.setIntentionCast(skill, target);
    }

    private void startAvoidTask() {
        if (avoidTask == null) {
            avoidTask = ThreadPool.scheduleAtFixedRate(this, 100, 100);
        }
    }

    private void stopAvoidTask() {
        if (avoidTask != null) {
            avoidTask.cancel(false);
            avoidTask = null;
        }
    }

    @Override
    public void stopAITask() {
        stopAvoidTask();
        super.stopAITask();
    }
}
