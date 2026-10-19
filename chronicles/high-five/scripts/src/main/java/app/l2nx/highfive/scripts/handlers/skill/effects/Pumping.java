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

import app.l2nx.highfive.data.xml.FishingRodsData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.fishing.Fishing;
import app.l2nx.highfive.mechanics.fishing.FishingRod;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.util.StatSet;

/**
 * Pumping effect implementation.
 * @author UnAfraid
 */
public class Pumping extends AbstractEffect {
    private final double power;

    public Pumping(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        if (params.getString("power", null) == null) {
            throw new IllegalArgumentException(getClass().getSimpleName() + ": effect without power!");
        }

        power = params.getDouble("power");
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public EffectType getEffectType() {
        return EffectType.FISHING;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (!effector.isPlayer()) {
            return;
        }

        final Player player = effector.asPlayer();
        final Fishing fish = player.getFishCombat();
        if (fish == null) {
            // Pumping skill is available only while fishing
            player.sendPacket(SystemMessageId.YOU_MAY_ONLY_USE_THE_PUMPING_SKILL_WHILE_YOU_ARE_FISHING);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final Weapon weaponItem = player.getActiveWeaponItem();
        final Item weaponInst = effector.getActiveWeaponInstance();
        if ((weaponInst == null) || (weaponItem == null)) {
            return;
        }

        int ss = 1;
        int pen = 0;
        if (effector.isChargedShot(ShotType.FISH_SOULSHOTS)) {
            ss = 2;
        }

        final FishingRod fishingRod = FishingRodsData.getInstance().getFishingRod(weaponItem.getId());
        final double gradeBonus = fishingRod.getFishingRodLevel() * 0.1; // TODO: Check this formula (is guessed)
        int dmg = (int)
                ((fishingRod.getFishingRodDamage() + player.calcStat(Stat.FISHING_EXPERTISE, 1, null, null) + power)
                        * gradeBonus
                        * ss);

        // Penalty 5% less damage dealt
        if (player.getSkillLevel(1315) < (skill.getLevel() - 2)) // 1315 - Fish Expertise
        {
            player.sendPacket(
                    SystemMessageId
                            .DUE_TO_YOUR_REELING_AND_OR_PUMPING_SKILL_BEING_THREE_OR_MORE_LEVELS_HIGHER_THAN_YOUR_FISHING_SKILL_A_50_DAMAGE_PENALTY_WILL_BE_APPLIED);
            pen = (int) (dmg * 0.05);
            dmg -= pen;
        }

        if (ss > 1) {
            weaponInst.setChargedShot(ShotType.FISH_SOULSHOTS, false);
        }

        fish.usePumping(dmg, pen);
    }
}
