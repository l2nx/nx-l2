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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.holders.creature.EffectList;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectTaskInfo;
import app.l2nx.highfive.mechanics.effects.EffectTickTask;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Buff Info.<br>
 * Complex DTO that holds all the information for a given buff (or debuff or dance/song) set of effects issued by an skill.
 * @author Zoey76, Mobius
 */
@SingleWriter
public class BuffInfo {
    private final Creature effector;

    private final Creature effected;

    private final Skill skill;

    // Filled before the buff joins an effect list and never changed after, so its own walks need no copy.
    private final ObjectArrayList<AbstractEffect> effects = new ObjectArrayList<>(1);

    // Effects are shared skill templates, hence identity keys; linked for a stable cancel order.
    private final Reference2ObjectLinkedOpenHashMap<AbstractEffect, EffectTaskInfo> tasks =
            new Reference2ObjectLinkedOpenHashMap<>(1);

    private final EffectTiming timing;

    private SkillFinishType finishType = SkillFinishType.NORMAL;

    private boolean inUse = true;

    private boolean finished;

    public BuffInfo(Creature effector, Creature effected, Skill skill) {
        this.effector = effector;
        this.effected = effected;
        this.skill = skill;
        timing = new EffectTiming(Formulas.calcEffectAbnormalTime(effector, effected, skill), ClockBridge.gameMillis());
    }

    public Creature getEffector() {
        WorldPartition.checkOwner();
        return effector;
    }

    public Creature getEffected() {
        WorldPartition.checkOwner();
        return effected;
    }

    public Skill getSkill() {
        WorldPartition.checkOwner();
        return skill;
    }

    public List<AbstractEffect> getEffects() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(effects);
    }

    public boolean hasEffectType(EffectType type) {
        WorldPartition.checkOwner();
        for (int i = 0, size = effects.size(); i < size; i++) {
            if (effects.get(i).getEffectType() == type) {
                return true;
            }
        }

        return false;
    }

    public int getEffectFlags() {
        WorldPartition.checkOwner();
        int flags = 0;
        for (int i = 0, size = effects.size(); i < size; i++) {
            flags |= effects.get(i).getEffectFlags();
        }

        return flags;
    }

    public void setFinishType(SkillFinishType finishType) {
        WorldPartition.checkOwner();
        this.finishType = finishType;
    }

    public boolean isInUse() {
        WorldPartition.checkOwner();
        return inUse;
    }

    public void setInUse(boolean inUse) {
        WorldPartition.checkOwner();
        this.inUse = inUse;
    }

    public int getAbnormalTime() {
        WorldPartition.checkOwner();
        return timing.abnormalTime();
    }

    public void setAbnormalTime(int abnormalTime) {
        WorldPartition.checkOwner();
        timing.setAbnormalTime(abnormalTime);
    }

    public void addEffect(AbstractEffect effect) {
        WorldPartition.checkOwner();
        effects.add(effect);
    }

    public int getTime() {
        WorldPartition.checkOwner();
        return timing.remainingSeconds(ClockBridge.gameMillis());
    }

    public boolean isExpired() {
        WorldPartition.checkOwner();
        return timing.isExpired(ClockBridge.gameMillis());
    }

    /** {@code null} when the effect has already run out and is waiting for removal. */
    public StoredEffect toStoredEffect() {
        WorldPartition.checkOwner();
        return timing.toStored(ClockBridge.gameMillis());
    }

    /** Must be called before the buff is added to the effect list, which schedules its ticks. */
    public void restoreTiming(StoredEffect stored) {
        WorldPartition.checkOwner();
        timing.restore(stored, ClockBridge.gameMillis());
    }

    public boolean isRemoved() {
        WorldPartition.checkOwner();
        return finishType == SkillFinishType.REMOVED;
    }

    public boolean isFinished() {
        WorldPartition.checkOwner();
        return finished;
    }

    /**
     * Does not remove the buff info from the effect list; call
     * {@link EffectList#stopSkillEffects(SkillFinishType, Skill)} for that.
     */
    public void stopAllEffects(SkillFinishType type, boolean broadcast) {
        WorldPartition.checkOwner();
        finishType = type;

        effected.removeBuffInfoTime(this);
        finishEffects(broadcast);
    }

    public void initializeEffects() {
        WorldPartition.checkOwner();
        if ((effected == null) || (skill == null)) {
            return;
        }

        if (effected.isPlayer()
                && (effected.asPlayer().hasEnteredWorld() || PlayerConfig.SHOW_EFFECT_MESSAGES_ON_LOGIN)
                && !skill.isPassive()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_S_EFFECT_CAN_BE_FELT);
            sm.addSkillName(skill);
            effected.sendPacket(sm);
        }

        if (timing.abnormalTime() > 0) {
            effected.addBuffInfoTime(this);
        }

        boolean update = false;
        for (int i = 0, size = effects.size(); i < size; i++) {
            final AbstractEffect effect = effects.get(i);
            if (effect.isInstant() || (effected.isDead() && !skill.isPassive())) {
                continue;
            }

            effect.onStart(effector, effected, skill);

            // A start that stopped this buff already cleared tasks and stats; anything added now would never be
            // removed.
            if (finished) {
                return;
            }

            // Target died from the initial effect: a continuous effect would tick forever.
            if (effected.isDead() && !skill.isPassive()) {
                continue;
            }

            if (effect.getTicks() > 0) {
                final long tickPeriod = (long) effect.getTicks() * PlayerConfig.EFFECT_TICK_RATIO;
                final EffectTickTask effectTask = new EffectTickTask(this, effect);
                tasks.put(
                        effect,
                        new EffectTaskInfo(
                                effectTask,
                                WorldPartition.loop()
                                        .scheduleAtFixedRate(
                                                effectTask,
                                                timing.firstTickDelay(ClockBridge.gameMillis(), tickPeriod),
                                                tickPeriod,
                                                TimeUnit.MILLISECONDS)));
            }

            effected.addStatFuncs(effect.getStatFuncs(effector, effected, skill));

            update = true;
        }

        if (update) {
            if (effected.isSummon()) {
                effected.asSummon().updateAndBroadcastStatus(1);
            }

            addAbnormalVisualEffects();
        }
    }

    public void onTick(AbstractEffect effect) {
        WorldPartition.checkOwner();
        boolean continueForever = false;

        if (inUse) {
            continueForever = effect.onActionTime(effector, effected, skill);
        }

        if (!continueForever && skill.isToggle()) {
            final EffectTaskInfo task = tasks.get(effect);
            if (task != null) {
                cancel(task);
                effected.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill);
            }
        }
    }

    public void finishEffects(boolean broadcast) {
        WorldPartition.checkOwner();
        finished = true;
        cancelTasks();
        removeStatsOfEffects();

        for (int i = 0, size = effects.size(); i < size; i++) {
            final AbstractEffect effect = effects.get(i);
            if (!effect.isInstant()) {
                effect.onExit(effector, effected, skill);
            }
        }

        removeAbnormalVisualEffects(broadcast);

        if (!(effected.isSummon() && !effected.asSummon().getOwner().hasSummon())) {
            SystemMessageId smId = null;
            if (finishType == SkillFinishType.SILENT) {
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

        if (this == effected.getEffectList().getShortBuff()) {
            effected.getEffectList().shortBuffStatusUpdate(null);
        }
    }

    /** Stops the effect ticks without exiting the effects: a replaced passive must not run its exit, which may clamp HP. */
    public void stopTicks() {
        WorldPartition.checkOwner();
        cancelTasks();
    }

    private void cancelTasks() {
        for (EffectTaskInfo task : tasks.values()) {
            cancel(task);
        }

        tasks.clear();
    }

    private static void cancel(EffectTaskInfo task) {
        final ScheduledFuture<?> schedule = task.getScheduledFuture();
        if ((schedule != null) && !schedule.isCancelled() && !schedule.isDone()) {
            schedule.cancel(true);
        }
    }

    public boolean isAbnormalType(AbnormalType type) {
        WorldPartition.checkOwner();
        return skill.getAbnormalType() == type;
    }

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

        effected.updateAbnormalEffect();
    }

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

    public void addStats() {
        WorldPartition.checkOwner();
        for (int i = 0, size = effects.size(); i < size; i++) {
            effected.addStatFuncs(effects.get(i).getStatFuncs(effector, effected, skill));
        }
    }

    public void removeStats() {
        WorldPartition.checkOwner();
        removeStatsOfEffects();
    }

    private void removeStatsOfEffects() {
        for (int i = 0, size = effects.size(); i < size; i++) {
            effected.removeStatsOwner(effects.get(i));
        }

        effected.removeStatsOwner(skill);
    }

    @Override
    public String toString() {
        return "BuffInfo [effector=" + effector + ", effected=" + effected + ", skill=" + skill + ", effects=" + effects
                + ", tasks=" + tasks + ", abnormalTime=" + timing.abnormalTime() + ", isRemoved="
                + (finishType == SkillFinishType.REMOVED) + ", isInUse=" + inUse + "]";
    }
}
