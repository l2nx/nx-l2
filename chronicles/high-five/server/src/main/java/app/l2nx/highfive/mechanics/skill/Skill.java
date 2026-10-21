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
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.handler.ITargetTypeHandler;
import app.l2nx.highfive.handler.TargetHandler;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.enums.FlyType;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.mechanics.stats.BaseStat;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.TraitType;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.util.LocationUtil;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Skill {
    @Getter
    private final int id;

    @Getter
    private final int level;

    @Getter
    private final int displayId;

    @Getter
    private final int displayLevel;

    @Getter
    private final String name;

    private final SkillOperateType operateType;

    private final int magic;

    @Getter
    private final TraitType traitType;

    @Getter
    private final boolean staticReuse;

    @Getter
    private final int mpConsume;

    @Getter
    private final int mpInitialConsume;

    @Getter
    private final int mpPerChanneling;

    @Getter
    private final int hpConsume;

    @Getter
    private final int itemConsumeCount;

    @Getter
    private final int itemConsumeId;

    @Getter
    private final int castRange;

    @Getter
    private final int effectRange;

    @Getter
    private final boolean abnormalInstant;

    @Getter
    private final int abnormalLevel;

    @Getter
    private final AbnormalType abnormalType;

    @Getter
    private final int abnormalTime;

    private AbnormalVisualEffect[] abnormalVisualEffects = null;
    private AbnormalVisualEffect[] abnormalVisualEffectsSpecial = null;
    private AbnormalVisualEffect[] abnormalVisualEffectsEvent = null;

    @Getter
    private final boolean stayAfterDeath;

    @Getter
    private final boolean stayOnSubclassChange;

    @Getter
    private final boolean recoveryHerb;

    @Getter
    @Setter
    private int referenceItemId;

    // All times in milliseconds.
    @Getter
    private final int hitTime;

    @Getter
    private final int coolTime;

    @Getter
    private final int reuseHashCode;

    @Getter
    private final int reuseDelay;

    @Getter
    private final TargetType targetType;

    @Getter
    private final int feed;

    @Getter
    private final double power;

    private final double pvpPower;
    private final double pvePower;

    @Getter
    private final int magicLevel;

    @Getter
    private final int lvlBonusRate;

    @Getter
    private final int activateRate;

    @Getter
    private final int minChance;

    @Getter
    private final int maxChance;

    @Getter
    private final int blowChance;

    // Effect area radius, centered on the caster for AURA/PARTY/CLAN and on the target for AREA.
    @Getter
    private final int affectRange;

    private final int[] affectLimit = new int[2];

    private final boolean nextActionIsAttack;

    @Getter
    private final boolean removedOnAnyActionExceptMove;

    @Getter
    private final boolean removedOnDamage;

    @Getter
    private final boolean blockedInOlympiad;

    @Getter
    private final byte element;

    @Getter
    private final int elementPower;

    @Getter
    private final BaseStat basicProperty;

    @Getter
    private final boolean overhit;

    @Getter
    private final int minPledgeClass;

    @Getter
    private final int chargeConsumeCount;

    @Getter
    private final int maxSoulConsumeCount;

    @Getter
    private final boolean heroSkill;

    private final boolean gmSkill;
    private final boolean sevenSigns;

    @Getter
    private final int baseCritRate; // Percent; ignores rCrit and buffs.

    private final boolean dmgDirectlyToHP;

    @Getter
    private final boolean triggeredSkill;

    @Getter
    private final int effectPoint;

    private List<Condition> preCondition;
    private List<Condition> itemPreCondition;

    private List<FuncTemplate> funcTemplates;

    private final Map<EffectScope, List<AbstractEffect>> effectLists = new EnumMap<>(EffectScope.class);

    @Getter
    private final FlyType flyType;

    @Getter
    private final int flyRadius;

    @Getter
    private final float flyCourse;

    @Getter
    private final boolean debuff;

    @Getter
    private final String attributeName;

    private final boolean ignoreShield;

    @Getter
    private final boolean suicideAttack;

    private final boolean canBeDispeled;

    @Getter
    private final boolean clanSkill;

    @Getter
    private final boolean excludedFromCheck;

    @Getter
    private final boolean simultaneousCast;

    @Getter
    private final String icon;

    private volatile Byte[] effectTypes;

    @Getter
    private final int channelingSkillId;

    @Getter
    private final int channelingTickInitialDelay;

    @Getter
    private final int channelingTickInterval;

    @Getter
    private final boolean pvPOnly;

    public Skill(StatSet set) {
        id = set.getInt("skill_id");
        level = set.getInt("level");
        displayId = set.getInt("displayId", id);
        displayLevel = set.getInt("displayLevel", level);
        name = set.getString("name", "");
        operateType = set.getEnum("operateType", SkillOperateType.class);
        magic = set.getInt("isMagic", 0);
        traitType = set.getEnum("trait", TraitType.class, TraitType.NONE);
        staticReuse = set.getBoolean("staticReuse", false);
        mpConsume = set.getInt("mpConsume", 0);
        mpInitialConsume = set.getInt("mpInitialConsume", 0);
        mpPerChanneling = set.getInt("mpPerChanneling", mpConsume);
        hpConsume = set.getInt("hpConsume", 0);
        itemConsumeCount = set.getInt("itemConsumeCount", 0);
        itemConsumeId = set.getInt("itemConsumeId", 0);
        castRange = set.getInt("castRange", -1);
        effectRange = set.getInt("effectRange", -1);
        abnormalLevel = set.getInt("abnormalLevel", 0);
        abnormalType = set.getEnum("abnormalType", AbnormalType.class, AbnormalType.NONE);
        int abnormalTime = set.getInt("abnormalTime", 0);
        if (PlayerConfig.ENABLE_MODIFY_SKILL_DURATION
                && PlayerConfig.SKILL_DURATION_LIST.containsKey(id)
                && (operateType != SkillOperateType.T)) {
            if ((level < 100) || (level > 140)) {
                abnormalTime = PlayerConfig.SKILL_DURATION_LIST.get(id);
            } else if ((level >= 100) && (level < 140)) {
                abnormalTime += PlayerConfig.SKILL_DURATION_LIST.get(id);
            }
        }

        this.abnormalTime = abnormalTime;
        abnormalInstant = set.getBoolean("abnormalInstant", false);
        parseAbnormalVisualEffect(set.getString("abnormalVisualEffect", null));
        attributeName = set.getString("attribute", "");
        stayAfterDeath = set.getBoolean("stayAfterDeath", false);
        stayOnSubclassChange = set.getBoolean("stayOnSubclassChange", true);
        hitTime = set.getInt("hitTime", 0);
        coolTime = set.getInt("coolTime", 0);
        debuff = set.getBoolean("isDebuff", false);
        recoveryHerb = set.getBoolean("isRecoveryHerb", false);
        feed = set.getInt("feed", 0);
        reuseHashCode = SkillData.getSkillHashCode(id, level);
        if (PlayerConfig.ENABLE_MODIFY_SKILL_REUSE && PlayerConfig.SKILL_REUSE_LIST.containsKey(id)) {
            reuseDelay = PlayerConfig.SKILL_REUSE_LIST.get(id);
        } else {
            reuseDelay = set.getInt("reuseDelay", 0);
        }

        affectRange = set.getInt("affectRange", 0);

        final String affectLimit = set.getString("affectLimit", null);
        if (affectLimit != null) {
            try {
                final String[] valuesSplit = affectLimit.split("-");
                this.affectLimit[0] = Integer.parseInt(valuesSplit[0]);
                this.affectLimit[1] = Integer.parseInt(valuesSplit[1]);
            } catch (Exception e) {
                throw new IllegalArgumentException("SkillId: " + id + " invalid affectLimit value: " + affectLimit
                        + ", \"percent-percent\" required");
            }
        }

        targetType = set.getEnum("targetType", TargetType.class, TargetType.SELF);
        power = set.getFloat("power", 0.f);
        pvpPower = set.getFloat("pvpPower", (float) power);
        pvePower = set.getFloat("pvePower", (float) power);
        magicLevel = set.getInt("magicLevel", 0);
        lvlBonusRate = set.getInt("lvlBonusRate", 0);
        activateRate = set.getInt("activateRate", -1);
        minChance = set.getInt("minChance", PlayerConfig.MIN_ABNORMAL_STATE_SUCCESS_RATE);
        maxChance = set.getInt("maxChance", PlayerConfig.MAX_ABNORMAL_STATE_SUCCESS_RATE);
        ignoreShield = set.getBoolean("ignoreShld", false);
        nextActionIsAttack = set.getBoolean("nextActionAttack", false);
        removedOnAnyActionExceptMove = set.getBoolean("removedOnAnyActionExceptMove", false);
        removedOnDamage = set.getBoolean("removedOnDamage", false);
        blockedInOlympiad = set.getBoolean("blockedInOlympiad", false);
        element = set.getByte("element", (byte) -1);
        elementPower = set.getInt("elementPower", 0);
        basicProperty = set.getEnum("basicProperty", BaseStat.class, BaseStat.NONE);
        overhit = set.getBoolean("overHit", false);
        suicideAttack = set.getBoolean("isSuicideAttack", false);
        minPledgeClass = set.getInt("minPledgeClass", 0);
        chargeConsumeCount = set.getInt("chargeConsume", 0);
        maxSoulConsumeCount = set.getInt("soulMaxConsumeCount", 0);
        blowChance = set.getInt("blowChance", 0);
        heroSkill = SkillTreeData.getInstance().isHeroSkill(id, level);
        gmSkill = SkillTreeData.getInstance().isGMSkill(id, level);
        sevenSigns = (id > 4360) && (id < 4367);
        clanSkill = SkillTreeData.getInstance().isClanSkill(id, level);
        baseCritRate = set.getInt("baseCritRate", 0);
        dmgDirectlyToHP = set.getBoolean("dmgDirectlyToHp", false);
        triggeredSkill = set.getBoolean("isTriggeredSkill", false);
        effectPoint = set.getInt("effectPoint", 0);
        flyType = set.getEnum("flyType", FlyType.class, null);
        flyRadius = set.getInt("flyRadius", 0);
        flyCourse = set.getFloat("flyCourse", 0);
        canBeDispeled = set.getBoolean("canBeDispeled", true);
        excludedFromCheck = set.getBoolean("excludedFromCheck", false);
        simultaneousCast = set.getBoolean("simultaneousCast", false);
        icon = set.getString("icon", "icon.skill0000");
        channelingSkillId = set.getInt("channelingSkillId", 0);
        channelingTickInterval = set.getInt("channelingTickInterval", 2) * 1000;
        channelingTickInitialDelay = set.getInt("channelingTickInitialDelay", channelingTickInterval / 1000) * 1000;
        pvPOnly = set.getBoolean("isPvPOnly", false);
    }

    public boolean isAOE() {
        switch (targetType) {
            case AREA:
            case AURA:
            case BEHIND_AREA:
            case BEHIND_AURA:
            case FRONT_AREA:
            case FRONT_AURA: {
                return true;
            }
        }

        return false;
    }

    public boolean isDamage() {
        return hasEffectType(
                EffectType.MAGICAL_ATTACK,
                EffectType.HP_DRAIN,
                EffectType.PHYSICAL_ATTACK,
                EffectType.PHYSICAL_ATTACK_HP_LINK);
    }

    public double getPower(Creature creature, Creature target, boolean isPvP, boolean isPvE) {
        if (creature == null) {
            return getPower(isPvP, isPvE);
        }

        if (hasEffectType(EffectType.DEATH_LINK)) {
            return getPower(isPvP, isPvE) * (-((creature.getCurrentHp() * 2) / creature.getMaxHp()) + 2);
        }

        if (hasEffectType(EffectType.PHYSICAL_ATTACK_HP_LINK)) {
            return getPower(isPvP, isPvE) * (-((target.getCurrentHp() * 2) / target.getMaxHp()) + 2);
        }

        return getPower(isPvP, isPvE);
    }

    public double getPower(boolean isPvP, boolean isPvE) {
        return isPvE ? pvePower : isPvP ? pvpPower : power;
    }

    public AbnormalVisualEffect[] getAbnormalVisualEffects() {
        return abnormalVisualEffects;
    }

    public boolean hasAbnormalVisualEffects() {
        return (abnormalVisualEffects != null) && (abnormalVisualEffects.length > 0);
    }

    public AbnormalVisualEffect[] getAbnormalVisualEffectsSpecial() {
        return abnormalVisualEffectsSpecial;
    }

    public boolean hasAbnormalVisualEffectsSpecial() {
        return (abnormalVisualEffectsSpecial != null) && (abnormalVisualEffectsSpecial.length > 0);
    }

    public AbnormalVisualEffect[] getAbnormalVisualEffectsEvent() {
        return abnormalVisualEffectsEvent;
    }

    public boolean hasAbnormalVisualEffectsEvent() {
        return (abnormalVisualEffectsEvent != null) && (abnormalVisualEffectsEvent.length > 0);
    }

    public boolean nextActionIsAttack() {
        return nextActionIsAttack;
    }

    public boolean isPhysical() {
        return magic == 0;
    }

    public boolean isMagic() {
        return magic == 1;
    }

    public boolean isStatic() {
        return magic == 2;
    }

    public boolean isDance() {
        return magic == 3;
    }

    public int getAffectLimit() {
        return affectLimit[0] + (affectLimit[1] > 0 ? ClockBridge.random().nextInt(affectLimit[1]) : 0);
    }

    public boolean isActive() {
        return (operateType != null) && operateType.isActive();
    }

    public boolean isPassive() {
        return (operateType != null) && operateType.isPassive();
    }

    public boolean isToggle() {
        return (operateType != null) && operateType.isToggle();
    }

    public boolean isContinuous() {
        return ((operateType != null) && operateType.isContinuous()) || isSelfContinuous();
    }

    public boolean isSelfContinuous() {
        return (operateType != null) && operateType.isSelfContinuous();
    }

    public boolean isChanneling() {
        return (operateType != null) && operateType.isChanneling();
    }

    public boolean isTransformation() {
        return abnormalType == AbnormalType.TRANSFORM;
    }

    public boolean useSoulShot() {
        return hasEffectType(EffectType.PHYSICAL_ATTACK, EffectType.PHYSICAL_ATTACK_HP_LINK);
    }

    public boolean useSpiritShot() {
        return magic == 1;
    }

    public boolean useFishShot() {
        return hasEffectType(EffectType.FISHING);
    }

    public boolean isGMSkill() {
        return gmSkill;
    }

    public boolean is7Signs() {
        return sevenSigns;
    }

    public boolean isHealingPotionSkill() {
        return abnormalType == AbnormalType.HP_RECOVER;
    }

    public boolean getDmgDirectlyToHP() {
        return dmgDirectlyToHP;
    }

    public boolean hasNegativeEffect() {
        return (effectPoint < 0) && (targetType != TargetType.SELF);
    }

    public boolean checkCondition(Creature creature, WorldObject object, boolean itemOrWeapon) {
        if (creature.isFakePlayer() || (creature.isGM() && !GeneralConfig.GM_SKILL_RESTRICTION)) {
            return true;
        }

        if (creature.isPlayer()
                && creature.asPlayer().isMounted()
                && hasNegativeEffect()
                && !MountEnabledSkillList.contains(id)) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
            sm.addSkillName(id);
            creature.sendPacket(sm);
            return false;
        }

        final List<Condition> preCondition = itemOrWeapon ? itemPreCondition : this.preCondition;
        if ((preCondition == null) || preCondition.isEmpty()) {
            return true;
        }

        final Creature target = object instanceof Creature ? object.asCreature() : null;
        for (Condition cond : preCondition) {
            if (!cond.test(creature, target, this)) {
                final String msg = cond.getMessage();
                final int msgId = cond.getMessageId();
                if (msgId != 0) {
                    final SystemMessage sm = new SystemMessage(msgId);
                    if (cond.isAddName()) {
                        sm.addSkillName(id);
                    }

                    creature.sendPacket(sm);
                } else if (msg != null) {
                    creature.sendMessage(msg);
                }

                return false;
            }
        }

        return true;
    }

    public List<WorldObject> getTargetList(Creature creature, boolean onlyFirst) {
        Creature target = null;

        final WorldObject objTarget = creature.getTarget();

        if ((objTarget != null) && objTarget.isCreature()) {
            target = objTarget.asCreature();
        }

        return getTargetList(creature, onlyFirst, target);
    }

    public List<WorldObject> getTargetList(Creature creature, boolean onlyFirst, Creature target) {
        final ITargetTypeHandler handler = TargetHandler.getInstance().getHandler(getTargetType());
        if (handler != null) {
            try {
                final List<WorldObject> result = handler.getTargetList(this, creature, onlyFirst, target);

                if ((creature != null)
                        && creature.isMonster()
                        && !hasNegativeEffect()
                        && (result instanceof LinkedList)) {
                    result.removeIf(wo -> wo.isPlayable());
                }

                return result;
            } catch (Exception e) {
                log.warn("Exception in Skill.getTargetList(): {}", e.getMessage(), e);
            }
        }

        if (creature != null) {
            creature.sendMessage("Target type of skill is not currently handled.");
        }

        return Collections.emptyList();
    }

    public List<WorldObject> getTargetList(Creature creature) {
        return getTargetList(creature, false);
    }

    public WorldObject getFirstOfTargetList(Creature creature) {
        final List<WorldObject> targets = getTargetList(creature, true);
        if (targets.isEmpty()) {
            return null;
        }

        return targets.get(0);
    }

    public static boolean checkForAreaOffensiveSkills(
            Creature caster, Creature target, Skill skill, boolean sourceInArena) {
        if ((target == null) || target.isDead() || (target == caster)) {
            return false;
        }

        final Player player = caster.asPlayer();
        final Player targetPlayer = target.asPlayer();
        if (player != null) {
            if (targetPlayer != null) {
                if ((targetPlayer == caster) || (targetPlayer == player)) {
                    return false;
                }

                if (player.isAutoPlaying()) {
                    final int targetMode = player.getAutoPlaySettings().getNextTargetMode();
                    if ((targetMode == 1 /* Monster */) || (targetMode == 3 /* NPC */)) {
                        return false;
                    }
                }

                if (targetPlayer.inObserverMode()) {
                    return false;
                }

                if (skill.hasNegativeEffect()
                        && (player.getSiegeState() > 0)
                        && player.isInsideZone(ZoneId.SIEGE)
                        && (player.getSiegeState() == targetPlayer.getSiegeState())
                        && (player.getSiegeSide() == targetPlayer.getSiegeSide())) {
                    return false;
                }

                if (skill.hasNegativeEffect() && target.isInsideZone(ZoneId.PEACE)) {
                    return false;
                }

                final Party party = player.getParty();
                final Party targetParty = targetPlayer.getParty();
                if ((party != null) && (targetParty != null)) {
                    if (party.getLeaderObjectId() == targetParty.getLeaderObjectId()) {
                        return false;
                    }

                    if (party.isInCommandChannel() && (party.getCommandChannel() == targetParty.getCommandChannel())) {
                        return false;
                    }
                }

                if (player.isOnEvent()
                        && !player.isOnSoloEvent()
                        && targetPlayer.isOnEvent()
                        && (player.getTeam() == target.getTeam())) {
                    return false;
                }

                if (player.isAutoPlaying() && ((targetPlayer.getPvpFlag() == 0) || (targetPlayer.getKarma() < 1))) {
                    final int targetMode = player.getAutoPlaySettings().getNextTargetMode();
                    if ((targetMode != 0 /* Any Target */) && (targetMode != 2 /* Characters */)) {
                        return false;
                    }
                }

                if (!sourceInArena
                        && !(targetPlayer.isInsideZone(ZoneId.PVP) && !targetPlayer.isInsideZone(ZoneId.SIEGE))) {
                    if ((player.getAllyId() != 0) && (player.getAllyId() == targetPlayer.getAllyId())) {
                        return false;
                    }

                    if ((player.getClanId() != 0) && (player.getClanId() == targetPlayer.getClanId())) {
                        return false;
                    }

                    if (!player.checkPvpSkill(targetPlayer, skill)) {
                        return false;
                    }
                }
            }
        } else {
            if ((targetPlayer == null) && target.isAttackable() && caster.isAttackable()) {
                return false;
            }
        }

        if (!GeoEngine.getInstance().canSeeTarget(caster, target)) {
            return false;
        }

        return true;
    }

    public static boolean addSummon(Creature caster, Player owner, int radius, boolean isDead) {
        if (!owner.hasSummon()) {
            return false;
        }

        return addCharacter(caster, owner.getSummon(), radius, isDead);
    }

    public static boolean addCharacter(Creature caster, Creature target, int radius, boolean isDead) {
        if (isDead != target.isDead()) {
            return false;
        }

        if ((radius > 0) && !LocationUtil.checkIfInRange(radius, caster, target, true)) {
            return false;
        }

        return true;
    }

    public List<AbstractFunction> getStatFuncs(AbstractEffect effect, Creature creature) {
        if ((funcTemplates == null) || (creature == null)) {
            return Collections.<AbstractFunction>emptyList();
        }

        if (!creature.isPlayable() && !creature.isAttackable()) {
            return Collections.<AbstractFunction>emptyList();
        }

        final List<AbstractFunction> funcs = new ArrayList<>(funcTemplates.size());
        for (FuncTemplate t : funcTemplates) {
            final AbstractFunction f = t.getFunc(creature, null, this, this);
            if (f != null) {
                funcs.add(f);
            }
        }

        return funcs;
    }

    public List<AbstractEffect> getEffects(EffectScope effectScope) {
        return effectLists.get(effectScope);
    }

    public boolean hasEffects(EffectScope effectScope) {
        final List<AbstractEffect> effects = effectLists.get(effectScope);
        return (effects != null) && !effects.isEmpty();
    }

    public void applyEffectScope(
            EffectScope effectScope, BuffInfo info, boolean applyInstantEffects, boolean addContinuousEffects) {
        if ((effectScope != null) && hasEffects(effectScope)) {
            for (AbstractEffect effect : getEffects(effectScope)) {
                if (effect != null) {
                    if (effect.isInstant()) {
                        if (applyInstantEffects
                                && effect.calcSuccess(info.getEffector(), info.getEffected(), info.getSkill())) {
                            effect.onStart(info.getEffector(), info.getEffected(), info.getSkill());
                        }
                    } else if (addContinuousEffects
                            && effect.canStart(info.getEffector(), info.getEffected(), info.getSkill())) {
                        info.addEffect(effect);
                    }
                }
            }
        }
    }

    public void applyEffects(Creature effector, Creature effected) {
        applyEffects(effector, effected, false, false, true, 0);
    }

    public void applyEffects(Creature effector, Creature effected, boolean instant, int abnormalTime) {
        applyEffects(effector, effected, false, false, instant, abnormalTime);
    }

    public void applyEffects(
            Creature effector, Creature effected, boolean self, boolean passive, boolean instant, int abnormalTime) {
        applyEffects(effector, effected, self, passive, instant, abnormalTime, null);
    }

    public void applyStoredEffects(Creature effected, StoredEffect stored) {
        applyEffects(effected, effected, false, false, false, 0, stored);
    }

    private void applyEffects(
            Creature effector,
            Creature effected,
            boolean self,
            boolean passive,
            boolean instant,
            int abnormalTime,
            StoredEffect stored) {
        if (effected == null) {
            return;
        }

        if ((effector != effected)
                && hasNegativeEffect()
                && (effected.isInvul()
                        || (effector.isGM() && !effector.getAccessLevel().canGiveDamage()))) {
            return;
        }

        if (effected.isInvulAgainst(id, level)) {
            return;
        }

        boolean addContinuousEffects = !passive
                && (operateType.isToggle()
                        || (operateType.isContinuous() && Formulas.calcEffectSuccess(effector, effected, this)));
        if (!self && !passive) {
            final BuffInfo info = new BuffInfo(effector, effected, this);
            if (addContinuousEffects) {
                if (stored != null) {
                    info.restoreTiming(stored);
                } else if (abnormalTime > 0) {
                    info.setAbnormalTime(abnormalTime);
                }
            }

            applyEffectScope(EffectScope.GENERAL, info, instant, addContinuousEffects);

            final EffectScope pvpOrPveEffectScope = effector.isPlayable() && effected.isAttackable()
                    ? EffectScope.PVE
                    : effector.isPlayable() && effected.isPlayable() ? EffectScope.PVP : null;
            applyEffectScope(pvpOrPveEffectScope, info, instant, addContinuousEffects);
            applyEffectScope(EffectScope.CHANNELING, info, instant, addContinuousEffects);
            if (addContinuousEffects) {
                effected.getEffectList().add(info);
            }
        }

        if (self) {
            addContinuousEffects = !passive
                    && (operateType.isToggle()
                            || ((operateType.isContinuous() || operateType.isSelfContinuous())
                                    && Formulas.calcEffectSuccess(effector, effector, this)));

            final BuffInfo info = new BuffInfo(effector, effector, this);
            if (addContinuousEffects && (abnormalTime > 0)) {
                info.setAbnormalTime(abnormalTime);
            }

            applyEffectScope(EffectScope.SELF, info, instant, addContinuousEffects);

            // TODO : Need to be done better after skill rework
            if (addContinuousEffects && hasEffectType(EffectType.BUFF)) {
                info.getEffector().getEffectList().add(info);
            }
        }

        if (passive) {
            final BuffInfo info = new BuffInfo(effector, effector, this);
            applyEffectScope(EffectScope.PASSIVE, info, false, true);
            effector.getEffectList().add(info);
        }
    }

    public void activateSkill(Creature caster, Collection<WorldObject> targets) {
        activateSkill(caster, null, targets);
    }

    public void activateSkill(Cubic cubic, Collection<WorldObject> targets) {
        activateSkill(cubic.getOwner(), cubic, targets);
    }

    private void activateSkill(Creature caster, Cubic cubic, Collection<WorldObject> targets) {
        for (WorldObject obj : targets) {
            final Creature target = obj.asCreature();
            if (Formulas.calcBuffDebuffReflection(target, this)) {
                // If skill is reflected instant effects should be casted on target and continuous effects on caster.
                applyEffects(target, caster, false, 0);

                final BuffInfo info = new BuffInfo(caster, target, this);
                applyEffectScope(EffectScope.GENERAL, info, true, false);

                final EffectScope pvpOrPveEffectScope = caster.isPlayable() && target.isAttackable()
                        ? EffectScope.PVE
                        : caster.isPlayable() && target.isPlayable() ? EffectScope.PVP : null;
                applyEffectScope(pvpOrPveEffectScope, info, true, false);
                applyEffectScope(EffectScope.CHANNELING, info, true, false);
            } else {
                applyEffects(caster, target);
            }
        }

        if (hasEffects(EffectScope.SELF)) {
            if (caster.isAffectedBySkill(id)) {
                caster.stopSkillEffects(SkillFinishType.REMOVED, id);
            }

            applyEffects(caster, caster, true, false, true, 0);
        }

        if (cubic == null) {
            if (useSpiritShot()) {
                caster.setChargedShot(
                        caster.isChargedShot(ShotType.BLESSED_SPIRITSHOTS)
                                ? ShotType.BLESSED_SPIRITSHOTS
                                : ShotType.SPIRITSHOTS,
                        false);
            } else if (useSoulShot()) {
                caster.setChargedShot(ShotType.SOULSHOTS, false);
            }
        }

        if (suicideAttack) {
            caster.doDie(caster);
        }
    }

    public void attach(FuncTemplate f) {
        if (funcTemplates == null) {
            funcTemplates = new ArrayList<>(1);
        }

        funcTemplates.add(f);
    }

    public void addEffect(EffectScope effectScope, AbstractEffect effect) {
        List<AbstractEffect> effects = effectLists.get(effectScope);
        if (effects == null) {
            effects = new ArrayList<>(1);
            effectLists.put(effectScope, effects);
        }

        effects.add(effect);
    }

    public void attach(Condition c, boolean itemOrWeapon) {
        if (itemOrWeapon) {
            if (itemPreCondition == null) {
                itemPreCondition = new ArrayList<>();
            }

            itemPreCondition.add(c);
        } else {
            if (preCondition == null) {
                preCondition = new ArrayList<>();
            }

            preCondition.add(c);
        }
    }

    @Override
    public String toString() {
        return "Skill " + name + "(" + id + "," + level + ")";
    }

    public boolean ignoreShield() {
        return ignoreShield;
    }

    public boolean canBeDispeled() {
        return canBeDispeled;
    }

    public boolean canBeStolen() {
        return !isPassive()
                && !isToggle()
                && !debuff
                && !heroSkill
                && !gmSkill
                && !(isStatic() && (getId() != CommonSkill.CARAVANS_SECRET_MEDICINE.getId()))
                && canBeDispeled
                && (getId() != CommonSkill.SERVITOR_SHARE.getId());
    }

    private void parseAbnormalVisualEffect(String abnormalVisualEffects) {
        if (abnormalVisualEffects != null) {
            final String[] data = abnormalVisualEffects.split(";");
            List<AbnormalVisualEffect> avesEvent = null;
            List<AbnormalVisualEffect> avesSpecial = null;
            List<AbnormalVisualEffect> aves = null;
            for (String ave2 : data) {
                final AbnormalVisualEffect ave = AbnormalVisualEffect.valueOf(ave2);
                if (ave != null) {
                    if (ave.isEvent()) {
                        if (avesEvent == null) {
                            avesEvent = new ArrayList<>(1);
                        }

                        avesEvent.add(ave);
                        continue;
                    }

                    if (ave.isSpecial()) {
                        if (avesSpecial == null) {
                            avesSpecial = new ArrayList<>(1);
                        }

                        avesSpecial.add(ave);
                        continue;
                    }

                    if (aves == null) {
                        aves = new ArrayList<>(1);
                    }

                    aves.add(ave);
                }
            }

            if (avesEvent != null) {
                abnormalVisualEffectsEvent = avesEvent.toArray(new AbnormalVisualEffect[0]);
            }

            if (avesSpecial != null) {
                abnormalVisualEffectsSpecial = avesSpecial.toArray(new AbnormalVisualEffect[0]);
            }

            if (aves != null) {
                this.abnormalVisualEffects = aves.toArray(new AbnormalVisualEffect[0]);
            }
        }
    }

    public boolean hasEffectType(EffectType effectType, EffectType... effectTypes) {
        if (this.effectTypes == null) {
            synchronized (this) {
                if (this.effectTypes == null) {
                    final Set<Byte> effectTypesSet = new HashSet<>();
                    for (List<AbstractEffect> effectList : effectLists.values()) {
                        if (effectList != null) {
                            for (AbstractEffect effect : effectList) {
                                if (effect == null) {
                                    continue;
                                }

                                effectTypesSet.add((byte) effect.getEffectType().ordinal());
                            }
                        }
                    }

                    final Byte[] effectTypesArray = effectTypesSet.toArray(new Byte[0]);
                    Arrays.sort(effectTypesArray);
                    this.effectTypes = effectTypesArray;
                }
            }
        }

        if (Arrays.binarySearch(this.effectTypes, (byte) effectType.ordinal()) >= 0) {
            return true;
        }

        for (EffectType type : effectTypes) {
            if (Arrays.binarySearch(this.effectTypes, (byte) type.ordinal()) >= 0) {
                return true;
            }
        }

        return false;
    }
}
