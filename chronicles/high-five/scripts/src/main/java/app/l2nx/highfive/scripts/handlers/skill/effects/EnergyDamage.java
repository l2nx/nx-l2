/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.ClassBalanceConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.BaseStat;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.util.StatSet;

/**
 * Energy Attack effect implementation.
 * @author NosBit
 */
public class EnergyDamage extends AbstractEffect {
    private final double power;
    private final int criticalChance;
    private final boolean ignoreShieldDefence;

    public EnergyDamage(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        power = params.getDouble("power", 0);
        criticalChance = params.getInt("criticalChance", 0);
        ignoreShieldDefence = params.getBoolean("ignoreShieldDefence", false);
    }

    @Override
    public boolean calcSuccess(Creature effector, Creature effected, Skill skill) {
        // TODO: Verify this on retail
        return !Formulas.calcPhysicalSkillEvasion(effector, effected, skill);
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.PHYSICAL_ATTACK;
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        final Player attacker = effector.isPlayer() ? effector.asPlayer() : null;
        if (attacker == null) {
            return;
        }

        double attack = attacker.getPAtk(effected);
        double defence = effected.getPDef(attacker);
        if (!ignoreShieldDefence) {
            final byte shield = Formulas.calcShldUse(attacker, effected, skill, true);
            switch (shield) {
                case Formulas.SHIELD_DEFENSE_FAILED: {
                    break;
                }
                case Formulas.SHIELD_DEFENSE_SUCCEED: {
                    defence += effected.getShldDef();
                    break;
                }
                case Formulas.SHIELD_DEFENSE_PERFECT_BLOCK: {
                    defence = -1;
                    break;
                }
            }
        }

        double damage = 1;
        boolean critical = false;
        if (defence != -1) {
            final double damageMultiplier = Formulas.calcWeaponTraitBonus(attacker, effected)
                    * Formulas.calcAttributeBonus(attacker, effected, skill)
                    * Formulas.calcGeneralTraitBonus(attacker, effected, skill.getTraitType(), true);
            final boolean ss = skill.useSoulShot() && attacker.isChargedShot(ShotType.SOULSHOTS);
            final double ssBoost = ss ? 2 : 1.0;
            double weaponTypeBoost;
            final Weapon weapon = attacker.getActiveWeaponItem();
            if ((weapon != null)
                    && ((weapon.getItemType() == WeaponType.BOW) || (weapon.getItemType() == WeaponType.CROSSBOW))) {
                weaponTypeBoost = 70;
            } else {
                weaponTypeBoost = 77;
            }

            // Charges are already consumed before effects run, so add the consumed count back.
            final double energyChargesBoost = (((attacker.getCharges() + skill.getChargeConsumeCount()) - 1) * 0.2) + 1;
            attack += power;
            attack *= ssBoost;
            attack *= energyChargesBoost;
            attack *= weaponTypeBoost;
            if (effected.isPlayer()) {
                defence *= effected.calcStat(Stat.PVP_PHYS_SKILL_DEF, 1.0);
            }

            if (effected.isPlayable()) {
                defence *= attacker.isPlayable()
                        ? ClassBalanceConfig.PVP_ENERGY_SKILL_DEFENCE_MULTIPLIERS[
                                effected.asPlayer().getPlayerClass().getId()]
                        : ClassBalanceConfig.PVE_ENERGY_SKILL_DEFENCE_MULTIPLIERS[
                                effected.asPlayer().getPlayerClass().getId()];
            }

            damage = attack / defence;
            damage *= damageMultiplier;
            if (effected.isPlayer()) {
                damage *= attacker.calcStat(Stat.PVP_PHYS_SKILL_DMG, 1.0);
                damage = attacker.calcStat(Stat.PHYSICAL_SKILL_POWER, damage);
            }

            critical = (BaseStat.STR.calcBonus(attacker) * criticalChance)
                    > (ClockBridge.random().nextDouble() * 100);
            if (critical) {
                damage *= 2;
                final double critAdd = attacker.calcStat(Stat.CRITICAL_DAMAGE_ADD, 0, effected, skill);
                damage += critAdd;
                damage += effected.calcStat(Stat.DEFENCE_CRITICAL_DAMAGE_ADD, 0, effected, skill);
            }

            if (attacker.isPlayable()) {
                damage *= effected.isPlayable()
                        ? ClassBalanceConfig.PVP_ENERGY_SKILL_DAMAGE_MULTIPLIERS[
                                attacker.asPlayer().getPlayerClass().getId()]
                        : ClassBalanceConfig.PVE_ENERGY_SKILL_DAMAGE_MULTIPLIERS[
                                attacker.asPlayer().getPlayerClass().getId()];
            }
        }

        if (damage > 0) {
            attacker.sendDamageMessage(effected, (int) damage, false, critical, false);
            effected.reduceCurrentHp(damage, attacker, skill);
            effected.notifyDamageReceived(damage, attacker, skill, critical, false);

            Formulas.calcDamageReflected(attacker, effected, skill, critical);
        }
    }
}
