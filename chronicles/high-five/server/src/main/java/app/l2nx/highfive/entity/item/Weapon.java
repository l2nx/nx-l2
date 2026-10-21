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
package app.l2nx.highfive.entity.item;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.conditions.ConditionGameChance;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcSkillSee;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * This class is dedicated to the management of weapons.
 */
@Slf4j
public class Weapon extends ItemTemplate {
    @Getter
    private WeaponType itemType;

    @Getter
    private boolean magicWeapon;

    @Getter
    private int soulShotCount;

    @Getter
    private int spiritShotCount;

    @Getter
    private int mpConsume;

    @Getter
    private int baseAttackRange;

    @Getter
    private int baseAttackRadius;

    @Getter
    private int baseAttackAngle;
    /**
     * Skill that activates when item is enchanted +4 (for duals).
     */
    private SkillHolder enchant4Skill = null;

    @Getter
    private int changeWeaponId;

    // Attached skills for Special Abilities
    private SkillHolder skillsOnMagic;
    private Condition skillsOnMagicCondition = null;
    private SkillHolder skillsOnCrit;
    private Condition skillsOnCritCondition = null;

    @Getter
    private int reducedSoulShot;

    @Getter
    private int reducedSoulShotChance;

    @Getter
    private int reducedMpConsume;

    @Getter
    private int reducedMpConsumeChance;

    @Getter
    private boolean forceEquip;

    @Getter
    private boolean attackWeapon;

    private boolean useWeaponSkillsOnly;

    /**
     * Constructor for Weapon.
     * @param set the StatSet designating the set of couples (key,value) characterizing the weapon.
     */
    public Weapon(StatSet set) {
        super(set);
    }

    @Override
    public void set(StatSet set) {
        super.set(set);
        itemType = WeaponType.valueOf(set.getString("weapon_type", "none").toUpperCase());
        type1 = ItemTemplate.TYPE1_WEAPON_RING_EARRING_NECKLACE;
        type2 = ItemTemplate.TYPE2_WEAPON;
        magicWeapon = set.getBoolean("is_magic_weapon", false);
        soulShotCount = set.getInt("soulshots", 0);
        spiritShotCount = set.getInt("spiritshots", 0);
        mpConsume = set.getInt("mp_consume", 0);
        baseAttackRange = set.getInt("attack_range", 40);
        final String damageRangeValue = set.getString("damage_range", null);
        // 0?;0?;fan sector;base attack angle
        final String[] damageRange = damageRangeValue == null ? null : damageRangeValue.split(";");
        if ((damageRange != null)
                && (damageRange.length >= 4)
                && StringUtils.isNumeric(damageRange[2])
                && StringUtils.isNumeric(damageRange[3])) {
            baseAttackRadius = Integer.parseInt(damageRange[2]);
            baseAttackAngle = Integer.parseInt(damageRange[3]);
        } else {
            baseAttackRadius = 40;
            baseAttackAngle = 0;
        }

        final String[] reducedSoulshots = set.getString("reduced_soulshot", "").split(",");
        reducedSoulShotChance = (reducedSoulshots.length == 2) ? Integer.parseInt(reducedSoulshots[0]) : 0;
        reducedSoulShot = (reducedSoulshots.length == 2) ? Integer.parseInt(reducedSoulshots[1]) : 0;

        final String[] reducedMpConsume =
                set.getString("reduced_mp_consume", "").split(",");
        reducedMpConsumeChance = (reducedMpConsume.length == 2) ? Integer.parseInt(reducedMpConsume[0]) : 0;
        this.reducedMpConsume = (reducedMpConsume.length == 2) ? Integer.parseInt(reducedMpConsume[1]) : 0;

        String skill = set.getString("enchant4_skill", null);
        if (skill != null) {
            final String[] info = skill.split("-");
            if ((info != null) && (info.length == 2)) {
                int id = 0;
                int level = 0;
                try {
                    id = Integer.parseInt(info[0]);
                    level = Integer.parseInt(info[1]);
                } catch (Exception nfe) {
                    // Incorrect syntax, do not add new skill.
                    log.info("> Could not parse {} in weapon enchant skills! item {}", skill, this);
                }

                if ((id > 0) && (level > 0)) {
                    enchant4Skill = new SkillHolder(id, level);
                }
            }
        }

        skill = set.getString("onmagic_skill", null);
        if (skill != null) {
            final String[] info = skill.split("-");
            final int chance = set.getInt("onmagic_chance", 100);
            if ((info != null) && (info.length == 2)) {
                int id = 0;
                int level = 0;
                try {
                    id = Integer.parseInt(info[0]);
                    level = Integer.parseInt(info[1]);
                } catch (Exception nfe) {
                    // Incorrect syntax, don't add new skill.
                    log.info("> Could not parse {} in weapon onmagic skills! item {}", skill, this);
                }

                if ((id > 0) && (level > 0) && (chance > 0)) {
                    skillsOnMagic = new SkillHolder(id, level);
                    skillsOnMagicCondition = new ConditionGameChance(chance);
                }
            }
        }

        skill = set.getString("oncrit_skill", null);
        if (skill != null) {
            final String[] info = skill.split("-");
            final int chance = set.getInt("oncrit_chance", 100);
            if ((info != null) && (info.length == 2)) {
                int id = 0;
                int level = 0;
                try {
                    id = Integer.parseInt(info[0]);
                    level = Integer.parseInt(info[1]);
                } catch (Exception nfe) {
                    // Incorrect syntax, don't add new skill.
                    log.info("> Could not parse {} in weapon oncrit skills! item {}", skill, this);
                }

                if ((id > 0) && (level > 0) && (chance > 0)) {
                    skillsOnCrit = new SkillHolder(id, level);
                    skillsOnCritCondition = new ConditionGameChance(chance);
                }
            }
        }

        changeWeaponId = set.getInt("change_weaponId", 0);
        forceEquip = set.getBoolean("isForceEquip", false);
        attackWeapon = set.getBoolean("isAttackWeapon", true);
        useWeaponSkillsOnly = set.getBoolean("useWeaponSkillsOnly", false);

        // Check if ranged weapon reuse delay is missing.
        if ((reuseDelay == 0) && itemType.isRanged()) {
            reuseDelay = 1500;
        }
    }

    /**
     * @return the ID of the Etc item after applying the mask.
     */
    @Override
    public int getItemMask() {
        return itemType.mask();
    }

    /**
     * @return {@code true} if the item is a weapon, {@code false} otherwise.
     */
    @Override
    public boolean isWeapon() {
        return true;
    }

    /**
     * @return the skill that player get when has equipped weapon +4 or more (for duals SA).
     */
    @Override
    public Skill getEnchant4Skill() {
        return enchant4Skill == null ? null : enchant4Skill.getSkill();
    }

    /**
     * @return {@code true} if the weapon is skills only, {@code false} otherwise.
     */
    public boolean useWeaponSkillsOnly() {
        return useWeaponSkillsOnly;
    }

    /**
     * @param caster the Creature pointing out the caster
     * @param target the Creature pointing out the target
     */
    public void castOnCriticalSkill(Creature caster, Creature target) {
        if (skillsOnCrit == null) {
            return;
        }

        final Skill onCritSkill = skillsOnCrit.getSkill();
        if ((skillsOnCritCondition != null) && !skillsOnCritCondition.test(caster, target, onCritSkill)) {
            // Chance not met
            return;
        }

        if (!onCritSkill.checkCondition(caster, target, false)) {
            // Skill condition not met.
            return;
        }

        onCritSkill.activateSkill(caster, Collections.singletonList(target));
    }

    /**
     * @param caster the Creature pointing out the caster
     * @param target the Creature pointing out the target
     * @param trigger the Skill pointing out the skill triggering this action
     */
    public void castOnMagicSkill(Creature caster, Creature target, Skill trigger) {
        if (skillsOnMagic == null) {
            return;
        }

        final Skill onMagicSkill = skillsOnMagic.getSkill();

        // Trigger only if both are good or bad magic.
        if (trigger.hasNegativeEffect() != onMagicSkill.hasNegativeEffect()) {
            return;
        }

        // No Trigger if not Magic Skill.
        if (!trigger.isMagic() && !onMagicSkill.isMagic()) {
            return;
        }

        if (trigger.isToggle()) {
            return;
        }

        if (caster.getAI().getCastTarget() != target) {
            return;
        }

        if ((skillsOnMagicCondition != null) && !skillsOnMagicCondition.test(caster, target, onMagicSkill)) {
            // Chance not met
            return;
        }

        if (!onMagicSkill.checkCondition(caster, target, false)) {
            // Skill condition not met.
            return;
        }

        if (onMagicSkill.hasNegativeEffect()
                && (Formulas.calcShldUse(caster, target, onMagicSkill) == Formulas.SHIELD_DEFENSE_PERFECT_BLOCK)) {
            return;
        }

        // Launch the magic skill and calculate its effects.
        // Get the skill handler corresponding to the skill type.
        onMagicSkill.activateSkill(caster, Collections.singletonList(target));

        // Notify quests of a skill use.
        if (caster.isPlayer()) {
            final List<WorldObject> targets = Collections.singletonList(target);
            World.forEachVisibleObjectInRange(caster, Npc.class, 1000, npc -> {
                if (EventDispatcher.getInstance().hasListener(EventType.ON_NPC_SKILL_SEE, npc)) {
                    EventDispatcher.getInstance()
                            .notifyEventAsync(
                                    new OnNpcSkillSee(npc, caster.asPlayer(), onMagicSkill, targets, false), npc);
                }
            });
        }

        if (caster.isPlayer()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_ACTIVATED);
            sm.addSkillName(onMagicSkill);
            caster.sendPacket(sm);
        }
    }

    public boolean isRange() {
        return isBow() || isCrossBow();
    }

    public boolean isBow() {
        return itemType == WeaponType.BOW;
    }

    public boolean isCrossBow() {
        return itemType == WeaponType.CROSSBOW;
    }
}
