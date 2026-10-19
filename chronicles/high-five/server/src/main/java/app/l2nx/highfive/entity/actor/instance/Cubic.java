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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.CubicData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.tasks.cubics.CubicAction;
import app.l2nx.highfive.entity.actor.tasks.cubics.CubicDisappear;
import app.l2nx.highfive.entity.actor.tasks.cubics.CubicHeal;
import app.l2nx.highfive.entity.actor.templates.CubicTemplate;
import app.l2nx.highfive.entity.cubic.CubicSelectionMode;
import app.l2nx.highfive.entity.cubic.CubicSkill;
import app.l2nx.highfive.entity.cubic.CubicTargetType;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.DuelManager;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid, Mobius, BazookaRpm
 */
@Slf4j
public class Cubic {

    // Type of Cubics
    public static final int STORM_CUBIC = 1;
    public static final int VAMPIRIC_CUBIC = 2;
    public static final int LIFE_CUBIC = 3;
    public static final int VIPER_CUBIC = 4;
    public static final int POLTERGEIST_CUBIC = 5;
    public static final int BINDING_CUBIC = 6;
    public static final int AQUA_CUBIC = 7;
    public static final int SPARK_CUBIC = 8;
    public static final int ATTRACT_CUBIC = 9;

    // Max range of cubic skills.
    // TODO: Check/fix the max range
    public static final int MAX_MAGIC_RANGE = 900;

    // Cubic skills
    public static final int SKILL_CUBIC_HEAL = 4051;

    @Getter
    private final Player owner;

    @Getter
    @Setter
    private Creature target;

    @Getter
    private final int id;

    @Getter
    private final CubicTemplate template;

    @Getter
    private final int cubicPower;

    private final int cubicDelay;
    private final int cubicSkillChance;

    @Getter
    private final int cubicMaxCount;

    private final boolean givenByOther;

    @Getter
    private final List<CubicSkill> cubicSkills;

    private Future<?> disappearTask;
    private Future<?> actionTask;

    /**
     * Creates a cubic instance for the specified owner and template level.<br>
     * Schedules its disappearance and starts heal cubics immediately when required.
     * @param owner
     * @param cubicId
     * @param level
     * @param cubicPower
     * @param cubicDelay
     * @param cubicSkillChance
     * @param cubicMaxCount
     * @param cubicDuration
     * @param givenByOther
     */
    public Cubic(
            Player owner,
            int cubicId,
            int level,
            int cubicPower,
            int cubicDelay,
            int cubicSkillChance,
            int cubicMaxCount,
            int cubicDuration,
            boolean givenByOther) {
        this.owner = owner;
        id = cubicId;
        template = CubicData.getInstance().getCubicTemplate(cubicId, level);
        if (template == null) {
            this.cubicPower = cubicPower;
            this.cubicDelay = cubicDelay * 1000;
            this.cubicSkillChance = cubicSkillChance;
            this.cubicMaxCount = cubicMaxCount;
            cubicSkills = java.util.Collections.emptyList();
            log.warn("Missing cubic template. cubicId: {} level: {} ownerId: {}", cubicId, level, owner.getObjectId());
        } else {
            this.cubicPower = cubicPower > 0 ? cubicPower : template.getBaseMAtk();
            this.cubicDelay = (cubicDelay > 0 ? cubicDelay : template.getDelay()) * 1000;
            this.cubicSkillChance = cubicSkillChance;
            this.cubicMaxCount = cubicMaxCount > -1 ? cubicMaxCount : template.getMaxCount();
            cubicSkills = template.getCubicSkills();
        }
        this.givenByOther = givenByOther;

        final int duration =
                cubicDuration > 0 ? cubicDuration : template != null ? template.getDuration() : cubicDuration;
        disappearTask = ThreadPool.schedule(new CubicDisappear(this), duration * 1000);
        if ((template != null) && (template.getTargetType() == CubicTargetType.HEAL)) {
            doAction();
        }
    }

    /**
     * Starts the cubic action task when the template and skill list are valid.
     */
    public void doAction() {
        if ((actionTask == null) && (template != null) && !cubicSkills.isEmpty()) {
            synchronized (this) {
                if (actionTask == null) {
                    if (template.getTargetType() == CubicTargetType.HEAL) {
                        actionTask = ThreadPool.scheduleAtFixedRate(new CubicHeal(this), 0, cubicDelay);
                    } else {
                        actionTask =
                                ThreadPool.scheduleAtFixedRate(new CubicAction(this, cubicSkillChance), 0, cubicDelay);
                    }
                }
            }
        }
    }

    /**
     * Selects the next non-priority cubic skill according to the template selection mode.
     * @return The selected cubic skill or {@code null}.
     */
    public CubicSkill getCubicSkill() {
        if (getNormalCubicSkillCount() == 0) {
            return null;
        }

        final CubicSelectionMode selectionMode =
                template != null ? template.getSelectionMode() : CubicSelectionMode.RANDOM;
        switch (selectionMode) {
            case ORDERED: {
                return getOrderedCubicSkill();
            }
            case WEIGHTED: {
                return getWeightedCubicSkill();
            }
            case RANDOM:
            default: {
                return getRandomCubicSkill();
            }
        }
    }

    private int getNormalCubicSkillCount() {
        int count = 0;
        for (CubicSkill cubicSkill : cubicSkills) {
            if (!cubicSkill.isPriority()) {
                count++;
            }
        }

        return count;
    }

    private CubicSkill getRandomCubicSkill() {
        final int normalSkillCount = getNormalCubicSkillCount();
        int index = normalSkillCount > 0 ? ThreadLocalRandom.current().nextInt(normalSkillCount) : 0;
        for (CubicSkill cubicSkill : cubicSkills) {
            if (cubicSkill.isPriority()) {
                continue;
            }

            if (index-- == 0) {
                return cubicSkill;
            }
        }

        return null;
    }

    private CubicSkill getOrderedCubicSkill() {
        for (CubicSkill cubicSkill : cubicSkills) {
            if (cubicSkill.isPriority()) {
                continue;
            }

            if ((cubicSkill.getTriggerRate() >= 100)
                    || (ThreadLocalRandom.current().nextInt(100) < cubicSkill.getTriggerRate())) {
                return cubicSkill;
            }
        }

        return null;
    }

    private CubicSkill getWeightedCubicSkill() {
        int totalRate = 0;
        for (CubicSkill cubicSkill : cubicSkills) {
            if (!cubicSkill.isPriority() && (cubicSkill.getTriggerRate() > 0)) {
                totalRate += cubicSkill.getTriggerRate();
            }
        }

        if (totalRate <= 0) {
            return getRandomCubicSkill();
        }

        int chance = ThreadLocalRandom.current().nextInt(totalRate);
        for (CubicSkill cubicSkill : cubicSkills) {
            if (cubicSkill.isPriority() || (cubicSkill.getTriggerRate() <= 0)) {
                continue;
            }

            chance -= cubicSkill.getTriggerRate();
            if (chance < 0) {
                return cubicSkill;
            }
        }

        return getRandomCubicSkill();
    }

    /**
     * Stops the scheduled cubic action task and clears the current target.
     */
    public void stopAction() {
        target = null;
        if (actionTask != null) {
            actionTask.cancel(true);
            actionTask = null;
        }
    }

    /**
     * Cancels the scheduled cubic disappearance task.
     */
    public void cancelDisappear() {
        if (disappearTask != null) {
            disappearTask.cancel(true);
            disappearTask = null;
        }
    }

    /**
     * Selects a valid offensive target for this cubic.<br>
     * Handles event, duel, Olympiad, PvE aggro and PvP target rules.
     */
    public void getCubicTarget() {
        try {
            this.target = null;
            final WorldObject ownerTarget = owner.getTarget();
            if (ownerTarget == null) {
                return;
            }

            // Custom event targeting.
            if (owner.isOnEvent()) {
                final Player target = ownerTarget.asPlayer();
                if ((target != null)
                        && ((owner.getTeam() != target.getTeam()) || owner.isOnSoloEvent())
                        && !(target.isDead())) {
                    this.target = ownerTarget.asCreature();
                }
                return;
            }

            // Duel targeting.
            if (owner.isInDuel()) {
                final Player playerA =
                        DuelManager.getInstance().getDuel(owner.getDuelId()).getPlayerA();
                final Player playerB =
                        DuelManager.getInstance().getDuel(owner.getDuelId()).getPlayerB();
                if (DuelManager.getInstance().getDuel(owner.getDuelId()).isPartyDuel()) {
                    final Party partyA = playerA.getParty();
                    final Party partyB = playerB.getParty();
                    Party partyEnemy = null;
                    if (partyA != null) {
                        if (partyA.getMembers().contains(owner)) {
                            if (partyB != null) {
                                partyEnemy = partyB;
                            } else {
                                this.target = playerB;
                            }
                        } else {
                            partyEnemy = partyA;
                        }
                    } else {
                        if (playerA == owner) {
                            if (partyB != null) {
                                partyEnemy = partyB;
                            } else {
                                this.target = playerB;
                            }
                        } else {
                            this.target = playerA;
                        }
                    }

                    if (((this.target == playerA) || (this.target == playerB)) && (this.target == ownerTarget)) {
                        return;
                    }

                    if (partyEnemy != null) {
                        if (partyEnemy.getMembers().contains(ownerTarget)) {
                            this.target = ownerTarget.asCreature();
                        }
                        return;
                    }
                }

                if ((playerA != owner) && (ownerTarget == playerA)) {
                    this.target = playerA;
                    return;
                }

                if ((playerB != owner) && (ownerTarget == playerB)) {
                    this.target = playerB;
                    return;
                }

                this.target = null;
                return;
            }

            // Olympiad targeting.
            if (owner.isInOlympiadMode()) {
                if (owner.isOlympiadStart() && ownerTarget.isPlayable()) {
                    final Player targetPlayer = ownerTarget.asPlayer();
                    if ((targetPlayer != null)
                            && (targetPlayer.getOlympiadGameId() == owner.getOlympiadGameId())
                            && (targetPlayer.getOlympiadSide() != owner.getOlympiadSide())) {
                        this.target = ownerTarget.asCreature();
                    }
                }
                return;
            }

            // Test the owner's target and use it when valid.
            if (ownerTarget.isCreature() && (ownerTarget != owner.getSummon()) && (ownerTarget != owner)) {
                // Target mobs that have aggro on the owner or the owner's summon.
                if (ownerTarget.isAttackable()) {
                    final Attackable attackable = ownerTarget.asAttackable();
                    if (attackable.isInAggroList(owner) && !attackable.isDead()) {
                        this.target = ownerTarget.asCreature();
                        return;
                    }

                    if (owner.hasSummon() && attackable.isInAggroList(owner.getSummon()) && !attackable.isDead()) {
                        this.target = ownerTarget.asCreature();
                        return;
                    }
                }

                // Get target in PvP or siege.
                Player enemy = null;
                if (((owner.getPvpFlag() > 0) && !owner.isInsideZone(ZoneId.PEACE)) || owner.isInsideZone(ZoneId.PVP)) {
                    if (!ownerTarget.asCreature().isDead()) {
                        enemy = ownerTarget.asPlayer();
                    }

                    if (enemy != null) {
                        boolean targetIt = true;
                        if (owner.getParty() != null) {
                            if (owner.getParty().getMembers().contains(enemy)) {
                                targetIt = false;
                            } else if ((owner.getParty().getCommandChannel() != null)
                                    && owner.getParty()
                                            .getCommandChannel()
                                            .getMembers()
                                            .contains(enemy)) {
                                targetIt = false;
                            }
                        }

                        if ((owner.getClan() != null) && !owner.isInsideZone(ZoneId.PVP)) {
                            if (owner.getClan().isMember(enemy.getObjectId())) {
                                targetIt = false;
                            }

                            if ((owner.getAllyId() > 0)
                                    && (enemy.getAllyId() > 0)
                                    && (owner.getAllyId() == enemy.getAllyId())) {
                                targetIt = false;
                            }
                        }

                        if ((enemy.getPvpFlag() == 0) && !enemy.isInsideZone(ZoneId.PVP)) {
                            targetIt = false;
                        }

                        if (enemy.isInsideZone(ZoneId.PEACE)) {
                            targetIt = false;
                        }

                        if ((owner.getSiegeState() > 0) && (owner.getSiegeState() == enemy.getSiegeState())) {
                            targetIt = false;
                        }

                        if (!enemy.isSpawned()) {
                            targetIt = false;
                        }

                        if (targetIt) {
                            this.target = enemy;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Cubic target selection failed. cubicId: {} ownerId: {}", id, owner.getObjectId(), e);
        }
    }

    /**
     * Applies cubic effects to a validated target.
     * @param skill
     * @param target
     */
    private void applyCubicEffects(Skill skill, Creature target) {
        if ((target == null)
                || ((target != owner)
                        && skill.hasNegativeEffect()
                        && (target.isInvul()
                                || (owner.isGM() && !owner.getAccessLevel().canGiveDamage())))) {
            return;
        }

        if (target.isInvulAgainst(skill.getId(), skill.getLevel())) {
            return;
        }

        final boolean addContinuousEffects = skill.isToggle() || skill.isContinuous();
        final BuffInfo info = new BuffInfo(owner, target, skill);
        skill.applyEffectScope(EffectScope.GENERAL, info, true, addContinuousEffects);
        final EffectScope pvpOrPveEffectScope = owner.isPlayable() && target.isAttackable()
                ? EffectScope.PVE
                : owner.isPlayable() && target.isPlayable() ? EffectScope.PVP : null;
        skill.applyEffectScope(pvpOrPveEffectScope, info, true, addContinuousEffects);
        skill.applyEffectScope(EffectScope.CHANNELING, info, true, addContinuousEffects);
        if (addContinuousEffects) {
            target.getEffectList().add(info);
        }
    }

    /**
     * Applies continuous cubic effects to all valid targets.
     * @param skill
     * @param targets
     */
    public void useCubicContinuous(Skill skill, List<Creature> targets) {
        for (Creature target : targets) {
            if ((target == null) || target.isDead()) {
                continue;
            }

            if (skill.hasNegativeEffect()) {
                final boolean acted =
                        Formulas.calcCubicSkillSuccess(this, target, skill, Formulas.SHIELD_DEFENSE_FAILED);
                if (!acted) {
                    owner.sendPacket(SystemMessageId.YOUR_ATTACK_HAS_FAILED);
                    continue;
                }
            }

            // Apply effects.
            applyCubicEffects(skill, target);

            // If this is a negative effect skill notify the duel manager, so it can be removed after the duel (player &
            // target must be in the same duel).
            if (target.isPlayer()) {
                final Player player = target.asPlayer();
                if (player.isInDuel() && skill.hasNegativeEffect() && (owner.getDuelId() == player.getDuelId())) {
                    DuelManager.getInstance().onBuff(player, skill);
                }
            }
        }
    }

    /**
     * Applies cubic magic damage to all valid targets.
     * @param skill
     * @param targets
     */
    public void useCubicMdam(Skill skill, List<Creature> targets) {
        for (Creature target : targets) {
            if (target == null) {
                continue;
            }

            if (target.isAlikeDead()) {
                if (target.isPlayer() && PlayerConfig.FAKE_DEATH_DAMAGE_STAND) {
                    target.stopFakeDeath(true);
                } else {
                    continue;
                }
            }

            final boolean mcrit = Formulas.calcMCrit(owner.getMCriticalHit(target, skill));
            final byte shld = Formulas.calcShldUse(owner, target, skill);
            int damage = (int) Formulas.calcMagicDam(this, target, skill, mcrit, shld);
            if (damage > 0) {
                // Manage attack or cast break of the target.
                if (!target.isRaid() && Formulas.calcAtkBreak(target, damage)) {
                    target.breakAttack();
                    target.breakCast();
                }

                // Shield Deflect Magic: If target is reflecting the skill then no damage is done.
                if (target.getStat().calcStat(Stat.VENGEANCE_SKILL_MAGIC_DAMAGE, 0, target, skill)
                        > ThreadLocalRandom.current().nextInt(100)) {
                    damage = 0;
                } else {
                    owner.sendDamageMessage(target, damage, mcrit, false, false);
                    target.reduceCurrentHp(damage, owner, skill);
                }
            }
        }
    }

    /**
     * Applies cubic drain damage and restores part of the owner's HP.
     * @param skill
     * @param targets
     */
    public void useCubicDrain(Skill skill, List<Creature> targets) {
        for (Creature target : targets) {
            if (target.isAlikeDead()) {
                continue;
            }

            final boolean mcrit = Formulas.calcMCrit(this.owner.getMCriticalHit(target, skill));
            final byte shld = Formulas.calcShldUse(this.owner, target, skill);
            final int damage = (int) Formulas.calcMagicDam(this, target, skill, mcrit, shld);

            // TODO: Unhardcode fixed value.
            final double hpAdd = (0.4 * damage);
            final Player owner = this.owner;
            final double hp = ((owner.getCurrentHp() + hpAdd) > owner.getMaxHp()
                    ? owner.getMaxHp()
                    : (owner.getCurrentHp() + hpAdd));
            owner.setCurrentHp(hp);

            // Check if the target should be damaged.
            if ((damage > 0) && !target.isDead()) {
                target.reduceCurrentHp(damage, this.owner, skill);

                // Manage attack or cast break of the target.
                if (!target.isRaid() && Formulas.calcAtkBreak(target, damage)) {
                    target.breakAttack();
                    target.breakCast();
                }

                owner.sendDamageMessage(target, damage, mcrit, false, false);
            }
        }
    }

    /**
     * Applies cubic disable or aggression effects to all valid targets.
     * @param skill
     * @param targets
     */
    public void useCubicDisabler(Skill skill, List<Creature> targets) {
        for (Creature target : targets) {
            if ((target == null) || target.isDead()) {
                continue;
            }

            if (skill.hasEffectType(EffectType.STUN, EffectType.PARALYZE, EffectType.ROOT)
                    && Formulas.calcCubicSkillSuccess(this, target, skill, Formulas.SHIELD_DEFENSE_FAILED)) {
                // Apply effects.
                applyCubicEffects(skill, target);

                // If this is a negative effect skill notify the duel manager, so it can be removed after the duel
                // (player & target must be in the same duel).
                if (target.isPlayer()) {
                    final Player player = target.asPlayer();
                    if (player.isInDuel() && skill.hasNegativeEffect() && (owner.getDuelId() == player.getDuelId())) {
                        DuelManager.getInstance().onBuff(player, skill);
                    }
                }
            }

            if (skill.hasEffectType(EffectType.AGGRESSION)
                    && Formulas.calcCubicSkillSuccess(this, target, skill, Formulas.SHIELD_DEFENSE_FAILED)) {
                if (target.isAttackable()) {
                    target.getAI()
                            .notifyActionAggression(owner, (int) ((150 * skill.getPower()) / (target.getLevel() + 7)));
                }

                // Apply effects.
                applyCubicEffects(skill, target);
            }
        }
    }

    /**
     * Checks if the target is inside the owner's maximum cubic range.
     * @param owner
     * @param target
     * @return {@code true} if the target is inside range.
     */
    public static boolean isInCubicRange(Creature owner, Creature target) {
        if ((owner == null) || (target == null)) {
            return false;
        }

        // Temporary range check until real behavior of cubics is known/coded.
        final long range = MAX_MAGIC_RANGE;
        final long x = owner.getX() - target.getX();
        final long y = owner.getY() - target.getY();
        final long z = owner.getZ() - target.getZ();
        return (((x * x) + (y * y) + (z * z)) <= (range * range));
    }

    /**
     * Selects the friendly target with the lowest HP percentage inside cubic range.
     */
    public void cubicTargetForHeal() {
        Creature target = null;
        double percentleft = 100.0;
        Party party = owner.getParty();

        // Treat non-party duels as solo context for heal target selection.
        if (owner.isInDuel()
                && !DuelManager.getInstance().getDuel(owner.getDuelId()).isPartyDuel()) {
            party = null;
        }

        if ((party != null) && !owner.isInOlympiadMode()) {
            // Check party members and their summons inside cubic range.
            for (Creature partyMember : party.getMembers()) {
                // Select the party member with the lowest HP percentage inside cubic range.
                if (!partyMember.isDead()
                        && isInCubicRange(owner, partyMember)
                        && (partyMember.getCurrentHp() < partyMember.getMaxHp())
                        && (percentleft > (partyMember.getCurrentHp() / partyMember.getMaxHp()))) {
                    percentleft = (partyMember.getCurrentHp() / partyMember.getMaxHp());
                    target = partyMember;
                }

                final Player player = partyMember.asPlayer();
                if (player != null) {
                    final Summon summon = player.getSummon();
                    if (summon != null) {
                        if (summon.isDead()) {
                            continue;
                        }

                        // Check if the party member's summon is inside cubic range.
                        if (!isInCubicRange(owner, summon)) {
                            continue;
                        }

                        // Select the summon when it has the lowest HP percentage.
                        if ((summon.getCurrentHp() < summon.getMaxHp())
                                && (percentleft > (summon.getCurrentHp() / summon.getMaxHp()))) {
                            percentleft = (summon.getCurrentHp() / summon.getMaxHp());
                            target = summon;
                        }
                    }
                }
            }
        } else {
            if (owner.getCurrentHp() < owner.getMaxHp()) {
                percentleft = (owner.getCurrentHp() / owner.getMaxHp());
                target = owner;
            }

            if (owner.hasSummon()
                    && !owner.getSummon().isDead()
                    && (owner.getSummon().getCurrentHp() < owner.getSummon().getMaxHp())
                    && (percentleft
                            > (owner.getSummon().getCurrentHp()
                                    / owner.getSummon().getMaxHp()))
                    && isInCubicRange(owner, owner.getSummon())) {
                target = owner.getSummon();
            }
        }

        this.target = target;
    }

    /**
     * Checks if this cubic was given by another player.
     * @return {@code true} if the cubic was given by another player.
     */
    public boolean givenByOther() {
        return givenByOther;
    }
}
