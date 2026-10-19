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

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.WorldRegion;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.AutoAttackStart;
import app.l2nx.highfive.network.serverpackets.AutoAttackStop;
import app.l2nx.highfive.network.serverpackets.Die;
import app.l2nx.highfive.network.serverpackets.MoveToLocation;
import app.l2nx.highfive.network.serverpackets.MoveToPawn;
import app.l2nx.highfive.network.serverpackets.StopMove;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import app.l2nx.highfive.taskmanagers.CreatureFollowTaskManager;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Mother class of all objects AI in the world.<br>
 * AbastractAI:<br>
 * <li>CreatureAI</li>
 */
public abstract class AbstractAI {
    /** The creature that this AI manages. */
    @Getter
    protected final Creature actor;

    /** Current long-term intention. */
    @Getter
    protected Intention intention = Intention.IDLE;

    /** Flags about client's state, in order to know which messages to send. */
    @Getter
    protected volatile boolean autoAttacking;
    /** Flags about client's state, in order to know which messages to send. */
    @Getter
    protected int clientMovingToPawnOffset;

    /** Different targets this AI maintains. */
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private WorldObject target;

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private Creature castTarget;

    @Getter
    @Setter(AccessLevel.PROTECTED)
    protected Creature attackTarget;

    @Getter
    protected Creature followTarget;

    /** The skill we are currently casting by INTENTION_CAST. */
    protected Skill skill;

    /** Different internal state flags. */
    private int moveToPawnTimeout;

    @Getter
    @Setter
    private NextAction nextAction;

    protected AbstractAI(Creature creature) {
        actor = creature;
    }

    /**
     * @return the saved Intention pending replay (e.g. after a CAST finishes), or {@code null} if none.<br>
     *         <b><u>Overridden in</u>:</b>
     *         <ul>
     *         <li><b>PlayerAI</b> : returns the intention that was interrupted by the current CAST</li>
     *         <li><b>SummonAI</b> : returns {@link Intention#ATTACK} if a pending attack is saved</li>
     *         </ul>
     */
    public Intention getNextIntention() {
        return null;
    }

    public abstract void setIntentionIdle();

    public abstract void setIntentionActive();

    public abstract void setIntentionRest();

    public abstract void setIntentionAttack(WorldObject target);

    public abstract void setIntentionCast(Skill skill, WorldObject target);

    public abstract void setIntentionMoveTo(ILocational destination);

    public abstract void setIntentionFollow(WorldObject target);

    public abstract void setIntentionPickUp(WorldObject item);

    public abstract void setIntentionInteract(WorldObject object);

    public abstract void notifyActionThink();

    public abstract void notifyActionAttacked(WorldObject attacker);

    public abstract void notifyActionAggression(WorldObject target, int aggro);

    public abstract void notifyActionStunned();

    public abstract void notifyActionParalyzed();

    public abstract void notifyActionSleeping();

    public abstract void notifyActionRooted();

    public abstract void notifyActionConfused();

    public abstract void notifyActionMuted();

    public abstract void notifyActionEvaded(WorldObject attacker);

    public abstract void notifyActionReadyToAct();

    public abstract void notifyActionUserCmd(Object arg0, Object arg1);

    public abstract void notifyActionArrived();

    public abstract void notifyActionArrivedRevalidate();

    public abstract void notifyActionArrivedBlocked(Location location);

    public abstract void notifyActionForgetObject(WorldObject object);

    public abstract void notifyActionCancel();

    public abstract void notifyActionDeath();

    public abstract void notifyActionFakeDeath();

    public abstract void notifyActionAfraid(WorldObject effector, boolean start);

    public abstract void notifyActionFinishCasting();

    /**
     * Cancel action client side by sending Server->Client packet ActionFailed to the Player actor.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     */
    protected void clientActionFailed() {
        if (actor.isPlayer()) {
            actor.sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    /**
     * Move the actor to Pawn server side AND client side by sending Server->Client packet MoveToPawn <i>(broadcast)</i>.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     * @param pawn
     * @param offsetValue
     */
    public void moveToPawn(WorldObject pawn, int offsetValue) {
        // Check if actor can move.
        if (!actor.isMovementDisabled() && !actor.isAttackingNow() && !actor.isCastingNow()) {
            int offset = offsetValue;
            if (offset < 10) {
                offset = 10;
            }

            // Prevent possible extra calls to this function (there is none?), also don't send movetopawn packets too
            // often.
            final int gameTime = GameTimeTaskManager.getInstance().getGameTicks();
            if (actor.isMoving() && (target == pawn)) {
                if (clientMovingToPawnOffset == offset) {
                    if (gameTime < moveToPawnTimeout) {
                        return;
                    }
                }
                // Minimum time to calculate new route is 2 seconds.
                else if (actor.isOnGeodataPath() && (gameTime < (moveToPawnTimeout + 10))) {
                    return;
                }
            }

            // Set AI movement data.
            clientMovingToPawnOffset = offset;
            target = pawn;
            moveToPawnTimeout = gameTime;
            moveToPawnTimeout += 1000 / GameTimeTaskManager.MILLIS_IN_TICK;

            if (pawn == null) {
                return;
            }

            // Calculate movement data for a move to location action and add the actor to movingObjects of
            // GameTimeTaskManager.
            actor.moveToLocation(pawn.getX(), pawn.getY(), pawn.getZ(), offset);

            // May result to make monsters stop moving.
            // if (!_actor.isMoving())
            // {
            // clientActionFailed();
            // return;
            // }

            // Send a Server->Client packet MoveToPawn/MoveToLocation to the actor and all Player in its known players.
            if (pawn.isCreature()) {
                if (actor.isOnGeodataPath()) {
                    actor.broadcastMoveToLocation();
                    clientMovingToPawnOffset = 0;
                } else {
                    final WorldRegion region = actor.getWorldRegion();
                    if ((region != null) && region.isActive()) {
                        actor.broadcastPacket(new MoveToPawn(actor, pawn, offset));
                    }
                }
            } else {
                actor.broadcastMoveToLocation();
            }
        } else {
            clientActionFailed();
        }
    }

    public void moveTo(ILocational loc) {
        moveTo(loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Move the actor to Location (x,y,z) server side AND client side by sending Server->Client packet MoveToLocation <i>(broadcast)</i>.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     * @param x
     * @param y
     * @param z
     */
    protected void moveTo(int x, int y, int z) {
        // Check if actor can move.
        if (!actor.isMovementDisabled()) {
            // Set AI movement data.
            clientMovingToPawnOffset = 0;

            // Calculate movement data for a move to location action and add the actor to movingObjects of
            // GameTimeTaskManager.
            actor.moveToLocation(x, y, z, 0);

            // Send a Server->Client packet MoveToLocation to the actor and all Player in its known players.
            actor.broadcastMoveToLocation();
        } else {
            clientActionFailed();
        }
    }

    /**
     * Stop the actor movement server side AND client side by sending Server->Client packet StopMove/StopRotation <i>(broadcast)</i>.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     * @param loc
     */
    public void clientStopMoving(Location loc) {
        // Stop movement of the Creature.
        if (actor.isMoving()) {
            actor.stopMove(loc);
        }

        clientMovingToPawnOffset = 0;
    }

    /**
     * Client has already arrived to target, no need to force StopMove packet.
     */
    protected void clientStoppedMoving() {
        if (clientMovingToPawnOffset > 0) // movetoPawn needs to be stopped.
        {
            clientMovingToPawnOffset = 0;
            actor.broadcastPacket(new StopMove(actor));
        }
    }

    public void setAutoAttacking(boolean isAutoAttacking) {
        if (actor.isSummon()) {
            final Summon summon = actor.asSummon();
            if (summon.getOwner() != null) {
                summon.getOwner().getAI().setAutoAttacking(isAutoAttacking);
            }
            return;
        }

        autoAttacking = isAutoAttacking;
    }

    /**
     * Start the actor Auto Attack client side by sending Server->Client packet AutoAttackStart <i>(broadcast)</i>.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     */
    public void clientStartAutoAttack() {
        // Non attackable NPCs should not get in combat.
        if (actor.isNpc() && (!actor.isAttackable() || actor.isCoreAIDisabled())) {
            return;
        }

        if (actor.isSummon()) {
            final Summon summon = actor.asSummon();
            if (summon.getOwner() != null) {
                summon.getOwner().getAI().clientStartAutoAttack();
            }
            return;
        }

        if (!autoAttacking) {
            if (actor.isPlayer()) {
                final Player player = actor.asPlayer();
                if (player.hasSummon()) {
                    final Summon summon = player.getSummon();
                    summon.broadcastPacket(new AutoAttackStart(summon.getObjectId()));
                }
            }

            // Send a Server->Client packet AutoAttackStart to the actor and all Player in its known players.
            actor.broadcastPacket(new AutoAttackStart(actor.getObjectId()));
            setAutoAttacking(true);
        }

        AttackStanceTaskManager.getInstance().addAttackStanceTask(actor);
    }

    /**
     * Stop the actor auto-attack client side by sending Server->Client packet AutoAttackStop <i>(broadcast)</i>.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     */
    public void clientStopAutoAttack() {
        if (actor.isSummon()) {
            final Summon summon = actor.asSummon();
            if (summon.getOwner() != null) {
                summon.getOwner().getAI().clientStopAutoAttack();
            }
            return;
        }

        if (actor.isPlayer()) {
            if (!AttackStanceTaskManager.getInstance().hasAttackStanceTask(actor) && isAutoAttacking()) {
                AttackStanceTaskManager.getInstance().addAttackStanceTask(actor);
            }
        } else if (autoAttacking) {
            actor.broadcastPacket(new AutoAttackStop(actor.getObjectId()));
            setAutoAttacking(false);
        }
    }

    /**
     * Kill the actor client side by sending Server->Client packet AutoAttackStop, StopMove/StopRotation, Die <i>(broadcast)</i>.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     */
    protected void clientNotifyDead() {
        // Send a Server->Client packet Die to the actor and all Player in its known players.
        actor.broadcastPacket(new Die(actor));

        // Init AI
        intention = Intention.IDLE;
        target = null;
        castTarget = null;
        attackTarget = null;

        // Cancel the follow task if necessary.
        stopFollow();
    }

    /**
     * Update the state of this actor client side by sending Server->Client packet MoveToPawn/MoveToLocation and AutoAttackStart to the Player player.<br>
     * <font color=#FF0000><b><u>Caution</u>: Low level function, used by AI subclasses</b></font>
     * @param player The PlayerIstance to notify with state of this Creature
     */
    public void describeStateToPlayer(Player player) {
        if (actor.isVisibleFor(player) && actor.isMoving()) {
            if ((clientMovingToPawnOffset != 0) && isFollowing()) {
                // Send a Server->Client packet MoveToPawn to the actor and all Player in its known players.
                player.sendPacket(new MoveToPawn(actor, followTarget, clientMovingToPawnOffset));
            } else {
                // Send a Server->Client packet MoveToLocation to the actor and all Player in its known players.
                player.sendPacket(new MoveToLocation(actor));
            }
        }
    }

    public boolean isFollowing() {
        return (followTarget != null)
                && followTarget.isCreature()
                && ((intention == Intention.FOLLOW)
                        || CreatureFollowTaskManager.getInstance().isFollowing(actor));
    }

    /**
     * Create and Launch an AI Follow Task to execute every 1s.
     * @param target The Creature to follow
     */
    public void startFollow(Creature target) {
        startFollow(target, -1);
    }

    /**
     * Create and Launch an AI Follow Task to execute every 0.5s, following at specified range.
     * @param target The Creature to follow
     * @param range
     */
    public void startFollow(Creature target, int range) {
        stopFollow();
        followTarget = target;
        if (range == -1) {
            CreatureFollowTaskManager.getInstance().addNormalFollow(actor, range);
        } else {
            CreatureFollowTaskManager.getInstance().addAttackFollow(actor, range);
        }
    }

    /**
     * Stop an AI Follow Task.
     */
    public void stopFollow() {
        CreatureFollowTaskManager.getInstance().remove(actor);
        followTarget = null;
    }

    /**
     * Stop all Ai tasks and futures.
     */
    public void stopAITask() {
        stopFollow();
    }

    @Override
    public String toString() {
        return "Actor: " + actor;
    }
}
