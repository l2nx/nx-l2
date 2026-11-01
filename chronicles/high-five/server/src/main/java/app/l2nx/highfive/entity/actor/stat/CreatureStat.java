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
package app.l2nx.highfive.entity.actor.stat;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.ChampionMonstersConfig;
import app.l2nx.highfive.config.custom.ClassBalanceConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.entity.actor.transform.Transform;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.MoveType;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.TraitType;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Arrays;

@SingleWriter
public class CreatureStat {
    private static final int DIVINE_INSPIRATION = 1405;

    private final Creature activeChar;

    private final CreatureAccess access;

    private long exp = 0;

    private long sp = 0;

    private byte level = 1;

    private final float[] attackTraits = new float[TraitType.values().length];

    private final int[] attackTraitsCount = new int[TraitType.values().length];

    private final float[] defenceTraits = new float[TraitType.values().length];

    private final int[] defenceTraitsCount = new int[TraitType.values().length];

    private final int[] traitsInvul = new int[TraitType.values().length];
    private int maxBuffCount = PlayerConfig.BUFFS_MAX_AMOUNT;
    private double gmSpeedMultiplier = 1;

    public CreatureStat(Creature creature, CreatureAccess access) {
        activeChar = creature;
        this.access = access;
        Arrays.fill(attackTraits, 1.0f);
        Arrays.fill(defenceTraits, 1.0f);
    }

    public Creature getActiveChar() {
        WorldPartition.checkOwner();
        return activeChar;
    }

    /** Unchecked, for a subclass method that has already checked the owner. */
    protected final Creature creature() {
        return activeChar;
    }

    /** The creature's template, unchecked, for a method that has already checked the owner. */
    protected final CreatureTemplate template() {
        return access.template();
    }

    public final long getExp() {
        WorldPartition.checkOwner();
        return exp();
    }

    /** Unchecked {@link #getExp()}, the one subclasses override. */
    protected long exp() {
        return exp;
    }

    public void setExp(long value) {
        WorldPartition.checkOwner();
        exp = value;
    }

    public final long getSp() {
        WorldPartition.checkOwner();
        return sp();
    }

    /** Unchecked {@link #getSp()}, the one subclasses override. */
    protected long sp() {
        return sp;
    }

    public void setSp(long value) {
        WorldPartition.checkOwner();
        sp = value;
    }

    public final byte getLevel() {
        WorldPartition.checkOwner();
        return level();
    }

    /** Unchecked {@link #getLevel()}, the one subclasses override. */
    protected byte level() {
        return level;
    }

    public void setLevel(byte value) {
        WorldPartition.checkOwner();
        level = value;
    }

    public void setMaxBuffCount(int value) {
        WorldPartition.checkOwner();
        maxBuffCount = value;
    }

    // Works on the base count: getMaxBuffCount adds the Divine Inspiration level, which must not be folded into it.
    public void addMaxBuffCount(int delta) {
        WorldPartition.checkOwner();
        maxBuffCount = Math.max(0, maxBuffCount + delta);
    }

    public void setGmSpeedMultiplier(double value) {
        WorldPartition.checkOwner();
        gmSpeedMultiplier = value;
    }

    /**
     * The creature's calculation, reached through the access it handed this stat, without the check: for the getters
     * and the subclasses after their own.
     */
    protected final double calc(Stat stat, double initVal, Creature target, Skill skill) {
        return access.calculate(stat, initVal, target, skill);
    }

    public int getAccuracy() {
        WorldPartition.checkOwner();
        return (int) Math.round(calc(Stat.ACCURACY_COMBAT, 0, null, null));
    }

    public float getAttackSpeedMultiplier() {
        WorldPartition.checkOwner();
        return (float) ((1.1 * pAtkSpd()) / template().getBasePAtkSpd());
    }

    public double getCriticalDmg(Creature target, double init) {
        WorldPartition.checkOwner();
        return calc(Stat.CRITICAL_DAMAGE, init, target, null);
    }

    public int getCriticalHit(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        double val = calc(Stat.CRITICAL_RATE, template().getBaseCritRate(), target, skill);

        val = Math.min(val, PlayerConfig.MAX_PCRIT_RATE);

        return (int) (val + .5);
    }

    public int getCriticalHitPos(int base) {
        WorldPartition.checkOwner();
        return (int) calc(Stat.CRITICAL_RATE_POS, base, null, null);
    }

    public int getEvasionRate(Creature target) {
        WorldPartition.checkOwner();
        int val = (int) Math.round(calc(Stat.EVASION_RATE, 0, target, null));

        val = Math.min(val, PlayerConfig.MAX_EVASION);

        return val;
    }

    public int getMagicalAttackRange(Skill skill) {
        WorldPartition.checkOwner();
        if (skill != null) {
            return (int) calc(Stat.MAGIC_ATTACK_RANGE, skill.getCastRange(), null, skill);
        }

        return template().getBaseAttackRange();
    }

    public final int getMaxCp() {
        WorldPartition.checkOwner();
        return maxCp();
    }

    /** Unchecked {@link #getMaxCp()}, the one subclasses override. */
    protected int maxCp() {
        return (int) calc(Stat.MAX_CP, template().getBaseCpMax(), null, null);
    }

    public int getMaxRecoverableCp() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.MAX_RECOVERABLE_CP, maxCp(), null, null);
    }

    public final int getMaxHp() {
        WorldPartition.checkOwner();
        return maxHp();
    }

    /** Unchecked {@link #getMaxHp()}, the one subclasses override. */
    protected int maxHp() {
        return (int) calc(Stat.MAX_HP, template().getBaseHpMax(), null, null);
    }

    public int getMaxRecoverableHp() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.MAX_RECOVERABLE_HP, maxHp(), null, null);
    }

    public final int getMaxMp() {
        WorldPartition.checkOwner();
        return maxMp();
    }

    /** Unchecked {@link #getMaxMp()}, the one subclasses override. */
    protected int maxMp() {
        return (int) calc(Stat.MAX_MP, template().getBaseMpMax(), null, null);
    }

    public int getMaxRecoverableMp() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.MAX_RECOVERABLE_MP, maxMp(), null, null);
    }

    public double getMAtk(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        float bonusAtk = 1;
        if (ChampionMonstersConfig.CHAMPION_ENABLE && activeChar.isChampion()) {
            bonusAtk = ChampionMonstersConfig.CHAMPION_ATK;
        }

        if (activeChar.isRaid()) {
            bonusAtk *= NpcConfig.RAID_MATTACK_MULTIPLIER;
        }

        return Math.min(
                calc(Stat.MAGIC_ATTACK, template().getBaseMAtk() * bonusAtk, target, skill), PlayerConfig.MAX_MATK);
    }

    public final int getMAtkSpd() {
        WorldPartition.checkOwner();
        return mAtkSpd();
    }

    /** Unchecked {@link #getMAtkSpd()}, the one subclasses override. */
    protected int mAtkSpd() {
        float bonusSpdAtk = 1;
        if (ChampionMonstersConfig.CHAMPION_ENABLE && activeChar.isChampion()) {
            bonusSpdAtk = ChampionMonstersConfig.CHAMPION_SPD_ATK;
        }

        double val = calc(Stat.MAGIC_ATTACK_SPEED, template().getBaseMAtkSpd() * bonusSpdAtk, null, null);

        val = Math.min(val, PlayerConfig.MAX_MATK_SPEED);

        return (int) val;
    }

    public int getMCriticalHit(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        int val = (int) calc(Stat.MCRITICAL_RATE, 1, target, skill) * 10;

        if ((skill != null) && (target != null) && activeChar.isPlayable()) {
            if (target.isPlayable()) {
                val *= ClassBalanceConfig.PVP_MAGICAL_SKILL_CRITICAL_CHANCE_MULTIPLIERS[
                        activeChar.asPlayer().getPlayerClass().getId()];
            } else {
                val *= ClassBalanceConfig.PVE_MAGICAL_SKILL_CRITICAL_CHANCE_MULTIPLIERS[
                        activeChar.asPlayer().getPlayerClass().getId()];
            }
        }

        val = Math.min(val, PlayerConfig.MAX_MCRIT_RATE);

        return val;
    }

    public double getMDef(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        double defence = template().getBaseMDef();

        if (activeChar.isRaid()) {
            defence *= NpcConfig.RAID_MDEFENCE_MULTIPLIER;
        }

        if ((skill != null) && (target != null) && target.isPlayable()) {
            if (skill.isMagic()) {
                defence *= ClassBalanceConfig.PVE_MAGICAL_SKILL_DEFENCE_MULTIPLIERS[
                        target.asPlayer().getPlayerClass().getId()];
            } else {
                defence *= ClassBalanceConfig.PVE_PHYSICAL_SKILL_DEFENCE_MULTIPLIERS[
                        target.asPlayer().getPlayerClass().getId()];
            }
        }

        return calc(Stat.MAGIC_DEFENCE, defence, target, skill);
    }

    public int getCON() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.STAT_CON, template().getBaseCON(), null, null);
    }

    public int getDEX() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.STAT_DEX, template().getBaseDEX(), null, null);
    }

    public int getINT() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.STAT_INT, template().getBaseINT(), null, null);
    }

    public int getMEN() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.STAT_MEN, template().getBaseMEN(), null, null);
    }

    public int getSTR() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.STAT_STR, template().getBaseSTR(), null, null);
    }

    public int getWIT() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.STAT_WIT, template().getBaseWIT(), null, null);
    }

    public double getMovementSpeedMultiplier() {
        WorldPartition.checkOwner();
        double baseSpeed;
        if (activeChar.isInsideZone(ZoneId.WATER)) {
            baseSpeed = baseMoveSpeed(activeChar.isRunning() ? MoveType.FAST_SWIM : MoveType.SLOW_SWIM);
        } else {
            baseSpeed = baseMoveSpeed(activeChar.isRunning() ? MoveType.RUN : MoveType.WALK);
        }

        return moveSpeed() * (1. / baseSpeed);
    }

    public final double getRunSpeed() {
        WorldPartition.checkOwner();
        return runSpeed();
    }

    /** Unchecked {@link #getRunSpeed()}, the one subclasses override. */
    protected double runSpeed() {
        final double baseRunSpd = activeChar.isInsideZone(ZoneId.WATER) ? swimRunSpeed() : baseMoveSpeed(MoveType.RUN);
        if (baseRunSpd <= 0) {
            return 0;
        }

        return calc(Stat.MOVE_SPEED, baseRunSpd * gmSpeedMultiplier, null, null);
    }

    public final double getWalkSpeed() {
        WorldPartition.checkOwner();
        return walkSpeed();
    }

    /** Unchecked {@link #getWalkSpeed()}, the one subclasses override. */
    protected double walkSpeed() {
        final double baseWalkSpd =
                activeChar.isInsideZone(ZoneId.WATER) ? swimWalkSpeed() : baseMoveSpeed(MoveType.WALK);
        if (baseWalkSpd <= 0) {
            return 0;
        }

        return calc(Stat.MOVE_SPEED, baseWalkSpd * gmSpeedMultiplier, null, null);
    }

    public double getSwimRunSpeed() {
        WorldPartition.checkOwner();
        return swimRunSpeed();
    }

    private double swimRunSpeed() {
        final double baseRunSpd = baseMoveSpeed(MoveType.FAST_SWIM);
        if (baseRunSpd <= 0) {
            return 0;
        }

        return calc(Stat.MOVE_SPEED, baseRunSpd * gmSpeedMultiplier, null, null);
    }

    public double getSwimWalkSpeed() {
        WorldPartition.checkOwner();
        return swimWalkSpeed();
    }

    private double swimWalkSpeed() {
        final double baseWalkSpd = baseMoveSpeed(MoveType.SLOW_SWIM);
        if (baseWalkSpd <= 0) {
            return 0;
        }

        return calc(Stat.MOVE_SPEED, baseWalkSpd * gmSpeedMultiplier, null, null);
    }

    public final double getBaseMoveSpeed(MoveType type) {
        WorldPartition.checkOwner();
        return baseMoveSpeed(type);
    }

    /** Unchecked {@link #getBaseMoveSpeed(MoveType)}, the one subclasses override. */
    protected double baseMoveSpeed(MoveType type) {
        return template().getBaseMoveSpeed(type);
    }

    public final double getMoveSpeed() {
        WorldPartition.checkOwner();
        return moveSpeed();
    }

    /** Unchecked {@link #getMoveSpeed()}, the one subclasses override. */
    protected double moveSpeed() {
        if (activeChar.isInsideZone(ZoneId.WATER)) {
            return activeChar.isRunning() ? swimRunSpeed() : swimWalkSpeed();
        }

        return activeChar.isRunning() ? runSpeed() : walkSpeed();
    }

    public double getMReuseRate(Skill skill) {
        WorldPartition.checkOwner();
        return calc(Stat.MAGIC_REUSE_RATE, 1, null, skill);
    }

    public double getPAtk(Creature target) {
        WorldPartition.checkOwner();
        float bonusAtk = 1;
        if (ChampionMonstersConfig.CHAMPION_ENABLE && activeChar.isChampion()) {
            bonusAtk = ChampionMonstersConfig.CHAMPION_ATK;
        }

        if (activeChar.isRaid()) {
            bonusAtk *= NpcConfig.RAID_PATTACK_MULTIPLIER;
        }

        return Math.min(
                calc(Stat.POWER_ATTACK, template().getBasePAtk() * bonusAtk, target, null), PlayerConfig.MAX_PATK);
    }

    public final double getPAtkSpd() {
        WorldPartition.checkOwner();
        return pAtkSpd();
    }

    /** Unchecked {@link #getPAtkSpd()}, the one subclasses override. */
    protected double pAtkSpd() {
        float bonusAtk = 1;
        if (ChampionMonstersConfig.CHAMPION_ENABLE && activeChar.isChampion()) {
            bonusAtk = ChampionMonstersConfig.CHAMPION_SPD_ATK;
        }

        return Math.round(calc(Stat.POWER_ATTACK_SPEED, template().getBasePAtkSpd() * bonusAtk, null, null));
    }

    public double getPDef(Creature target) {
        WorldPartition.checkOwner();
        if (activeChar.isPlayable() && (target != null)) {
            if (target.isPlayable()) {
                return ClassBalanceConfig.PVP_PHYSICAL_ATTACK_DEFENCE_MULTIPLIERS[
                                activeChar.asPlayer().getPlayerClass().getId()]
                        * calc(
                                Stat.POWER_DEFENCE,
                                activeChar.isRaid()
                                        ? template().getBasePDef() * NpcConfig.RAID_PDEFENCE_MULTIPLIER
                                        : template().getBasePDef(),
                                target,
                                null);
            }

            return ClassBalanceConfig.PVE_PHYSICAL_ATTACK_DEFENCE_MULTIPLIERS[
                            activeChar.asPlayer().getPlayerClass().getId()]
                    * calc(
                            Stat.POWER_DEFENCE,
                            activeChar.isRaid()
                                    ? template().getBasePDef() * NpcConfig.RAID_PDEFENCE_MULTIPLIER
                                    : template().getBasePDef(),
                            target,
                            null);
        }

        return calc(
                Stat.POWER_DEFENCE,
                activeChar.isRaid()
                        ? template().getBasePDef() * NpcConfig.RAID_PDEFENCE_MULTIPLIER
                        : template().getBasePDef(),
                target,
                null);
    }

    public int getPhysicalAttackRange() {
        WorldPartition.checkOwner();
        final Transform transform = activeChar.getTransformation();
        int baseAttackRange;
        if (transform != null) {
            baseAttackRange = transform.getBaseAttackRange(activeChar.asPlayer());
        } else if (activeChar.getActiveWeaponItem() == null) {
            baseAttackRange = template().getBaseAttackRange();
        } else {
            baseAttackRange = 0;
        }

        return (int) calc(Stat.POWER_ATTACK_RANGE, baseAttackRange, null, null);
    }

    public final int getPhysicalAttackAngle() {
        WorldPartition.checkOwner();
        return physicalAttackAngle();
    }

    /** Unchecked {@link #getPhysicalAttackAngle()}, the one subclasses override. */
    protected int physicalAttackAngle() {
        return 0;
    }

    public double getWeaponReuseModifier(Creature target) {
        WorldPartition.checkOwner();
        return calc(Stat.ATK_REUSE, 1, target, null);
    }

    public int getShldDef() {
        WorldPartition.checkOwner();
        return (int) calc(Stat.SHIELD_DEFENCE, 0, null, null);
    }

    public int getMpConsume(Skill skill) {
        WorldPartition.checkOwner();
        if (skill == null) {
            return 1;
        }

        double mpConsume = skill.getMpConsume();
        final double nextDanceMpCost = Math.ceil(skill.getMpConsume() / 2.);
        if (skill.isDance()
                && PlayerConfig.DANCE_CONSUME_ADDITIONAL_MP
                && (activeChar != null)
                && (activeChar.getDanceCount() > 0)) {
            mpConsume += activeChar.getDanceCount() * nextDanceMpCost;
        }

        mpConsume = calc(Stat.MP_CONSUME, mpConsume, null, skill);
        if (skill.isDance()) {
            return (int) calc(Stat.DANCE_MP_CONSUME_RATE, mpConsume, null, null);
        }

        if (skill.isMagic()) {
            return (int) calc(Stat.MAGICAL_MP_CONSUME_RATE, mpConsume, null, null);
        }

        return (int) calc(Stat.PHYSICAL_MP_CONSUME_RATE, mpConsume, null, null);
    }

    public int getMpInitialConsume(Skill skill) {
        WorldPartition.checkOwner();
        return skill == null ? 1 : (int) calc(Stat.MP_CONSUME, skill.getMpInitialConsume(), null, skill);
    }

    public byte getAttackElement() {
        WorldPartition.checkOwner();
        final Item weaponInstance = activeChar.getActiveWeaponInstance();

        if ((weaponInstance != null) && (weaponInstance.getAttackElementType() >= 0)) {
            return weaponInstance.getAttackElementType();
        }

        int tempVal = 0;
        final int[] stats = {0, 0, 0, 0, 0, 0};

        byte returnVal = -2;
        stats[0] = (int) calc(Stat.FIRE_POWER, template().getBaseFire(), null, null);
        stats[1] = (int) calc(Stat.WATER_POWER, template().getBaseWater(), null, null);
        stats[2] = (int) calc(Stat.WIND_POWER, template().getBaseWind(), null, null);
        stats[3] = (int) calc(Stat.EARTH_POWER, template().getBaseEarth(), null, null);
        stats[4] = (int) calc(Stat.HOLY_POWER, template().getBaseHoly(), null, null);
        stats[5] = (int) calc(Stat.DARK_POWER, template().getBaseDark(), null, null);
        for (byte x = 0; x < 6; x++) {
            if (stats[x] > tempVal) {
                returnVal = x;
                tempVal = stats[x];
            }
        }

        return returnVal;
    }

    public int getAttackElementValue(byte attackAttribute) {
        WorldPartition.checkOwner();
        switch (attackAttribute) {
            case Elementals.FIRE: {
                return (int) calc(Stat.FIRE_POWER, template().getBaseFire(), null, null);
            }
            case Elementals.WATER: {
                return (int) calc(Stat.WATER_POWER, template().getBaseWater(), null, null);
            }
            case Elementals.WIND: {
                return (int) calc(Stat.WIND_POWER, template().getBaseWind(), null, null);
            }
            case Elementals.EARTH: {
                return (int) calc(Stat.EARTH_POWER, template().getBaseEarth(), null, null);
            }
            case Elementals.HOLY: {
                return (int) calc(Stat.HOLY_POWER, template().getBaseHoly(), null, null);
            }
            case Elementals.DARK: {
                return (int) calc(Stat.DARK_POWER, template().getBaseDark(), null, null);
            }
            default: {
                return 0;
            }
        }
    }

    public int getDefenseElementValue(byte defenseAttribute) {
        WorldPartition.checkOwner();
        switch (defenseAttribute) {
            case Elementals.FIRE: {
                return (int) calc(Stat.FIRE_RES, template().getBaseFireRes(), null, null);
            }
            case Elementals.WATER: {
                return (int) calc(Stat.WATER_RES, template().getBaseWaterRes(), null, null);
            }
            case Elementals.WIND: {
                return (int) calc(Stat.WIND_RES, template().getBaseWindRes(), null, null);
            }
            case Elementals.EARTH: {
                return (int) calc(Stat.EARTH_RES, template().getBaseEarthRes(), null, null);
            }
            case Elementals.HOLY: {
                return (int) calc(Stat.HOLY_RES, template().getBaseHolyRes(), null, null);
            }
            case Elementals.DARK: {
                return (int) calc(Stat.DARK_RES, template().getBaseDarkRes(), null, null);
            }
            default: {
                return (int) template().getBaseElementRes();
            }
        }
    }

    public float getAttackTrait(TraitType traitType) {
        WorldPartition.checkOwner();
        return attackTraits[traitType.ordinal()];
    }

    public boolean hasAttackTrait(TraitType traitType) {
        WorldPartition.checkOwner();
        return attackTraitsCount[traitType.ordinal()] > 0;
    }

    public void addAttackTrait(TraitType traitType, float multiplier) {
        WorldPartition.checkOwner();
        attackTraits[traitType.ordinal()] *= multiplier;
        attackTraitsCount[traitType.ordinal()]++;
    }

    /** Undoes {@link #addAttackTrait}; a trait never added is left as is. */
    public void removeAttackTrait(TraitType traitType, float multiplier) {
        WorldPartition.checkOwner();
        if (attackTraitsCount[traitType.ordinal()] == 0) {
            return;
        }

        attackTraits[traitType.ordinal()] /= multiplier;
        attackTraitsCount[traitType.ordinal()]--;
    }

    public float getDefenceTrait(TraitType traitType) {
        WorldPartition.checkOwner();
        return defenceTraits[traitType.ordinal()];
    }

    public boolean hasDefenceTrait(TraitType traitType) {
        WorldPartition.checkOwner();
        return defenceTraitsCount[traitType.ordinal()] > 0;
    }

    public void addDefenceTrait(TraitType traitType, float multiplier) {
        WorldPartition.checkOwner();
        defenceTraits[traitType.ordinal()] *= multiplier;
        defenceTraitsCount[traitType.ordinal()]++;
    }

    /** Undoes {@link #addDefenceTrait}; a trait never added is left as is. */
    public void removeDefenceTrait(TraitType traitType, float multiplier) {
        WorldPartition.checkOwner();
        if (defenceTraitsCount[traitType.ordinal()] == 0) {
            return;
        }

        defenceTraits[traitType.ordinal()] /= multiplier;
        defenceTraitsCount[traitType.ordinal()]--;
    }

    public boolean isTraitInvul(TraitType traitType) {
        WorldPartition.checkOwner();
        return traitsInvul[traitType.ordinal()] > 0;
    }

    public void addTraitInvul(TraitType traitType) {
        WorldPartition.checkOwner();
        traitsInvul[traitType.ordinal()]++;
    }

    public void removeTraitInvul(TraitType traitType) {
        WorldPartition.checkOwner();
        if (traitsInvul[traitType.ordinal()] > 0) {
            traitsInvul[traitType.ordinal()]--;
        }
    }

    public int getMaxBuffCount() {
        WorldPartition.checkOwner();
        return maxBuffCount + activeChar.getSkillLevel(DIVINE_INSPIRATION);
    }

    /**
     * What a stat reads of its creature without the ownership check. A creature hands it to its own stat only, whose
     * getters check once before reading through it.
     */
    public interface CreatureAccess {
        double calculate(Stat stat, double initVal, Creature target, Skill skill);

        CreatureTemplate template();
    }
}
