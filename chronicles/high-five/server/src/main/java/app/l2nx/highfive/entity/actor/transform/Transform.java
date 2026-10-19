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
package app.l2nx.highfive.entity.actor.transform;

import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.serverpackets.ExBasicActionList;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class Transform {
    @Getter
    private final int id;

    @Getter
    private final int displayId;

    @Getter
    private final TransformType type;

    private final boolean canSwim;

    @Getter
    private final int spawnHeight;

    private final boolean canAttack;

    @Getter
    private final String name;

    @Getter
    private final String title;

    private TransformTemplate maleTemplate;
    private TransformTemplate femaleTemplate;

    public Transform(StatSet set) {
        id = set.getInt("id");
        displayId = set.getInt("displayId", id);
        type = set.getEnum("type", TransformType.class, TransformType.COMBAT);
        canSwim = set.getInt("can_swim", 0) == 1;
        canAttack = set.getInt("normal_attackable", 1) == 1;
        spawnHeight = set.getInt("spawn_height", 0);
        name = set.getString("setName", null);
        title = set.getString("setTitle", null);
    }

    public boolean canSwim() {
        return canSwim;
    }

    public boolean canAttack() {
        return canAttack;
    }

    public TransformTemplate getTemplate(Player player) {
        return player != null ? (player.getAppearance().isFemale() ? femaleTemplate : maleTemplate) : null;
    }

    public void setTemplate(boolean male, TransformTemplate template) {
        if (male) {
            maleTemplate = template;
        } else {
            femaleTemplate = template;
        }
    }

    /**
     * @return {@code true} if transform type is mode change, {@code false} otherwise
     */
    public boolean isStance() {
        return type == TransformType.MODE_CHANGE;
    }

    /**
     * @return {@code true} if transform type is combat, {@code false} otherwise
     */
    public boolean isCombat() {
        return type == TransformType.COMBAT;
    }

    /**
     * @return {@code true} if transform type is non combat, {@code false} otherwise
     */
    public boolean isNonCombat() {
        return type == TransformType.NON_COMBAT;
    }

    /**
     * @return {@code true} if transform type is flying, {@code false} otherwise
     */
    public boolean isFlying() {
        return type == TransformType.FLYING;
    }

    /**
     * @return {@code true} if transform type is cursed, {@code false} otherwise
     */
    public boolean isCursed() {
        return type == TransformType.CURSED;
    }

    /**
     * @return {@code true} if transform type is raiding, {@code false} otherwise
     */
    public boolean isRiding() {
        return type == TransformType.RIDING_MODE;
    }

    /**
     * @return {@code true} if transform type is pure stat, {@code false} otherwise
     */
    public boolean isPureStats() {
        return type == TransformType.PURE_STAT;
    }

    /**
     * @return {@code true} if transform uses equipped weapon stats, {@code false} otherwise
     */
    public boolean canUseWeaponStats() {
        return (type == TransformType.COMBAT) || (type == TransformType.MODE_CHANGE);
    }

    public float getCollisionHeight(Player player) {
        final TransformTemplate template = getTemplate(player);
        return template != null ? template.getCollisionHeight() : player.getCollisionHeight();
    }

    public float getCollisionRadius(Player player) {
        final TransformTemplate template = getTemplate(player);
        return template != null ? template.getCollisionRadius() : player.getCollisionRadius();
    }

    public int getBaseAttackRange(Player player) {
        final TransformTemplate template = getTemplate(player);
        return template != null
                ? template.getBaseAttackRange()
                : player.getTemplate().getBaseAttackRange();
    }

    public void onTransform(Player player) {
        final TransformTemplate template = getTemplate(player);
        if (template == null) {
            return;
        }

        // Start flying.
        if (isFlying()) {
            player.setFlying(true);
        }

        if (name != null) {
            player.getAppearance().setVisibleName(name);
        }

        if (title != null) {
            player.getAppearance().setVisibleTitle(title);
        }

        // Set inventory blocks if needed.
        if (!template.getAdditionalItems().isEmpty()) {
            final List<Integer> allowed = new ArrayList<>();
            final List<Integer> notAllowed = new ArrayList<>();
            for (AdditionalItemHolder holder : template.getAdditionalItems()) {
                if (holder.isAllowedToUse()) {
                    allowed.add(holder.getId());
                } else {
                    notAllowed.add(holder.getId());
                }
            }

            if (!allowed.isEmpty()) {
                final int[] items = new int[allowed.size()];
                for (int i = 0; i < items.length; i++) {
                    items[i] = allowed.get(i);
                }

                player.getInventory().setInventoryBlock(items, 1);
            }

            if (!notAllowed.isEmpty()) {
                final int[] items = new int[notAllowed.size()];
                for (int i = 0; i < items.length; i++) {
                    items[i] = notAllowed.get(i);
                }

                player.getInventory().setInventoryBlock(items, 2);
            }
        }

        // Send basic action list.
        if (template.hasBasicActionList()) {
            player.sendPacket(template.getBasicActionList());
        }
    }

    public void onUntransform(Player player) {
        final TransformTemplate template = getTemplate(player);
        if (template == null) {
            return;
        }

        // Stop flying.
        if (isFlying()) {
            player.setFlying(false);
        }

        if (name != null) {
            player.getAppearance().setVisibleName(null);
        }

        if (title != null) {
            player.getAppearance().setVisibleTitle(null);
        }

        // Remove common skills.
        if (!template.getSkills().isEmpty()) {
            for (SkillHolder holder : template.getSkills()) {
                final Skill skill = holder.getSkill();
                if (!SkillTreeData.getInstance().isSkillAllowed(player, skill)) {
                    player.removeSkill(skill, false, skill.isPassive());
                }
            }
        }

        // Remove skills depending on level.
        if (!template.getAdditionalSkills().isEmpty()) {
            for (AdditionalSkillHolder holder : template.getAdditionalSkills()) {
                final Skill skill = holder.getSkill();
                if ((player.getLevel() >= holder.getMinLevel())
                        && !SkillTreeData.getInstance().isSkillAllowed(player, skill)) {
                    player.removeSkill(skill, false, skill.isPassive());
                }
            }
        }

        // Remove transformation skills.
        player.removeAllTransformSkills();

        // Remove inventory blocks if needed.
        if (!template.getAdditionalItems().isEmpty()) {
            player.getInventory().unblock();
        }

        player.sendPacket(ExBasicActionList.STATIC_PACKET);
    }

    public void onLevelUp(Player player) {
        final TransformTemplate template = getTemplate(player);

        // Add skills depending on level.
        if ((template != null) && !template.getAdditionalSkills().isEmpty()) {
            for (AdditionalSkillHolder holder : template.getAdditionalSkills()) {
                if ((player.getLevel() >= holder.getMinLevel())
                        && (player.getSkillLevel(holder.getSkillId()) < holder.getSkillLevel())) {
                    player.addSkill(holder.getSkill(), false);
                    player.addTransformSkill(holder.getSkill());
                }
            }
        }
    }

    public double getStat(Player player, Stat stat) {
        double val = 0;
        final TransformTemplate template = getTemplate(player);
        if (template != null) {
            val = template.getStats(stat);
            final TransformLevelData data = template.getData(player.getLevel());
            if (data != null) {
                val = data.getStats(stat);
            }
        }

        return val;
    }

    /**
     * @param player
     * @param slot
     * @return
     */
    public int getBaseDefBySlot(Player player, int slot) {
        final TransformTemplate template = getTemplate(player);
        return template != null
                ? template.getDefense(slot)
                : player.getTemplate().getBaseDefBySlot(slot);
    }

    /**
     * @param player
     * @return
     */
    public double getLevelMod(Player player) {
        double val = -1;
        final TransformTemplate template = getTemplate(player);
        if (template != null) {
            final TransformLevelData data = template.getData(player.getLevel());
            if (data != null) {
                val = data.getLevelMod();
            }
        }

        return val;
    }
}
