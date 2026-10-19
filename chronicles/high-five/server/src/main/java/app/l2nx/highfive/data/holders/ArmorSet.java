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
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * @author Luno
 */
public class ArmorSet {
    @Getter
    private int chestId;

    private final List<Integer> legs;
    private final List<Integer> head;
    private final List<Integer> gloves;
    private final List<Integer> feet;
    private final List<Integer> shield;

    @Getter
    private final List<SkillHolder> skills;

    @Getter
    private final List<SkillHolder> shieldSkillId;

    @Getter
    private final List<SkillHolder> enchant6skillId;

    private int con;
    private int dex;
    private int str;
    private int men;
    private int wit;
    private int intStat;

    public ArmorSet() {
        legs = new ArrayList<>();
        head = new ArrayList<>();
        gloves = new ArrayList<>();
        feet = new ArrayList<>();
        shield = new ArrayList<>();
        skills = new ArrayList<>();
        shieldSkillId = new ArrayList<>();
        enchant6skillId = new ArrayList<>();
    }

    public void addChest(int id) {
        chestId = id;
    }

    public void addLegs(int id) {
        legs.add(id);
    }

    public void addHead(int id) {
        head.add(id);
    }

    public void addGloves(int id) {
        gloves.add(id);
    }

    public void addFeet(int id) {
        feet.add(id);
    }

    public void addShield(int id) {
        shield.add(id);
    }

    public void addSkill(SkillHolder holder) {
        skills.add(holder);
    }

    public void addShieldSkill(SkillHolder holder) {
        shieldSkillId.add(holder);
    }

    public void addEnchant6Skill(SkillHolder holder) {
        enchant6skillId.add(holder);
    }

    public void addCon(int value) {
        con = value;
    }

    public void addDex(int value) {
        dex = value;
    }

    public void addStr(int value) {
        str = value;
    }

    public void addMen(int value) {
        men = value;
    }

    public void addWit(int value) {
        wit = value;
    }

    public void addInt(int value) {
        intStat = value;
    }

    /**
     * Checks if player have equipped all items from set (not checking shield)
     * @param player whose inventory is being checked
     * @return True if player equips whole set
     */
    public boolean containAll(Player player) {
        final Inventory inv = player.getInventory();
        final Item legsItem = inv.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
        final Item headItem = inv.getPaperdollItem(Inventory.PAPERDOLL_HEAD);
        final Item glovesItem = inv.getPaperdollItem(Inventory.PAPERDOLL_GLOVES);
        final Item feetItem = inv.getPaperdollItem(Inventory.PAPERDOLL_FEET);
        int legs = 0;
        int head = 0;
        int gloves = 0;
        int feet = 0;
        if (legsItem != null) {
            legs = legsItem.getId();
        }

        if (headItem != null) {
            head = headItem.getId();
        }

        if (glovesItem != null) {
            gloves = glovesItem.getId();
        }

        if (feetItem != null) {
            feet = feetItem.getId();
        }

        return containAll(chestId, legs, head, gloves, feet);
    }

    public boolean containAll(int chest, int legs, int head, int gloves, int feet) {
        if ((chestId != 0) && (chestId != chest)) {
            return false;
        }

        if (!this.legs.isEmpty() && !this.legs.contains(legs)) {
            return false;
        }

        if (!this.head.isEmpty() && !this.head.contains(head)) {
            return false;
        }

        if (!this.gloves.isEmpty() && !this.gloves.contains(gloves)) {
            return false;
        }

        if (!this.feet.isEmpty() && !this.feet.contains(feet)) {
            return false;
        }

        return true;
    }

    public boolean containItem(int slot, int itemId) {
        switch (slot) {
            case Inventory.PAPERDOLL_CHEST: {
                return chestId == itemId;
            }
            case Inventory.PAPERDOLL_LEGS: {
                return legs.contains(itemId);
            }
            case Inventory.PAPERDOLL_HEAD: {
                return head.contains(itemId);
            }
            case Inventory.PAPERDOLL_GLOVES: {
                return gloves.contains(itemId);
            }
            case Inventory.PAPERDOLL_FEET: {
                return feet.contains(itemId);
            }
            default: {
                return false;
            }
        }
    }

    public boolean containShield(Player player) {
        final Inventory inv = player.getInventory();
        final Item shieldItem = inv.getPaperdollItem(Inventory.PAPERDOLL_LHAND);
        return ((shieldItem != null) && shield.contains(shieldItem.getId()));
    }

    public boolean containShield(int shieldId) {
        if (shield.isEmpty()) {
            return false;
        }

        return shield.contains(shieldId);
    }

    /**
     * @param player
     * @return true if all parts of set are enchanted to +6 or more
     */
    public boolean isEnchanted6(Player player) {
        // Player don't have full set.
        if (!containAll(player)) {
            return false;
        }

        final Inventory inv = player.getInventory();
        final Item chestItem = inv.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
        if ((chestItem == null) || (chestItem.getEnchantLevel() < 6)) {
            return false;
        }

        final Item legsItem = inv.getPaperdollItem(Inventory.PAPERDOLL_LEGS);
        if (!legs.isEmpty() && ((legsItem == null) || (legsItem.getEnchantLevel() < 6))) {
            return false;
        }

        final Item glovesItem = inv.getPaperdollItem(Inventory.PAPERDOLL_GLOVES);
        if (!gloves.isEmpty() && ((glovesItem == null) || (glovesItem.getEnchantLevel() < 6))) {
            return false;
        }

        final Item headItem = inv.getPaperdollItem(Inventory.PAPERDOLL_HEAD);
        if (!head.isEmpty() && ((headItem == null) || (headItem.getEnchantLevel() < 6))) {
            return false;
        }

        final Item feetItem = inv.getPaperdollItem(Inventory.PAPERDOLL_FEET);
        if (!feet.isEmpty() && ((feetItem == null) || (feetItem.getEnchantLevel() < 6))) {
            return false;
        }

        return true;
    }

    public int getCON() {
        return con;
    }

    public int getDEX() {
        return dex;
    }

    public int getSTR() {
        return str;
    }

    public int getMEN() {
        return men;
    }

    public int getWIT() {
        return wit;
    }

    public int getINT() {
        return intStat;
    }
}
