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
package app.l2nx.highfive.mechanics.skill;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.holders.creature.EffectList;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectTaskInfo;
import app.l2nx.highfive.mechanics.effects.EffectTickTask;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;

/**
 * Buff Info.<br>
 * Complex DTO that holds all the information for a given buff (or debuff or dance/song) set of effects issued by an skill.
 * @author Zoey76, Mobius
 */
public class BuffInfo {
    // Data
    /** Data. */
    @Getter
    private final Creature effector;

    @Getter
    private final Creature effected;

    @Getter
    private final Skill skill;
    /** The effects. */
    @Getter
    private final List<AbstractEffect> effects = new ArrayList<>(1);

    // Tasks
    /** Effect tasks for ticks. */
    private final Map<AbstractEffect, EffectTaskInfo> tasks = new ConcurrentHashMap<>();

    // Time and ticks
    /** Abnormal time. */
    @Getter
    @Setter
    private int abnormalTime;
    /** The game ticks at the start of this effect. */
    @Getter
    private final int periodStartTicks;

    // Misc
    /** If {@code true} then this effect has been cancelled. */
    @Setter
    private volatile SkillFinishType finishType = SkillFinishType.NORMAL;
    /** If {@code true} then this effect is in use (or has been stop because an Herb took place). */
    @Getter
    @Setter
    private boolean inUse = true;

    /**
     * Buff Info constructor.
     * @param effector
     * @param effected
     * @param skill
     */
    public BuffInfo(Creature effector, Creature effected, Skill skill) {
        this.effector = effector;
        this.effected = effected;
        this.skill = skill;
        abnormalTime = Formulas.calcEffectAbnormalTime(effector, effected, skill);
        periodStartTicks = GameTimeTaskManager.getInstance().getGameTicks();
    }

    /**
     * Adds an effect to this buff info.
     * @param effect the effect to add
     */
    public void addEffect(AbstractEffect effect) {
        effects.add(effect);
    }

    /**
     * Adds an effect task to this buff info.
     * @param effect the effect that owns the task
     * @param effectTaskInfo the task info
     */
    private void addTask(AbstractEffect effect, EffectTaskInfo effectTaskInfo) {
        tasks.put(effect, effectTaskInfo);
    }

    /**
     * Gets the task for the given effect.
     * @param effect the effect
     * @return the task
     */
    private EffectTaskInfo getEffectTask(AbstractEffect effect) {
        return tasks.get(effect);
    }

    /**
     * Get the remaining time in seconds for this buff info.
     * @return the elapsed time
     */
    public int getTime() {
        return abnormalTime
                - ((GameTimeTaskManager.getInstance().getGameTicks() - periodStartTicks)
                        / GameTimeTaskManager.TICKS_PER_SECOND);
    }

    /**
     * Verify if this buff info has been cancelled.
     * @return {@code true} if this buff info has been cancelled, {@code false} otherwise
     */
    public boolean isRemoved() {
        return finishType == SkillFinishType.REMOVED;
    }

    /**
     * Stops all the effects for this buff info.<br>
     * Removes effects stats.<br>
     * <b>It will not remove the buff info from the effect list</b>.<br>
     * Instead call {@link EffectList#stopSkillEffects(SkillFinishType, Skill)}
     * @param type determines the system message that will be sent.
     * @param broadcast if {@code true} broadcast abnormal visual effects
     */
    public void stopAllEffects(SkillFinishType type, boolean broadcast) {
        setFinishType(type);

        // Remove this buff info from BuffFinishTask.
        effected.removeBuffInfoTime(this);
        finishEffects(broadcast);
    }

    public void initializeEffects() {
        if ((effected == null) || (skill == null)) {
            return;
        }

        // When effects are initialized, they have successfully landed.
        if (effected.isPlayer()
                && (effected.asPlayer().hasEnteredWorld() || PlayerConfig.SHOW_EFFECT_MESSAGES_ON_LOGIN)
                && !skill.isPassive()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_S_EFFECT_CAN_BE_FELT);
            sm.addSkillName(skill);
            effected.sendPacket(sm);
        }

        // Creates a task that will stop all the effects.
        if (abnormalTime > 0) {
            effected.addBuffInfoTime(this);
        }

        boolean update = false;
        for (AbstractEffect effect : effects) {
            if (effect.isInstant() || (effected.isDead() && !skill.isPassive())) {
                continue;
            }

            // Call on start.
            effect.onStart(effector, effected, skill);

            // Do not add continuous effect if target just died from the initial effect, otherwise they'll be ticked
            // forever.
            if (effected.isDead() && !skill.isPassive()) {
                continue;
            }

            // If it's a continuous effect, if has ticks schedule a task with period, otherwise schedule a simple task
            // to end it.
            if (effect.getTicks() > 0) {
                // The task for the effect ticks.
                final EffectTickTask effectTask = new EffectTickTask(this, effect);
                addTask(
                        effect,
                        new EffectTaskInfo(
                                effectTask,
                                ThreadPool.scheduleAtFixedRate(
                                        effectTask,
                                        effect.getTicks() * PlayerConfig.EFFECT_TICK_RATIO,
                                        effect.getTicks() * PlayerConfig.EFFECT_TICK_RATIO)));
            }

            // Add stats.
            effected.addStatFuncs(effect.getStatFuncs(effector, effected, skill));

            update = true;
        }

        if (update) {
            if (effected.isSummon()) {
                effected.asSummon().updateAndBroadcastStatus(1);
            }

            // Add abnormal visual effects.
            addAbnormalVisualEffects();
        }
    }

    /**
     * Called on each tick.<br>
     * Verify if the effect should end and the effect task should be cancelled.
     * @param effect the effect that is ticking
     */
    public void onTick(AbstractEffect effect) {
        boolean continueForever = false;

        // If the effect is in use, allow it to affect the effected.
        if (inUse) {
            // Callback for on action time event.
            continueForever = effect.onActionTime(effector, effected, skill);
        }

        if (!continueForever && skill.isToggle()) {
            final EffectTaskInfo task = getEffectTask(effect);
            if (task != null) {
                final ScheduledFuture<?> schedule = task.getScheduledFuture();
                if ((schedule != null) && !schedule.isCancelled() && !schedule.isDone()) {
                    schedule.cancel(true); // Don't allow to finish current run.
                }

                effected.getEffectList()
                        .stopSkillEffects(SkillFinishType.REMOVED, skill); // Remove the buff from the effect list.
            }
        }
    }

    public void finishEffects(boolean broadcast) {
        // Cancels the ticking task.
        for (EffectTaskInfo effectTask : tasks.values()) {
            final ScheduledFuture<?> schedule = effectTask.getScheduledFuture();
            if ((schedule != null) && !schedule.isCancelled() && !schedule.isDone()) {
                schedule.cancel(true); // Don't allow to finish current run.
            }
        }

        // Remove stats
        removeStats();

        // Notify on exit.
        for (AbstractEffect effect : effects) {
            // Instant effects shouldn't call onExit(..).
            if ((effect != null) && !effect.isInstant()) {
                effect.onExit(effector, effected, skill);
            }
        }

        // Remove abnormal visual effects.
        removeAbnormalVisualEffects(broadcast);

        // Set the proper system message.
        if (!(effected.isSummon() && !effected.asSummon().getOwner().hasSummon())) {
            SystemMessageId smId = null;
            if (finishType == SkillFinishType.SILENT) {
                // smId is null.
            } else if (skill.isToggle()) {
                smId = SystemMessageId.S1_HAS_BEEN_ABORTED;
            } else if (finishType == SkillFinishType.REMOVED) {
                smId = SystemMessageId.THE_EFFECT_OF_S1_HAS_BEEN_REMOVED;
            } else if (!skill.isPassive()) {
                smId = SystemMessageId.S1_HAS_WORN_OFF;
            }

            if (smId != null) {
                final SystemMessage sm = new SystemMessage(smId);
                sm.addSkillName(skill);
                effected.sendPacket(sm);
            }
        }

        // Remove short buff.
        if (this == effected.getEffectList().getShortBuff()) {
            effected.getEffectList().shortBuffStatusUpdate(null);
        }
    }

    public boolean isAbnormalType(AbnormalType type) {
        return skill.getAbnormalType() == type;
    }

    /**
     * Applies all the abnormal visual effects to the effected.<br>
     * Prevents multiple updates.
     */
    private void addAbnormalVisualEffects() {
        if (skill.hasAbnormalVisualEffects()) {
            effected.startAbnormalVisualEffect(false, skill.getAbnormalVisualEffects());
        }

        if (effected.isPlayer() && skill.hasAbnormalVisualEffectsEvent()) {
            effected.startAbnormalVisualEffect(false, skill.getAbnormalVisualEffectsEvent());
        }

        if (skill.hasAbnormalVisualEffectsSpecial()) {
            effected.startAbnormalVisualEffect(false, skill.getAbnormalVisualEffectsSpecial());
        }

        // Update abnormal visual effects.
        effected.updateAbnormalEffect();
    }

    /**
     * Removes all the abnormal visual effects from the effected.<br>
     * Prevents multiple updates.
     * @param broadcast if {@code true} broadcast abnormal visual effects
     */
    private void removeAbnormalVisualEffects(boolean broadcast) {
        if ((effected == null) || (skill == null)) {
            return;
        }

        if (skill.hasAbnormalVisualEffects()) {
            effected.stopAbnormalVisualEffect(false, skill.getAbnormalVisualEffects());
        }

        if (effected.isPlayer() && skill.hasAbnormalVisualEffectsEvent()) {
            effected.stopAbnormalVisualEffect(false, skill.getAbnormalVisualEffectsEvent());
        }

        if (skill.hasAbnormalVisualEffectsSpecial()) {
            effected.stopAbnormalVisualEffect(false, skill.getAbnormalVisualEffectsSpecial());
        }

        if (broadcast) {
            effected.updateAbnormalEffect();
        }
    }

    /**
     * Adds the buff stats.
     */
    public void addStats() {
        effects.forEach(effect -> effected.addStatFuncs(effect.getStatFuncs(effector, effected, skill)));
    }

    /**
     * Removes the buff stats.
     */
    public void removeStats() {
        effects.forEach(effected::removeStatsOwner);
        effected.removeStatsOwner(skill);
    }

    @Override
    public String toString() {
        return "BuffInfo [effector=" + effector + ", effected=" + effected + ", skill=" + skill + ", effects="
                + effects + ", tasks=" + tasks + ", abnormalTime=" + abnormalTime + ", periodStartTicks="
                + periodStartTicks + ", isRemoved=" + isRemoved() + ", isInUse=" + inUse + "]";
    }
}
