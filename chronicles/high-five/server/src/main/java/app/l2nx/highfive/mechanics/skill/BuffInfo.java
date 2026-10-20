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

import app.l2nx.highfive.clock.ClockBridge;
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
    @Getter
    private final Creature effector;

    @Getter
    private final Creature effected;

    @Getter
    private final Skill skill;

    @Getter
    private final List<AbstractEffect> effects = new ArrayList<>(1);

    private final Map<AbstractEffect, EffectTaskInfo> tasks = new ConcurrentHashMap<>();

    private final EffectTiming timing;

    @Setter
    private volatile SkillFinishType finishType = SkillFinishType.NORMAL;

    @Getter
    @Setter
    private boolean inUse = true;

    public BuffInfo(Creature effector, Creature effected, Skill skill) {
        this.effector = effector;
        this.effected = effected;
        this.skill = skill;
        timing = new EffectTiming(Formulas.calcEffectAbnormalTime(effector, effected, skill), ClockBridge.gameMillis());
    }

    public int getAbnormalTime() {
        return timing.abnormalTime();
    }

    public void setAbnormalTime(int abnormalTime) {
        timing.setAbnormalTime(abnormalTime);
    }

    public void addEffect(AbstractEffect effect) {
        effects.add(effect);
    }

    private void addTask(AbstractEffect effect, EffectTaskInfo effectTaskInfo) {
        tasks.put(effect, effectTaskInfo);
    }

    private EffectTaskInfo getEffectTask(AbstractEffect effect) {
        return tasks.get(effect);
    }

    /** @return the remaining time in seconds. */
    public int getTime() {
        return timing.remainingSeconds(ClockBridge.gameMillis());
    }

    public boolean isExpired() {
        return timing.isExpired(ClockBridge.gameMillis());
    }

    /** {@code null} when the effect has already run out and is waiting for removal. */
    public StoredEffect toStoredEffect() {
        return timing.toStored(ClockBridge.gameMillis());
    }

    /** Must be called before the buff is added to the effect list, which schedules its ticks. */
    public void restoreTiming(StoredEffect stored) {
        timing.restore(stored, ClockBridge.gameMillis());
    }

    public boolean isRemoved() {
        return finishType == SkillFinishType.REMOVED;
    }

    /**
     * Does not remove the buff info from the effect list; call
     * {@link EffectList#stopSkillEffects(SkillFinishType, Skill)} for that.
     */
    public void stopAllEffects(SkillFinishType type, boolean broadcast) {
        setFinishType(type);

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

        if (timing.abnormalTime() > 0) {
            effected.addBuffInfoTime(this);
        }

        boolean update = false;
        for (AbstractEffect effect : effects) {
            if (effect.isInstant() || (effected.isDead() && !skill.isPassive())) {
                continue;
            }

            effect.onStart(effector, effected, skill);

            // Do not add continuous effect if target just died from the initial effect, otherwise they'll be ticked
            // forever.
            if (effected.isDead() && !skill.isPassive()) {
                continue;
            }

            if (effect.getTicks() > 0) {
                final long tickPeriod = (long) effect.getTicks() * PlayerConfig.EFFECT_TICK_RATIO;
                final EffectTickTask effectTask = new EffectTickTask(this, effect);
                addTask(
                        effect,
                        new EffectTaskInfo(
                                effectTask,
                                ThreadPool.scheduleAtFixedRate(
                                        effectTask,
                                        timing.firstTickDelay(ClockBridge.gameMillis(), tickPeriod),
                                        tickPeriod)));
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
        boolean continueForever = false;

        if (inUse) {
            continueForever = effect.onActionTime(effector, effected, skill);
        }

        if (!continueForever && skill.isToggle()) {
            final EffectTaskInfo task = getEffectTask(effect);
            if (task != null) {
                final ScheduledFuture<?> schedule = task.getScheduledFuture();
                if ((schedule != null) && !schedule.isCancelled() && !schedule.isDone()) {
                    schedule.cancel(true);
                }

                effected.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill);
            }
        }
    }

    public void finishEffects(boolean broadcast) {
        for (EffectTaskInfo effectTask : tasks.values()) {
            final ScheduledFuture<?> schedule = effectTask.getScheduledFuture();
            if ((schedule != null) && !schedule.isCancelled() && !schedule.isDone()) {
                schedule.cancel(true);
            }
        }

        removeStats();

        for (AbstractEffect effect : effects) {
            // Instant effects shouldn't call onExit(..).
            if ((effect != null) && !effect.isInstant()) {
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

    public boolean isAbnormalType(AbnormalType type) {
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
        effects.forEach(effect -> effected.addStatFuncs(effect.getStatFuncs(effector, effected, skill)));
    }

    public void removeStats() {
        effects.forEach(effected::removeStatsOwner);
        effected.removeStatsOwner(skill);
    }

    @Override
    public String toString() {
        return "BuffInfo [effector=" + effector + ", effected=" + effected + ", skill=" + skill + ", effects=" + effects
                + ", tasks=" + tasks + ", abnormalTime=" + timing.abnormalTime() + ", isRemoved=" + isRemoved()
                + ", isInUse=" + inUse + "]";
    }
}
