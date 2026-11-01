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
package app.l2nx.highfive.entity.actor.holders.creature;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameTask;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbnormalStatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOlympiadSpelledInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PartySpelled;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShortBuffStatusUpdate;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Effect lists.<br>
 * Holds all the buff infos that are affecting a creature.<br>
 * Manages the logic that controls whether a buff is added, remove, replaced or set inactive.<br>
 * The lists keep insertion order: the client shows the icons in it, and a buff that replaces a stacked one goes to
 * the end. An effect's exit may stop other buffs, so every walk that calls out runs over a copy.
 * @author Zoey76, Mobius
 */
@Slf4j
@SingleWriter
public class EffectList {
    private static final Predicate<BuffInfo> EVERY = _ -> true;

    private final ObjectArrayList<BuffInfo> buffs = new ObjectArrayList<>();
    private final ObjectArrayList<BuffInfo> triggered = new ObjectArrayList<>();
    private final ObjectArrayList<BuffInfo> dances = new ObjectArrayList<>();
    private final ObjectArrayList<BuffInfo> toggles = new ObjectArrayList<>();
    private final ObjectArrayList<BuffInfo> debuffs = new ObjectArrayList<>();
    /** They bypass most of the actions and they are not included in most operations. */
    private final ObjectArrayList<BuffInfo> passives = new ObjectArrayList<>();

    private final EnumMap<AbnormalType, BuffInfo> stackedEffects = new EnumMap<>(AbnormalType.class);
    private final EnumSet<AbnormalType> blockedAbnormalTypes = EnumSet.noneOf(AbnormalType.class);
    private BuffInfo shortBuff = null;
    private boolean hasBuffsRemovedOnAnyAction = false;
    private boolean hasBuffsRemovedOnDamage = false;
    private boolean hasDebuffsRemovedOnDamage = false;
    private int effectFlags;
    private boolean partyOnly = false;
    private final Creature owner;
    private int hiddenBuffs;

    // Counts buffs added to any list, so a stop pass can tell an exit added one behind its copy.
    private int adds;
    private ScheduledFuture<?> updateEffectIconTask;

    public EffectList(Creature owner) {
        this.owner = owner;
    }

    public List<BuffInfo> getBuffs() {
        WorldPartition.checkOwner();
        return snapshotOf(buffs);
    }

    /** Appends the buffs, triggered buffs and dances, the kinds a cancel or steal picks from, in that order. */
    public void collectStealable(List<? super BuffInfo> into) {
        WorldPartition.checkOwner();
        into.addAll(buffs);
        into.addAll(triggered);
        into.addAll(dances);
    }

    public void collectDebuffs(List<? super BuffInfo> into) {
        WorldPartition.checkOwner();
        into.addAll(debuffs);
    }

    public List<BuffInfo> getTriggered() {
        WorldPartition.checkOwner();
        return snapshotOf(triggered);
    }

    public List<BuffInfo> getDances() {
        WorldPartition.checkOwner();
        return snapshotOf(dances);
    }

    public List<BuffInfo> getToggles() {
        WorldPartition.checkOwner();
        return snapshotOf(toggles);
    }

    public List<BuffInfo> getDebuffs() {
        WorldPartition.checkOwner();
        return snapshotOf(debuffs);
    }

    public List<BuffInfo> getPassives() {
        WorldPartition.checkOwner();
        return snapshotOf(passives);
    }

    public List<BuffInfo> getEffects() {
        WorldPartition.checkOwner();
        if (hasNoEffects()) {
            return List.of();
        }

        final ObjectArrayList<BuffInfo> effects = new ObjectArrayList<>(
                buffs.size() + triggered.size() + dances.size() + toggles.size() + debuffs.size());
        effects.addAll(buffs);
        effects.addAll(triggered);
        effects.addAll(dances);
        effects.addAll(toggles);
        effects.addAll(debuffs);
        return ObjectLists.unmodifiable(effects);
    }

    private static List<BuffInfo> snapshotOf(ObjectArrayList<BuffInfo> effects) {
        return effects.isEmpty() ? List.of() : ObjectLists.unmodifiable(new ObjectArrayList<>(effects));
    }

    private ObjectArrayList<BuffInfo> getEffectList(Skill skill) {
        if (skill == null) {
            return null;
        }

        final ObjectArrayList<BuffInfo> effects;
        if (skill.isPassive()) {
            effects = passives;
        } else if (skill.isDebuff()) {
            effects = debuffs;
        } else if (skill.isTriggeredSkill()) {
            effects = triggered;
        } else if (skill.isDance()) {
            effects = dances;
        } else if (skill.isToggle()) {
            effects = toggles;
        } else {
            effects = buffs;
        }

        return effects;
    }

    /** TODO: remove once all effect types are replaced by abnormal skill types. */
    public BuffInfo getFirstEffect(EffectType type) {
        WorldPartition.checkOwner();
        BuffInfo info = firstWithEffect(buffs, type);
        if (info == null) {
            info = firstWithEffect(triggered, type);
        }

        if (info == null) {
            info = firstWithEffect(dances, type);
        }

        if (info == null) {
            info = firstWithEffect(toggles, type);
        }

        if (info == null) {
            info = firstWithEffect(debuffs, type);
        }

        return info;
    }

    private static BuffInfo firstWithEffect(ObjectArrayList<BuffInfo> effects, EffectType type) {
        for (int i = 0, size = effects.size(); i < size; i++) {
            final BuffInfo info = effects.get(i);
            if (info.hasEffectType(type)) {
                return info;
            }
        }

        return null;
    }

    public boolean isAffectedBySkill(int skillId) {
        WorldPartition.checkOwner();
        return findBySkillId(skillId) != null;
    }

    public BuffInfo getBuffInfoBySkillId(int skillId) {
        WorldPartition.checkOwner();
        return findBySkillId(skillId);
    }

    private BuffInfo findBySkillId(int skillId) {
        BuffInfo info = firstWithSkill(buffs, skillId);
        if (info == null) {
            info = firstWithSkill(triggered, skillId);
        }

        if (info == null) {
            info = firstWithSkill(dances, skillId);
        }

        if (info == null) {
            info = firstWithSkill(toggles, skillId);
        }

        if (info == null) {
            info = firstWithSkill(debuffs, skillId);
        }

        if (info == null) {
            info = firstWithSkill(passives, skillId);
        }

        return info;
    }

    private static BuffInfo firstWithSkill(ObjectArrayList<BuffInfo> effects, int skillId) {
        for (int i = 0, size = effects.size(); i < size; i++) {
            final BuffInfo info = effects.get(i);
            if (info.getSkill().getId() == skillId) {
                return info;
            }
        }

        return null;
    }

    public boolean hasDispellableDebuff() {
        WorldPartition.checkOwner();
        for (int i = 0, size = debuffs.size(); i < size; i++) {
            if (debuffs.get(i).getSkill().canBeDispeled()) {
                return true;
            }
        }

        return false;
    }

    public BuffInfo getBuffInfoByAbnormalType(AbnormalType type) {
        WorldPartition.checkOwner();
        return stackedEffects.get(type);
    }

    public void addBlockedAbnormalTypes(Set<AbnormalType> blockedAbnormalTypes) {
        WorldPartition.checkOwner();
        this.blockedAbnormalTypes.addAll(blockedAbnormalTypes);
    }

    public boolean removeBlockedAbnormalTypes(Set<AbnormalType> blockedBuffSlots) {
        WorldPartition.checkOwner();
        return blockedAbnormalTypes.removeAll(blockedBuffSlots);
    }

    public Set<AbnormalType> getBlockedAbnormalTypes() {
        WorldPartition.checkOwner();
        return Collections.unmodifiableSet(EnumSet.copyOf(blockedAbnormalTypes));
    }

    public boolean isBlockedAbnormalType(AbnormalType type) {
        WorldPartition.checkOwner();
        return blockedAbnormalTypes.contains(type);
    }

    public BuffInfo getShortBuff() {
        WorldPartition.checkOwner();
        return shortBuff;
    }

    public void shortBuffStatusUpdate(BuffInfo info) {
        WorldPartition.checkOwner();
        if (owner.isPlayer()) {
            shortBuff = info;
            if (info != null) {
                owner.sendPacket(new ShortBuffStatusUpdate(
                        info.getSkill().getId(), info.getSkill().getLevel(), info.getTime()));
            } else {
                owner.sendPacket(ShortBuffStatusUpdate.RESET_SHORT_BUFF);
            }
        }
    }

    private boolean doesStack(Skill skill) {
        final AbnormalType type = skill.getAbnormalType();
        if (type.isNone() || hasNoEffects()) {
            return false;
        }

        final ObjectArrayList<BuffInfo> effects = getEffectList(skill);
        for (int i = 0, size = effects.size(); i < size; i++) {
            if (effects.get(i).getSkill().getAbnormalType() == type) {
                return true;
            }
        }

        return false;
    }

    /** Excludes hidden buffs and the short buff. */
    public int getBuffCount() {
        WorldPartition.checkOwner();
        return buffCount();
    }

    private int buffCount() {
        return buffs.isEmpty() ? 0 : buffs.size() - hiddenBuffs - (shortBuff != null ? 1 : 0);
    }

    public int getDanceCount() {
        WorldPartition.checkOwner();
        return dances.size();
    }

    public int getTriggeredBuffCount() {
        WorldPartition.checkOwner();
        return triggered.size();
    }

    public int getHiddenBuffsCount() {
        WorldPartition.checkOwner();
        return hiddenBuffs;
    }

    private boolean stopAndRemove(
            boolean broadcast, SkillFinishType type, BuffInfo info, ObjectArrayList<BuffInfo> buffs) {
        if (info == null) {
            return false;
        }

        // Already removed means already stopped; stopping again would strip a newer buff of the same skill.
        if (!buffs.remove(info)) {
            return false;
        }

        info.stopAllEffects(type, broadcast);

        if (!info.isInUse()) {
            hiddenBuffs--;
        } else {
            stackedEffects.remove(info.getSkill().getAbnormalType(), info);
        }

        if (info.getSkill().isAbnormalInstant()) {
            final BuffInfo hidden = firstHidden(info.getSkill().getAbnormalType());
            if (hidden != null) {
                hidden.setInUse(true);
                hidden.addStats();
                stackedEffects.put(hidden.getSkill().getAbnormalType(), hidden);
                hiddenBuffs--;
            }
        }

        if (type != SkillFinishType.REMOVED) {
            info.getSkill().applyEffectScope(EffectScope.END, info, true, false);
        }

        return true;
    }

    private BuffInfo firstHidden(AbnormalType type) {
        for (int i = 0, size = buffs.size(); i < size; i++) {
            final BuffInfo buff = buffs.get(i);
            if ((buff.getSkill().getAbnormalType() == type) && !buff.isInUse()) {
                return buff;
            }
        }

        return null;
    }

    /** The filter must not change the effect list. */
    private void stopAndRemoveIf(boolean broadcast, ObjectArrayList<BuffInfo> effects, Predicate<BuffInfo> filter) {
        if (effects.isEmpty()) {
            return;
        }

        // An exit may add a buff behind the copy, so repeat while a pass added any.
        int addsBefore;
        do {
            addsBefore = adds;
            try (Snapshot<BuffInfo> copy = ScratchBuffers.snapshot(effects)) {
                for (int i = 0, size = copy.size(); i < size; i++) {
                    final BuffInfo info = copy.get(i);
                    if (filter.test(info)) {
                        stopAndRemove(broadcast, SkillFinishType.REMOVED, info, effects);
                    }
                }
            }
        } while ((adds != addsBefore) && !effects.isEmpty());
    }

    public void stopAllEffects() {
        WorldPartition.checkOwner();
        stopAllBuffs(false, true);
        stopAllDances(false);
        stopAllToggles(false);
        stopAllDebuffs(false);
        stackedEffects.clear();
        updateEffectList(true);
        owner.updateAbnormalEffect();
    }

    public void stopAllEffectsExceptThoseThatLastThroughDeath() {
        WorldPartition.checkOwner();
        final boolean update = !hasNoEffects();
        final Predicate<BuffInfo> endsOnDeath = info -> !info.getSkill().isStayAfterDeath();
        stopAndRemoveIf(true, buffs, endsOnDeath);
        stopAndRemoveIf(true, triggered, endsOnDeath);
        stopAndRemoveIf(true, debuffs, endsOnDeath);
        stopAndRemoveIf(true, dances, endsOnDeath);
        stopAndRemoveIf(true, toggles, endsOnDeath);
        updateEffectList(update);
    }

    public void stopAllEffectsWithoutExclusions(boolean update, boolean broadcast) {
        WorldPartition.checkOwner();
        stopAndRemoveIf(broadcast, buffs, EVERY);
        stopAndRemoveIf(broadcast, triggered, EVERY);
        stopAndRemoveIf(broadcast, dances, EVERY);
        stopAndRemoveIf(broadcast, toggles, EVERY);
        stopAndRemoveIf(broadcast, debuffs, EVERY);
        stopAndRemoveIf(broadcast, passives, EVERY);
        if (update) {
            updateEffectList(broadcast);
        }
    }

    public void stopAllEffectsNotStayOnSubclassChange() {
        WorldPartition.checkOwner();
        final boolean update = !hasNoEffects();
        final Predicate<BuffInfo> endsOnSubclassChange =
                info -> !info.getSkill().isStayOnSubclassChange();
        stopAndRemoveIf(true, buffs, endsOnSubclassChange);
        stopAndRemoveIf(true, triggered, endsOnSubclassChange);
        stopAndRemoveIf(true, debuffs, endsOnSubclassChange);
        stopAndRemoveIf(true, dances, endsOnSubclassChange);
        stopAndRemoveIf(true, toggles, endsOnSubclassChange);
        updateEffectList(update);
    }

    public void stopAllBuffs(boolean update, boolean triggered) {
        WorldPartition.checkOwner();
        stopAndRemoveIf(update, buffs, EVERY);
        if (triggered) {
            stopAndRemoveIf(update, this.triggered, EVERY);
        }

        updateEffectList(update);
    }

    public void stopAllToggles() {
        WorldPartition.checkOwner();
        stopAllToggles(true);
    }

    public void stopAllToggles(boolean update) {
        WorldPartition.checkOwner();
        if (toggles.isEmpty()) {
            return;
        }

        stopAndRemoveIf(update, toggles, EVERY);
        updateEffectList(update);
    }

    public void stopAllDances(boolean update) {
        WorldPartition.checkOwner();
        if (dances.isEmpty()) {
            return;
        }

        stopAndRemoveIf(update, dances, EVERY);
        updateEffectList(update);
    }

    public void stopAllDebuffs(boolean update) {
        WorldPartition.checkOwner();
        if (debuffs.isEmpty()) {
            return;
        }

        stopAndRemoveIf(update, debuffs, EVERY);
        updateEffectList(update);
    }

    /** TODO: remove once all effect types are replaced by abnormal skill types. */
    public void stopEffects(EffectType type) {
        WorldPartition.checkOwner();
        final boolean update = !hasNoEffects();
        final Predicate<BuffInfo> ofType = info -> info.hasEffectType(type);
        stopAndRemoveIf(true, buffs, ofType);
        stopAndRemoveIf(true, triggered, ofType);
        stopAndRemoveIf(true, dances, ofType);
        stopAndRemoveIf(true, toggles, ofType);
        stopAndRemoveIf(true, debuffs, ofType);
        updateEffectList(update);
    }

    public void stopSkillEffects(SkillFinishType type, int skillId) {
        WorldPartition.checkOwner();
        stopSkillEffectsById(type, skillId);
    }

    private void stopSkillEffectsById(SkillFinishType type, int skillId) {
        final BuffInfo info = findBySkillId(skillId);
        if (info != null) {
            removeAndUpdate(type, info);
        }
    }

    public void stopSkillEffects(SkillFinishType type, Skill skill) {
        WorldPartition.checkOwner();
        if (skill != null) {
            stopSkillEffectsById(type, skill.getId());
        }
    }

    public boolean stopSkillEffects(SkillFinishType removeType, AbnormalType abnormalType) {
        WorldPartition.checkOwner();
        return stopStacked(removeType, abnormalType);
    }

    private boolean stopStacked(SkillFinishType removeType, AbnormalType abnormalType) {
        final BuffInfo old = stackedEffects.remove(abnormalType);
        if (old != null) {
            removeAndUpdate(removeType, old);
            return true;
        }

        return false;
    }

    public void stopEffectsOnAction() {
        WorldPartition.checkOwner();
        if (!hasBuffsRemovedOnAnyAction) {
            return;
        }

        final boolean update = !hasNoEffects();
        final Predicate<BuffInfo> endsOnAction = info -> info.getSkill().isRemovedOnAnyActionExceptMove();
        stopAndRemoveIf(true, buffs, endsOnAction);
        stopAndRemoveIf(true, triggered, endsOnAction);
        stopAndRemoveIf(true, debuffs, endsOnAction);
        stopAndRemoveIf(true, dances, endsOnAction);
        stopAndRemoveIf(true, toggles, endsOnAction);
        updateEffectList(update);
    }

    public void stopEffectsOnDamage(boolean awake) {
        WorldPartition.checkOwner();
        if (!awake) {
            return;
        }

        boolean update = false;
        final Predicate<BuffInfo> endsOnDamage = info -> info.getSkill().isRemovedOnDamage();
        if (hasBuffsRemovedOnDamage) {
            update = !buffs.isEmpty() || !triggered.isEmpty() || !dances.isEmpty() || !toggles.isEmpty();
            stopAndRemoveIf(true, buffs, endsOnDamage);
            stopAndRemoveIf(true, triggered, endsOnDamage);
            stopAndRemoveIf(true, dances, endsOnDamage);
            stopAndRemoveIf(true, toggles, endsOnDamage);
        }

        if (hasDebuffsRemovedOnDamage && !debuffs.isEmpty()) {
            stopAndRemoveIf(true, debuffs, endsOnDamage);
            update = true;
        }

        updateEffectList(update);
    }

    public void updateEffectIcons(boolean partyOnly) {
        WorldPartition.checkOwner();
        if (partyOnly) {
            this.partyOnly = true;
        }

        updateEffectList(true);
    }

    public boolean isEmpty() {
        WorldPartition.checkOwner();
        return hasNoEffects();
    }

    // Passives do not count.
    private boolean hasNoEffects() {
        return buffs.isEmpty() && triggered.isEmpty() && dances.isEmpty() && debuffs.isEmpty() && toggles.isEmpty();
    }

    public boolean hasBuffs() {
        WorldPartition.checkOwner();
        return !buffs.isEmpty();
    }

    public boolean hasTriggered() {
        WorldPartition.checkOwner();
        return !triggered.isEmpty();
    }

    public boolean hasDances() {
        WorldPartition.checkOwner();
        return !dances.isEmpty();
    }

    public boolean hasToggles() {
        WorldPartition.checkOwner();
        return !toggles.isEmpty();
    }

    public boolean hasDebuffs() {
        WorldPartition.checkOwner();
        return !debuffs.isEmpty();
    }

    public boolean hasPassives() {
        WorldPartition.checkOwner();
        return !passives.isEmpty();
    }

    /** A buff stopped by an earlier call is not passed to {@code function} again. */
    public void forEach(Function<BuffInfo, Boolean> function, boolean dances) {
        WorldPartition.checkOwner();
        boolean update = false;
        try (Snapshot<BuffInfo> copy = ScratchBuffers.empty()) {
            copy.addAll(buffs);
            copy.addAll(triggered);
            if (dances) {
                copy.addAll(this.dances);
            }

            copy.addAll(toggles);
            copy.addAll(debuffs);
            for (int i = 0, size = copy.size(); i < size; i++) {
                final BuffInfo info = copy.get(i);
                if (!info.isFinished()) {
                    update |= function.apply(info);
                }
            }
        }

        updateEffectList(update);
    }

    public void remove(SkillFinishType type, BuffInfo info) {
        WorldPartition.checkOwner();
        if (info == null) {
            return;
        }

        removeAndUpdate(type, info);
    }

    private void removeAndUpdate(SkillFinishType type, BuffInfo info) {
        stopAndRemove(true, type, info, getEffectList(info.getSkill()));
        updateEffectList(true);
    }

    public void add(BuffInfo info) {
        WorldPartition.checkOwner();
        if (info == null) {
            return;
        }

        final Skill skill = info.getSkill();
        if (blockedAbnormalTypes.contains(skill.getAbnormalType())) {
            return;
        }

        if (skill.isPassive()) {
            if (!skill.getAbnormalType().isNone()) {
                log.warn("Passive {} with abnormal type: {}!", skill, skill.getAbnormalType());
            }

            if (!skill.checkCondition(info.getEffector(), info.getEffected(), false)) {
                return;
            }

            try (Snapshot<BuffInfo> copy = ScratchBuffers.snapshot(passives)) {
                for (int i = 0, size = copy.size(); i < size; i++) {
                    final BuffInfo b = copy.get(i);
                    if (b.getSkill().getId() == skill.getId()) {
                        b.setInUse(false);
                        b.removeStats();
                        b.stopTicks();
                        passives.remove(b);
                    }
                }
            }

            passives.add(info);
            adds++;
            info.initializeEffects();
            return;
        }

        if (info.getEffected().isDead()) {
            return;
        }

        // Remove then add, not Map#put, so the new buff goes to the end of the list.
        if (skill.getAbnormalType().isNone()) {
            stopSkillEffectsById(SkillFinishType.NORMAL, skill.getId());
        } else {
            if (stackedEffects.containsKey(skill.getAbnormalType())) {
                BuffInfo stackedInfo = stackedEffects.get(skill.getAbnormalType());

                if ((stackedInfo != null)
                        && (skill.getAbnormalLevel() >= stackedInfo.getSkill().getAbnormalLevel())) {
                    // An herb hides the lesser buff: stays listed, stats removed, ticks keep running.
                    if (skill.isAbnormalInstant()) {
                        if (stackedInfo.getSkill().isAbnormalInstant()) {
                            stopStacked(SkillFinishType.NORMAL, skill.getAbnormalType());
                            stackedInfo = stackedEffects.get(skill.getAbnormalType());
                        }

                        if (stackedInfo != null) {
                            stackedInfo.setInUse(false);
                            stackedInfo.removeStats();
                            hiddenBuffs++;
                        }
                    } else {
                        if (stackedInfo.getSkill().isAbnormalInstant()) {
                            stopStacked(SkillFinishType.NORMAL, skill.getAbnormalType());
                        }

                        stopStacked(SkillFinishType.NORMAL, skill.getAbnormalType());
                    }
                } else {
                    return;
                }
            }

            stackedEffects.put(skill.getAbnormalType(), info);
        }

        final ObjectArrayList<BuffInfo> effects = getEffectList(skill);

        if (!skill.isDebuff() && !skill.isToggle() && !skill.is7Signs() && !doesStack(skill)) {
            int buffsToRemove = -1;
            if (skill.isDance()) {
                buffsToRemove = dances.size() - PlayerConfig.DANCES_MAX_AMOUNT;
            } else if (skill.isTriggeredSkill()) {
                buffsToRemove = triggered.size() - PlayerConfig.TRIGGERED_BUFFS_MAX_AMOUNT;
            } else if (!skill.isHealingPotionSkill()) {
                buffsToRemove = buffCount() - owner.getStat().getMaxBuffCount();
            }

            if (buffsToRemove >= 0) {
                try (Snapshot<BuffInfo> copy = ScratchBuffers.snapshot(effects)) {
                    for (int i = 0, size = copy.size(); (i < size) && (buffsToRemove >= 0); i++) {
                        final BuffInfo bi = copy.get(i);
                        if (bi.isInUse() && stopAndRemove(true, SkillFinishType.REMOVED, bi, effects)) {
                            buffsToRemove--;
                        }
                    }
                }
            }
        }

        effects.add(info);
        adds++;
        info.initializeEffects();
        updateEffectList(true);
    }

    private void updateEffectIcons() {
        if (owner == null) {
            return;
        }

        updateEffectFlags();

        if (!owner.isPlayable()) {
            return;
        }

        if (updateEffectIconTask == null) {
            updateEffectIconTask = WorldPartition.loop()
                    .schedule(
                            () -> {
                                AbnormalStatusUpdate asu = null;
                                PartySpelled ps = null;
                                PartySpelled psSummon = null;
                                ExOlympiadSpelledInfo os = null;
                                boolean isSummon = false;

                                if (owner.isPlayer()) {
                                    if (partyOnly) {
                                        partyOnly = false;
                                    } else {
                                        asu = new AbnormalStatusUpdate();
                                    }

                                    if (owner.isInParty()) {
                                        ps = new PartySpelled(owner);
                                    }

                                    final Player player = owner.asPlayer();
                                    if (player.isInOlympiadMode() && player.isOlympiadStart()) {
                                        os = new ExOlympiadSpelledInfo(player);
                                    }
                                } else if (owner.isSummon()) {
                                    isSummon = true;
                                    ps = new PartySpelled(owner);
                                    psSummon = new PartySpelled(owner);
                                }

                                // The short buff update sends a packet, so it runs after the walk over the live list.
                                BuffInfo healingPotion = null;
                                for (int i = 0, size = buffs.size(); i < size; i++) {
                                    final BuffInfo info = buffs.get(i);
                                    if (info.getSkill().isHealingPotionSkill()) {
                                        healingPotion = info;
                                    } else {
                                        addIcon(info, asu, ps, psSummon, os, isSummon);
                                    }
                                }

                                if (healingPotion != null) {
                                    shortBuffStatusUpdate(healingPotion);
                                }

                                addIcons(triggered, asu, ps, psSummon, os, isSummon);
                                addIcons(dances, asu, ps, psSummon, os, isSummon);
                                addIcons(toggles, asu, ps, psSummon, os, isSummon);
                                addIcons(debuffs, asu, ps, psSummon, os, isSummon);

                                if (asu != null) {
                                    owner.sendPacket(asu);
                                }

                                if (ps != null) {
                                    if (owner.isSummon()) {
                                        final Player summonOwner =
                                                owner.asSummon().getOwner();
                                        if (summonOwner != null) {
                                            if (summonOwner.isInParty()) {
                                                summonOwner.getParty().broadcastToPartyMembers(summonOwner, psSummon);
                                                summonOwner.sendPacket(ps);
                                            } else {
                                                summonOwner.sendPacket(ps);
                                            }
                                        }
                                    } else if (owner.isPlayer() && owner.isInParty()) {
                                        owner.getParty().broadcastPacket(ps);
                                    }
                                }

                                if (os != null) {
                                    final OlympiadGameTask game = OlympiadGameManager.getInstance()
                                            .getOlympiadTask(owner.asPlayer().getOlympiadGameId());
                                    if ((game != null) && game.isBattleStarted()) {
                                        game.getZone().broadcastPacketToObservers(os);
                                    }
                                }

                                updateEffectIconTask = null;
                            },
                            300,
                            TimeUnit.MILLISECONDS);
        }
    }

    private static void addIcons(
            ObjectArrayList<BuffInfo> effects,
            AbnormalStatusUpdate asu,
            PartySpelled ps,
            PartySpelled psSummon,
            ExOlympiadSpelledInfo os,
            boolean isSummon) {
        for (int i = 0, size = effects.size(); i < size; i++) {
            addIcon(effects.get(i), asu, ps, psSummon, os, isSummon);
        }
    }

    private static void addIcon(
            BuffInfo info,
            AbnormalStatusUpdate asu,
            PartySpelled ps,
            PartySpelled psSummon,
            ExOlympiadSpelledInfo os,
            boolean isSummon) {
        if ((info == null) || !info.isInUse()) {
            return;
        }

        final Skill skill = info.getSkill();
        if (asu != null) {
            asu.addSkill(info);
        }

        if ((ps != null) && (isSummon || !skill.isToggle())) {
            ps.addSkill(info);
        }

        if ((psSummon != null) && !skill.isToggle()) {
            psSummon.addSkill(info);
        }

        if (os != null) {
            os.addSkill(info);
        }
    }

    private void updateEffectList(boolean update) {
        if (update) {
            updateEffectIcons();
            computeEffectFlags();
        }
    }

    /** TODO: update in real time (add/remove/stop/activate/deactivate) to avoid iterations. */
    private void updateEffectFlags() {
        // Recomputed from scratch so the stop-on-action/damage guards skip their walks once the last such buff ended.
        hasBuffsRemovedOnAnyAction = false;
        hasBuffsRemovedOnDamage = false;
        hasDebuffsRemovedOnDamage = false;
        markRemovedOnActionOrDamage(buffs);
        markRemovedOnActionOrDamage(triggered);
        markRemovedOnActionOrDamage(dances);
        markRemovedOnActionOrDamage(toggles);

        for (int i = 0, size = debuffs.size(); i < size; i++) {
            if (debuffs.get(i).getSkill().isRemovedOnDamage()) {
                hasDebuffsRemovedOnDamage = true;
            }
        }
    }

    private void markRemovedOnActionOrDamage(ObjectArrayList<BuffInfo> effects) {
        for (int i = 0, size = effects.size(); i < size; i++) {
            final Skill skill = effects.get(i).getSkill();
            if (skill.isRemovedOnAnyActionExceptMove()) {
                hasBuffsRemovedOnAnyAction = true;
            }

            if (skill.isRemovedOnDamage()) {
                hasBuffsRemovedOnDamage = true;
            }
        }
    }

    /** TODO: update in real time to avoid iterations. */
    private void computeEffectFlags() {
        effectFlags = flagsOf(buffs) | flagsOf(triggered) | flagsOf(debuffs) | flagsOf(dances) | flagsOf(toggles);
    }

    private static int flagsOf(ObjectArrayList<BuffInfo> effects) {
        int flags = 0;
        for (int i = 0, size = effects.size(); i < size; i++) {
            flags |= effects.get(i).getEffectFlags();
        }

        return flags;
    }

    public boolean isAffected(EffectFlag flag) {
        WorldPartition.checkOwner();
        return (effectFlags & flag.getMask()) != 0;
    }

    public boolean isAffectedByAbnormalType(AbnormalType type) {
        WorldPartition.checkOwner();
        return stackedEffects.containsKey(type);
    }
}
