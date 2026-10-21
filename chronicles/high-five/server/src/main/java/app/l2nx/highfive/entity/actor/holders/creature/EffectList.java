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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
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
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Effect lists.<br>
 * Holds all the buff infos that are affecting a creature.<br>
 * Manages the logic that controls whether a buff is added, remove, replaced or set inactive.<br>
 * Uses maps with skill ID as key and buff info DTO as value to avoid iterations.<br>
 * Methods may resemble List interface, although it doesn't implement such interface.
 * @author Zoey76, Mobius
 */
@Slf4j
public class EffectList {
    /** Queue containing all effects from buffs for this effect list. */
    private final Queue<BuffInfo> buffs = new ConcurrentLinkedQueue<>();
    /** Queue containing all triggered skills for this effect list. */
    private final Queue<BuffInfo> triggered = new ConcurrentLinkedQueue<>();
    /** Queue containing all dances/songs for this effect list. */
    private final Queue<BuffInfo> dances = new ConcurrentLinkedQueue<>();
    /** Queue containing all toggle for this effect list. */
    private final Queue<BuffInfo> toggles = new ConcurrentLinkedQueue<>();
    /** Queue containing all debuffs for this effect list. */
    private final Queue<BuffInfo> debuffs = new ConcurrentLinkedQueue<>();
    /** Queue containing all passives for this effect list. They bypass most of the actions and they are not included in most operations. */
    private final Queue<BuffInfo> passives = new ConcurrentLinkedQueue<>();
    /** Map containing the all stacked effect in progress for each abnormal type. */
    private final Map<AbnormalType, BuffInfo> stackedEffects = new ConcurrentHashMap<>();
    /** Set containing all {@code AbnormalType}s that shouldn't be added to this creature effect list. */
    private final Set<AbnormalType> blockedAbnormalTypes = EnumSet.noneOf(AbnormalType.class);
    /** Short buff skill ID. */
    @Getter
    private BuffInfo shortBuff = null;
    /** If {@code true} this effect list has buffs removed on any action. */
    private volatile boolean hasBuffsRemovedOnAnyAction = false;
    /** If {@code true} this effect list has buffs removed on damage. */
    private volatile boolean hasBuffsRemovedOnDamage = false;
    /** If {@code true} this effect list has debuffs removed on damage. */
    private volatile boolean hasDebuffsRemovedOnDamage = false;
    /** Effect flags. */
    private int effectFlags;
    /** If {@code true} only party icons need to be updated. */
    private boolean partyOnly = false;
    /** The owner of this effect list. */
    private final Creature owner;
    /** Hidden buffs count, prevents iterations. */
    private final AtomicInteger hiddenBuffs = new AtomicInteger();
    /** Delay task **/
    private ScheduledFuture<?> updateEffectIconTask;

    /**
     * Constructor for effect list.
     * @param owner the creature that owns this effect list
     */
    public EffectList(Creature owner) {
        this.owner = owner;
    }

    /**
     * Gets buff skills.
     * @return the buff skills
     */
    public Queue<BuffInfo> getBuffs() {
        return buffs;
    }

    /**
     * Gets triggered skill skills.
     * @return the triggered skill skills
     */
    public Queue<BuffInfo> getTriggered() {
        return triggered;
    }

    /**
     * Gets dance/song skills.
     * @return the dance/song skills
     */
    public Queue<BuffInfo> getDances() {
        return dances;
    }

    /**
     * Gets toggle skills.
     * @return the toggle skills
     */
    public Queue<BuffInfo> getToggles() {
        return toggles;
    }

    /**
     * Gets debuff skills.
     * @return the debuff skills
     */
    public Queue<BuffInfo> getDebuffs() {
        return debuffs;
    }

    /**
     * Gets passive skills.
     * @return the passive skills
     */
    public Queue<BuffInfo> getPassives() {
        return passives;
    }

    /**
     * Gets all the effects on this effect list.
     * @return all the effects on this effect list
     */
    public List<BuffInfo> getEffects() {
        if (isEmpty()) {
            return Collections.<BuffInfo>emptyList();
        }

        final List<BuffInfo> buffs = new ArrayList<>();
        if (hasBuffs()) {
            buffs.addAll(this.buffs);
        }

        if (hasTriggered()) {
            buffs.addAll(triggered);
        }

        if (hasDances()) {
            buffs.addAll(dances);
        }

        if (hasToggles()) {
            buffs.addAll(toggles);
        }

        if (hasDebuffs()) {
            buffs.addAll(debuffs);
        }

        return buffs;
    }

    /**
     * Gets the effect list where the skill effects should be.
     * @param skill the skill
     * @return the effect list
     */
    private Queue<BuffInfo> getEffectList(Skill skill) {
        if (skill == null) {
            return null;
        }

        final Queue<BuffInfo> effects;
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

    /**
     * Gets the first effect for the given effect type.<br>
     * Prevents initialization.<br>
     * TODO: Remove this method after all the effect types gets replaced by abnormal skill types.
     * @param type the effect type
     * @return the first effect matching the given effect type
     */
    public BuffInfo getFirstEffect(EffectType type) {
        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (info != null) {
                    for (AbstractEffect effect : info.getEffects()) {
                        if ((effect != null) && (effect.getEffectType() == type)) {
                            return info;
                        }
                    }
                }
            }
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (info != null) {
                    for (AbstractEffect effect : info.getEffects()) {
                        if ((effect != null) && (effect.getEffectType() == type)) {
                            return info;
                        }
                    }
                }
            }
        }

        if (hasDances()) {
            for (BuffInfo info : dances) {
                if (info != null) {
                    for (AbstractEffect effect : info.getEffects()) {
                        if ((effect != null) && (effect.getEffectType() == type)) {
                            return info;
                        }
                    }
                }
            }
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (info != null) {
                    for (AbstractEffect effect : info.getEffects()) {
                        if ((effect != null) && (effect.getEffectType() == type)) {
                            return info;
                        }
                    }
                }
            }
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if (info != null) {
                    for (AbstractEffect effect : info.getEffects()) {
                        if ((effect != null) && (effect.getEffectType() == type)) {
                            return info;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Verifies if this effect list contains the given skill ID.<br>
     * Prevents initialization.
     * @param skillId the skill ID to verify
     * @return {@code true} if the skill ID is present in the effect list, {@code false} otherwise
     */
    public boolean isAffectedBySkill(int skillId) {
        return getBuffInfoBySkillId(skillId) != null;
    }

    /**
     * Gets the buff info by skill ID.<br>
     * Prevents initialization.
     * @param skillId the skill ID
     * @return the buff info
     */
    public BuffInfo getBuffInfoBySkillId(int skillId) {
        BuffInfo info = null;

        if (hasBuffs()) {
            for (BuffInfo b : buffs) {
                if (b.getSkill().getId() == skillId) {
                    info = b;
                    break;
                }
            }
        }

        if (hasTriggered() && (info == null)) {
            for (BuffInfo b : triggered) {
                if (b.getSkill().getId() == skillId) {
                    info = b;
                    break;
                }
            }
        }

        if (hasDances() && (info == null)) {
            for (BuffInfo b : dances) {
                if (b.getSkill().getId() == skillId) {
                    info = b;
                    break;
                }
            }
        }

        if (hasToggles() && (info == null)) {
            for (BuffInfo b : toggles) {
                if (b.getSkill().getId() == skillId) {
                    info = b;
                    break;
                }
            }
        }

        if (hasDebuffs() && (info == null)) {
            for (BuffInfo b : debuffs) {
                if (b.getSkill().getId() == skillId) {
                    info = b;
                    break;
                }
            }
        }

        if (hasPassives() && (info == null)) {
            for (BuffInfo b : passives) {
                if (b.getSkill().getId() == skillId) {
                    info = b;
                    break;
                }
            }
        }

        return info;
    }

    /**
     * Gets a buff info by abnormal type.<br>
     * It's O(1) for every buff in this effect list.
     * @param type the abnormal skill type
     * @return the buff info if it's present, {@code null} otherwise
     */
    public BuffInfo getBuffInfoByAbnormalType(AbnormalType type) {
        return stackedEffects.get(type);
    }

    /**
     * Adds {@code AbnormalType}s to the blocked buff slot set.
     * @param blockedAbnormalTypes the blocked buff slot set to add
     */
    public void addBlockedAbnormalTypes(Set<AbnormalType> blockedAbnormalTypes) {
        this.blockedAbnormalTypes.addAll(blockedAbnormalTypes);
    }

    /**
     * Removes {@code AbnormalType}s from the blocked buff slot set.
     * @param blockedBuffSlots the blocked buff slot set to remove
     * @return {@code true} if the blocked buff slots set has been modified, {@code false} otherwise
     */
    public boolean removeBlockedAbnormalTypes(Set<AbnormalType> blockedBuffSlots) {
        return blockedAbnormalTypes.removeAll(blockedBuffSlots);
    }

    /**
     * Gets all the blocked {@code AbnormalType}s for this creature effect list.
     * @return the current blocked {@code AbnormalType}s set in unmodifiable view.
     */
    public Set<AbnormalType> getBlockedAbnormalTypes() {
        return Collections.unmodifiableSet(blockedAbnormalTypes);
    }

    /**
     * Sets the Short Buff data and sends an update if the effected is a player.
     * @param info the buff info
     */
    public void shortBuffStatusUpdate(BuffInfo info) {
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

    /**
     * Checks if the given skill stacks with an existing one.
     * @param skill the skill to verify
     * @return {@code true} if this effect stacks with the given skill, {@code false} otherwise
     */
    private boolean doesStack(Skill skill) {
        final AbnormalType type = skill.getAbnormalType();
        if (type.isNone() || isEmpty()) {
            return false;
        }

        final Queue<BuffInfo> effects = getEffectList(skill);
        for (BuffInfo info : effects) {
            if ((info != null) && (info.getSkill().getAbnormalType() == type)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the buffs count without including the hidden buffs (after getting an Herb buff).<br>
     * Prevents initialization.
     * @return the number of buffs in this creature effect list
     */
    public int getBuffCount() {
        return hasBuffs() ? buffs.size() - hiddenBuffs.get() - (shortBuff != null ? 1 : 0) : 0;
    }

    /**
     * Gets the Songs/Dances count.<br>
     * Prevents initialization.
     * @return the number of Songs/Dances in this creature effect list
     */
    public int getDanceCount() {
        return hasDances() ? dances.size() : 0;
    }

    /**
     * Gets the triggered buffs count.<br>
     * Prevents initialization.
     * @return the number of triggered buffs in this creature effect list
     */
    public int getTriggeredBuffCount() {
        return hasTriggered() ? triggered.size() : 0;
    }

    /**
     * Gets the hidden buff count.
     * @return the number of hidden buffs
     */
    public int getHiddenBuffsCount() {
        return hiddenBuffs.get();
    }

    /**
     * Auxiliary method to stop all effects from a buff info and remove it from an effect list and stacked effects.
     * @param info the buff info
     */
    protected void stopAndRemove(BuffInfo info) {
        stopAndRemove(true, SkillFinishType.REMOVED, info, getEffectList(info.getSkill()));
    }

    /**
     * Auxiliary method to stop all effects from a buff info and remove it from an effect list and stacked effects.
     * @param broadcast if {@code true} broadcast abnormal visual effects
     * @param info the buff info
     * @param effects the effect list
     */
    protected void stopAndRemove(boolean broadcast, BuffInfo info, Queue<BuffInfo> effects) {
        stopAndRemove(broadcast, SkillFinishType.REMOVED, info, effects);
    }

    /**
     * Auxiliary method to stop all effects from a buff info and remove it from an effect list and stacked effects.
     * @param broadcast if {@code true} broadcast abnormal visual effects
     * @param type determines the system message that will be sent.
     * @param info the buff info
     * @param buffs the buff list
     */
    private void stopAndRemove(boolean broadcast, SkillFinishType type, BuffInfo info, Queue<BuffInfo> buffs) {
        if (info == null) {
            return;
        }

        // Removes the buff from the given effect list.
        // Already removed means already stopped, stopping again would strip a newer buff of the same skill.
        if (!buffs.remove(info)) {
            return;
        }

        // Stop the buff effects.
        info.stopAllEffects(type, broadcast);

        // If it's a hidden buff that ends, then decrease hidden buff count.
        if (!info.isInUse()) {
            hiddenBuffs.decrementAndGet();
        } else // Removes the buff from the stack.
        {
            stackedEffects.remove(info.getSkill().getAbnormalType(), info);
        }

        // If it's an herb that ends, check if there are hidden buffs.
        if (info.getSkill().isAbnormalInstant() && hasBuffs()) {
            for (BuffInfo buff : this.buffs) {
                if ((buff != null)
                        && (buff.getSkill().getAbnormalType() == info.getSkill().getAbnormalType())
                        && !buff.isInUse()) {
                    // Sets the buff in use again.
                    buff.setInUse(true);

                    // Adds the stats.
                    buff.addStats();

                    // Adds the buff to the stack.
                    stackedEffects.put(buff.getSkill().getAbnormalType(), buff);

                    // If it's a hidden buff that gets activated, then decrease hidden buff count.
                    hiddenBuffs.decrementAndGet();
                    break;
                }
            }
        }

        if (type != SkillFinishType.REMOVED) {
            info.getSkill().applyEffectScope(EffectScope.END, info, true, false);
        }
    }

    /**
     * Exits all effects in this effect list.<br>
     * Stops all the effects, clear the effect lists and updates the effect flags and icons.
     */
    public void stopAllEffects() {
        // Stop buffs.
        stopAllBuffs(false, true);

        // Stop dances and songs.
        stopAllDances(false);

        // Stop toggles.
        stopAllToggles(false);

        // Stop debuffs.
        stopAllDebuffs(false);

        stackedEffects.clear();

        // Update effect flags, icons and ave.
        updateEffectList(true);
        owner.updateAbnormalEffect();
    }

    /**
     * Stops all effects in this effect list except those that last through death.
     */
    public void stopAllEffectsExceptThoseThatLastThroughDeath() {
        boolean update = false;
        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (!info.getSkill().isStayAfterDeath()) {
                    stopAndRemove(true, info, buffs);
                }
            }

            update = true;
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (!info.getSkill().isStayAfterDeath()) {
                    stopAndRemove(true, info, triggered);
                }
            }

            update = true;
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if (!info.getSkill().isStayAfterDeath()) {
                    stopAndRemove(true, info, debuffs);
                }
            }

            update = true;
        }

        if (hasDances()) {
            for (BuffInfo info : dances) {
                if (!info.getSkill().isStayAfterDeath()) {
                    stopAndRemove(true, info, dances);
                }
            }

            update = true;
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (!info.getSkill().isStayAfterDeath()) {
                    stopAndRemove(true, info, toggles);
                }
            }

            update = true;
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Exits all effects in this effect list without excluding anything.<br>
     * Stops all the effects, clear the effect lists and updates the effect flags and icons.
     * @param update set to true to update the effect flags and icons.
     * @param broadcast {@code true} to broadcast update packets, {@code false} otherwise.
     */
    public void stopAllEffectsWithoutExclusions(boolean update, boolean broadcast) {
        for (BuffInfo info : buffs) {
            stopAndRemove(broadcast, info, buffs);
        }

        for (BuffInfo info : triggered) {
            stopAndRemove(broadcast, info, triggered);
        }

        for (BuffInfo info : dances) {
            stopAndRemove(broadcast, info, dances);
        }

        for (BuffInfo info : toggles) {
            stopAndRemove(broadcast, info, toggles);
        }

        for (BuffInfo info : debuffs) {
            stopAndRemove(broadcast, info, debuffs);
        }

        for (BuffInfo info : passives) {
            stopAndRemove(broadcast, info, passives);
        }

        // Update stats, effect flags and icons.
        if (update) {
            updateEffectList(broadcast);
        }
    }

    /**
     * Stop all effects that doesn't stay on sub-class change.
     */
    public void stopAllEffectsNotStayOnSubclassChange() {
        boolean update = false;
        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (!info.getSkill().isStayOnSubclassChange()) {
                    stopAndRemove(true, info, buffs);
                }
            }

            update = true;
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (!info.getSkill().isStayOnSubclassChange()) {
                    stopAndRemove(true, info, triggered);
                }
            }

            update = true;
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if (!info.getSkill().isStayOnSubclassChange()) {
                    stopAndRemove(true, info, debuffs);
                }
            }

            update = true;
        }

        if (hasDances()) {
            for (BuffInfo info : dances) {
                if (!info.getSkill().isStayOnSubclassChange()) {
                    stopAndRemove(true, info, dances);
                }
            }

            update = true;
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (!info.getSkill().isStayOnSubclassChange()) {
                    stopAndRemove(true, info, toggles);
                }
            }

            update = true;
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Stops all the active buffs.
     * @param update set to true to update the effect flags and icons
     * @param triggered if {@code true} stops triggered skills buffs
     */
    public void stopAllBuffs(boolean update, boolean triggered) {
        if (hasBuffs()) {
            buffs.forEach(b -> stopAndRemove(update, b, buffs));
        }

        if (triggered && hasTriggered()) {
            this.triggered.forEach(b -> stopAndRemove(update, b, this.triggered));
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Stops all active toggle skills.<br>
     * Performs an update.
     */
    public void stopAllToggles() {
        stopAllToggles(true);
    }

    /**
     * Stops all active toggle skills.
     * @param update set to true to update the effect flags and icons
     */
    public void stopAllToggles(boolean update) {
        if (!hasToggles()) {
            return;
        }

        toggles.forEach(b -> stopAndRemove(update, b, toggles));

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Stops all active dances/songs skills.
     * @param update set to true to update the effect flags and icons
     */
    public void stopAllDances(boolean update) {
        if (!hasDances()) {
            return;
        }

        dances.forEach(b -> stopAndRemove(update, b, dances));

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Stops all active dances/songs skills.
     * @param update set to true to update the effect flags and icons
     */
    public void stopAllDebuffs(boolean update) {
        if (!hasDebuffs()) {
            return;
        }

        debuffs.forEach(b -> stopAndRemove(update, b, debuffs));

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Exit all effects having a specified type.<br>
     * TODO: Remove after all effect types are replaced by abnormal skill types.
     * @param type the type of the effect to stop
     */
    public void stopEffects(EffectType type) {
        boolean update = false;
        final Consumer<BuffInfo> action = info -> {
            for (AbstractEffect effect : info.getEffects()) {
                if ((effect != null) && (effect.getEffectType() == type)) {
                    stopAndRemove(info);
                }
            }
        };

        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (info != null) {
                    action.accept(info);
                }
            }

            update = true;
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (info != null) {
                    action.accept(info);
                }
            }

            update = true;
        }

        if (hasDances()) {
            for (BuffInfo info : dances) {
                if (info != null) {
                    action.accept(info);
                }
            }

            update = true;
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (info != null) {
                    action.accept(info);
                }
            }

            update = true;
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if (info != null) {
                    action.accept(info);
                }
            }

            update = true;
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Exits all effects created by a specific skill ID.<br>
     * Removes the effects from the effect list.<br>
     * Removes the stats from the creature.<br>
     * Updates the effect flags and icons.<br>
     * @param type determines the system message that will be sent.
     * @param skillId the skill ID
     */
    public void stopSkillEffects(SkillFinishType type, int skillId) {
        final BuffInfo info = getBuffInfoBySkillId(skillId);
        if (info != null) {
            remove(type, info);
        }
    }

    /**
     * Exits all effects created by a specific skill.<br>
     * Removes the effects from the effect list.<br>
     * Removes the stats from the creature.<br>
     * Updates the effect flags and icons.<br>
     * @param type determines the system message that will be sent.
     * @param skill the skill
     */
    public void stopSkillEffects(SkillFinishType type, Skill skill) {
        if (skill != null) {
            stopSkillEffects(type, skill.getId());
        }
    }

    /**
     * Exits all effects created by a specific skill abnormal type.<br>
     * It's O(1) for every effect in this effect list except passive effects.<br>
     * @param removeType determines the system message that will be sent.
     * @param abnormalType the skill abnormal type
     * @return {@code true} if there was a buff info with the given abnormal type
     */
    public boolean stopSkillEffects(SkillFinishType removeType, AbnormalType abnormalType) {
        final BuffInfo old = stackedEffects.remove(abnormalType);
        if (old != null) {
            remove(removeType, old);
            return true;
        }

        return false;
    }

    /**
     * Exits all buffs effects of the skills with "removedOnAnyAction" set.<br>
     * Called on any action except movement (attack, cast).
     */
    public void stopEffectsOnAction() {
        if (!hasBuffsRemovedOnAnyAction) {
            return;
        }

        boolean update = false;

        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    stopAndRemove(true, info, buffs);
                }
            }

            update = true;
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    stopAndRemove(true, info, triggered);
                }
            }

            update = true;
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    stopAndRemove(true, info, debuffs);
                }
            }

            update = true;
        }

        if (hasDances()) {
            for (BuffInfo info : dances) {
                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    stopAndRemove(true, info, dances);
                }
            }

            update = true;
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    stopAndRemove(true, info, toggles);
                }
            }

            update = true;
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    public void stopEffectsOnDamage(boolean awake) {
        if (!awake) {
            return;
        }

        boolean update = false;
        if (hasBuffsRemovedOnDamage) {
            if (hasBuffs()) {
                for (BuffInfo info : buffs) {
                    if ((info != null) && info.getSkill().isRemovedOnDamage()) {
                        stopAndRemove(true, info, buffs);
                    }
                }

                update = true;
            }

            if (hasTriggered()) {
                for (BuffInfo info : triggered) {
                    if ((info != null) && info.getSkill().isRemovedOnDamage()) {
                        stopAndRemove(true, info, triggered);
                    }
                }

                update = true;
            }

            if (hasDances()) {
                for (BuffInfo info : dances) {
                    if ((info != null) && info.getSkill().isRemovedOnDamage()) {
                        stopAndRemove(true, info, dances);
                    }
                }

                update = true;
            }

            if (hasToggles()) {
                for (BuffInfo info : toggles) {
                    if ((info != null) && info.getSkill().isRemovedOnDamage()) {
                        stopAndRemove(true, info, toggles);
                    }
                }

                update = true;
            }
        }

        if (hasDebuffsRemovedOnDamage && hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if ((info != null) && info.getSkill().isRemovedOnDamage()) {
                    stopAndRemove(true, info, debuffs);
                }
            }

            update = true;
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * @param partyOnly
     */
    public void updateEffectIcons(boolean partyOnly) {
        if (partyOnly) {
            this.partyOnly = true;
        }

        // Update effect flags and icons.
        updateEffectList(true);
    }

    /**
     * Verify if this effect list is empty.<br>
     * Prevents initialization.
     * @return {@code true} if this effect list contains any skills
     */
    public boolean isEmpty() {
        return !hasBuffs() && !hasTriggered() && !hasDances() && !hasDebuffs() && !hasToggles();
    }

    /**
     * Verify if this effect list has buffs skills.<br>
     * Prevents initialization.
     * @return {@code true} if {@link #buffs} is not {@code null} and is not empty
     */
    public boolean hasBuffs() {
        return !buffs.isEmpty();
    }

    /**
     * Verify if this effect list has triggered skills.<br>
     * Prevents initialization.
     * @return {@code true} if {@link #triggered} is not {@code null} and is not empty
     */
    public boolean hasTriggered() {
        return !triggered.isEmpty();
    }

    /**
     * Verify if this effect list has dance/song skills.<br>
     * Prevents initialization.
     * @return {@code true} if {@link #dances} is not {@code null} and is not empty
     */
    public boolean hasDances() {
        return !dances.isEmpty();
    }

    /**
     * Verify if this effect list has toggle skills.<br>
     * Prevents initialization.
     * @return {@code true} if {@link #toggles} is not {@code null} and is not empty
     */
    public boolean hasToggles() {
        return !toggles.isEmpty();
    }

    /**
     * Verify if this effect list has debuffs skills.<br>
     * Prevents initialization.
     * @return {@code true} if {@link #debuffs} is not {@code null} and is not empty
     */
    public boolean hasDebuffs() {
        return !debuffs.isEmpty();
    }

    /**
     * Verify if this effect list has passive skills.<br>
     * Prevents initialization.
     * @return {@code true} if {@link #passives} is not {@code null} and is not empty
     */
    public boolean hasPassives() {
        return !passives.isEmpty();
    }

    /**
     * Executes a procedure for all effects.<br>
     * Prevents initialization.
     * @param function the function to execute
     * @param dances if {@code true} dances/songs will be included
     */
    public void forEach(Function<BuffInfo, Boolean> function, boolean dances) {
        boolean update = false;
        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                update |= function.apply(info);
            }
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                update |= function.apply(info);
            }
        }

        if (dances && hasDances()) {
            for (BuffInfo info : this.dances) {
                update |= function.apply(info);
            }
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                update |= function.apply(info);
            }
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                update |= function.apply(info);
            }
        }

        // Update effect flags and icons.
        updateEffectList(update);
    }

    /**
     * Removes a set of effects from this effect list.
     * @param type determines the system message that will be sent.
     * @param info the effects to remove
     */
    public void remove(SkillFinishType type, BuffInfo info) {
        if (info == null) {
            return;
        }

        // Remove the effect from creature effects.
        stopAndRemove(true, type, info, getEffectList(info.getSkill()));

        // Update effect flags and icons.
        updateEffectList(true);
    }

    /**
     * Adds a set of effects to this effect list.
     * @param info the buff info
     */
    public void add(BuffInfo info) {
        if (info == null) {
            return;
        }

        // Support for blocked buff slots.
        final Skill skill = info.getSkill();
        if (blockedAbnormalTypes.contains(skill.getAbnormalType())) {
            return;
        }

        // Passive effects are treated specially.
        if (skill.isPassive()) {
            // Passive effects don't need stack type!
            if (!skill.getAbnormalType().isNone()) {
                log.warn("Passive {} with abnormal type: {}!", skill, skill.getAbnormalType());
            }

            // Check for passive skill conditions.
            if (!skill.checkCondition(info.getEffector(), info.getEffected(), false)) {
                return;
            }

            // Puts the effects in the list.
            for (BuffInfo b : passives) {
                // Removes the old stats from the creature if the skill was present.
                if (b.getSkill().getId() == skill.getId()) {
                    b.setInUse(false);
                    b.removeStats();
                    passives.remove(b);
                }
            }

            passives.add(info);

            // Initialize effects.
            info.initializeEffects();
            return;
        }

        // Prevent adding and initializing buffs/effects on dead creatures.
        if (info.getEffected().isDead()) {
            return;
        }

        // The old effect is removed using Map#remove(key) instead of Map#put(key, value) (that would be the wisest
        // choice),
        // Because order matters and put method would insert in the same place it was before, instead of, at the end of
        // the effect list
        // Where new buff should be placed
        if (skill.getAbnormalType().isNone()) {
            stopSkillEffects(SkillFinishType.NORMAL, skill);
        } else // Verify stacked skills.
        {
            synchronized (this) {
                if (stackedEffects.containsKey(skill.getAbnormalType())) {
                    BuffInfo stackedInfo = stackedEffects.get(skill.getAbnormalType());

                    // Skills are only replaced if the incoming buff has greater or equal abnormal level.
                    if ((stackedInfo != null)
                            && (skill.getAbnormalLevel()
                                    >= stackedInfo.getSkill().getAbnormalLevel())) {
                        // If it is an herb, set as not in use the lesser buff.
                        // Effect will be present in the effect list.
                        // Effects stats are removed and onActionTime() is not called.
                        // But finish task continues to run, and ticks as well.
                        if (skill.isAbnormalInstant()) {
                            if (stackedInfo.getSkill().isAbnormalInstant()) {
                                stopSkillEffects(SkillFinishType.NORMAL, skill.getAbnormalType());
                                stackedInfo = stackedEffects.get(skill.getAbnormalType());
                            }

                            if (stackedInfo != null) {
                                stackedInfo.setInUse(false);

                                // Remove stats
                                stackedInfo.removeStats();
                                hiddenBuffs.incrementAndGet();
                            }
                        } else // Remove buff that will stack with the abnormal type.
                        {
                            if (stackedInfo.getSkill().isAbnormalInstant()) {
                                stopSkillEffects(SkillFinishType.NORMAL, skill.getAbnormalType());
                            }

                            stopSkillEffects(SkillFinishType.NORMAL, skill.getAbnormalType());
                        }
                    } else // If the new buff is a lesser buff, then don't add it.
                    {
                        return;
                    }
                }

                stackedEffects.put(skill.getAbnormalType(), info);
            }
        }

        // Select the map that holds the effects related to this skill.
        final Queue<BuffInfo> effects = getEffectList(skill);

        // Remove first buff when buff list is full.
        if (!skill.isDebuff() && !skill.isToggle() && !skill.is7Signs() && !doesStack(skill)) {
            int buffsToRemove = -1;
            if (skill.isDance()) {
                buffsToRemove = getDanceCount() - PlayerConfig.DANCES_MAX_AMOUNT;
            } else if (skill.isTriggeredSkill()) {
                buffsToRemove = getTriggeredBuffCount() - PlayerConfig.TRIGGERED_BUFFS_MAX_AMOUNT;
            } else if (!skill.isHealingPotionSkill()) {
                buffsToRemove = getBuffCount() - owner.getStat().getMaxBuffCount();
            }

            for (BuffInfo bi : effects) {
                if (buffsToRemove < 0) {
                    break;
                }

                if (!bi.isInUse()) {
                    continue;
                }

                stopAndRemove(true, bi, effects);
                buffsToRemove--;
            }
        }

        // After removing old buff (same ID) or stacked buff (same abnormal type),
        // Add the buff to the end of the effect list.
        effects.add(info);

        // Initialize effects.
        info.initializeEffects();

        // Update effect flags and icons.
        updateEffectList(true);
    }

    /**
     * Update effect icons.<br>
     * Prevents initialization.
     */
    private void updateEffectIcons() {
        if (owner == null) {
            return;
        }

        updateEffectFlags();

        if (!owner.isPlayable()) {
            return;
        }

        if (updateEffectIconTask == null) {
            updateEffectIconTask = ThreadPool.schedule(
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

                        // Buffs.
                        if (hasBuffs()) {
                            for (BuffInfo info : buffs) {
                                if (info.getSkill().isHealingPotionSkill()) {
                                    shortBuffStatusUpdate(info);
                                } else {
                                    addIcon(info, asu, ps, psSummon, os, isSummon);
                                }
                            }
                        }

                        // Triggered buffs.
                        if (hasTriggered()) {
                            for (BuffInfo info : triggered) {
                                addIcon(info, asu, ps, psSummon, os, isSummon);
                            }
                        }

                        // Songs and dances.
                        if (hasDances()) {
                            for (BuffInfo info : dances) {
                                addIcon(info, asu, ps, psSummon, os, isSummon);
                            }
                        }

                        // Toggles.
                        if (hasToggles()) {
                            for (BuffInfo info : toggles) {
                                addIcon(info, asu, ps, psSummon, os, isSummon);
                            }
                        }

                        // Debuffs.
                        if (hasDebuffs()) {
                            for (BuffInfo info : debuffs) {
                                addIcon(info, asu, ps, psSummon, os, isSummon);
                            }
                        }

                        if (asu != null) {
                            owner.sendPacket(asu);
                        }

                        if (ps != null) {
                            if (owner.isSummon()) {
                                final Player summonOwner = owner.asSummon().getOwner();
                                if (summonOwner != null) {
                                    if (summonOwner.isInParty()) {
                                        summonOwner
                                                .getParty()
                                                .broadcastToPartyMembers(
                                                        summonOwner, psSummon); // send to all member except summonOwner
                                        summonOwner.sendPacket(ps); // now send to summonOwner
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
                    300);
        }
    }

    private void addIcon(
            BuffInfo info,
            AbnormalStatusUpdate asu,
            PartySpelled ps,
            PartySpelled psSummon,
            ExOlympiadSpelledInfo os,
            boolean isSummon) {
        // Avoid null and not in use buffs.
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

    /**
     * Wrapper to update abnormal icons and effect flags.
     * @param update if {@code true} performs an update
     */
    private void updateEffectList(boolean update) {
        if (update) {
            updateEffectIcons();
            computeEffectFlags();
        }
    }

    /**
     * Updates effect flags.<br>
     * TODO: Rework it to update in real time (add/remove/stop/activate/deactivate operations) and avoid iterations.
     */
    private void updateEffectFlags() {
        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (info == null) {
                    continue;
                }

                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    hasBuffsRemovedOnAnyAction = true;
                }

                if (info.getSkill().isRemovedOnDamage()) {
                    hasBuffsRemovedOnDamage = true;
                }
            }
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (info == null) {
                    continue;
                }

                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    hasBuffsRemovedOnAnyAction = true;
                }

                if (info.getSkill().isRemovedOnDamage()) {
                    hasBuffsRemovedOnDamage = true;
                }
            }
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (info == null) {
                    continue;
                }

                if (info.getSkill().isRemovedOnAnyActionExceptMove()) {
                    hasBuffsRemovedOnAnyAction = true;
                }

                if (info.getSkill().isRemovedOnDamage()) {
                    hasBuffsRemovedOnDamage = true;
                }
            }
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if ((info != null) && info.getSkill().isRemovedOnDamage()) {
                    hasDebuffsRemovedOnDamage = true;
                }
            }
        }
    }

    /**
     * Recalculate effect bits flag.<br>
     * TODO: Rework to update in real time and avoid iterations.
     */
    private void computeEffectFlags() {
        int flags = 0;
        if (hasBuffs()) {
            for (BuffInfo info : buffs) {
                if (info != null) {
                    for (AbstractEffect e : info.getEffects()) {
                        flags |= e.getEffectFlags();
                    }
                }
            }
        }

        if (hasTriggered()) {
            for (BuffInfo info : triggered) {
                if (info != null) {
                    for (AbstractEffect e : info.getEffects()) {
                        flags |= e.getEffectFlags();
                    }
                }
            }
        }

        if (hasDebuffs()) {
            for (BuffInfo info : debuffs) {
                if (info != null) {
                    for (AbstractEffect e : info.getEffects()) {
                        flags |= e.getEffectFlags();
                    }
                }
            }
        }

        if (hasDances()) {
            for (BuffInfo info : dances) {
                if (info != null) {
                    for (AbstractEffect e : info.getEffects()) {
                        flags |= e.getEffectFlags();
                    }
                }
            }
        }

        if (hasToggles()) {
            for (BuffInfo info : toggles) {
                if (info != null) {
                    for (AbstractEffect e : info.getEffects()) {
                        flags |= e.getEffectFlags();
                    }
                }
            }
        }

        effectFlags = flags;
    }

    /**
     * Check if target is affected with special buff
     * @param flag of special buff
     * @return boolean true if affected
     */
    public boolean isAffected(EffectFlag flag) {
        return (effectFlags & flag.getMask()) != 0;
    }

    /**
     * Check if target is affected by AbnormalType.
     * @param type the AbnormalType
     * @return boolean true if affected
     */
    public boolean isAffectedByAbnormalType(AbnormalType type) {
        return stackedEffects.containsKey(type);
    }
}
