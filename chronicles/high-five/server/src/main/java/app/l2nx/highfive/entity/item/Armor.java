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

import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.type.ArmorType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is dedicated to the management of armors.
 */
@Slf4j
public class Armor extends ItemTemplate {
    /**
     * Skill that activates when armor is enchanted +4.
     */
    private SkillHolder enchant4Skill = null;

    @Getter
    private ArmorType itemType;

    /**
     * Constructor for Armor.
     * @param set the StatSet designating the set of couples (key,value) characterizing the armor.
     */
    public Armor(StatSet set) {
        super(set);
    }

    @Override
    public void set(StatSet set) {
        super.set(set);
        itemType = set.getEnum("armor_type", ArmorType.class, ArmorType.NONE);

        final BodyPart bodyPart = getBodyPart();
        if ((bodyPart == BodyPart.NECK) //
                || (bodyPart == BodyPart.L_EAR) //
                || (bodyPart == BodyPart.R_EAR) //
                || (bodyPart == BodyPart.LR_EAR) //
                || (bodyPart == BodyPart.L_FINGER) //
                || (bodyPart == BodyPart.R_FINGER) //
                || (bodyPart == BodyPart.LR_FINGER) //
                || (bodyPart == BodyPart.R_BRACELET) //
                || (bodyPart == BodyPart.L_BRACELET)) {
            type1 = ItemTemplate.TYPE1_WEAPON_RING_EARRING_NECKLACE;
            type2 = ItemTemplate.TYPE2_ACCESSORY;
        } else {
            if ((itemType == ArmorType.NONE) && (bodyPart == BodyPart.L_HAND)) {
                itemType = ArmorType.SHIELD;
            }

            type1 = ItemTemplate.TYPE1_SHIELD_ARMOR;
            type2 = ItemTemplate.TYPE2_SHIELD_ARMOR;
        }

        final String skill = set.getString("enchant4_skill", null);
        if (skill == null) {
            return;
        }

        final String[] info = skill.split("-");
        if ((info == null) || (info.length != 2)) {
            return;
        }

        int id = 0;
        int level = 0;
        try {
            id = Integer.parseInt(info[0]);
            level = Integer.parseInt(info[1]);
        } catch (Exception nfe) {
            // Incorrect syntax, don't add new skill.
            log.info("> Could not parse {} in armor enchant skills! item {}", skill, this);
        }

        if ((id > 0) && (level > 0)) {
            enchant4Skill = new SkillHolder(id, level);
        }
    }

    /**
     * @return the ID of the item after applying the mask.
     */
    @Override
    public int getItemMask() {
        return itemType.mask();
    }

    /**
     * @return {@code true} if the item is an etc item, {@code false} otherwise.
     */
    @Override
    public boolean isArmor() {
        return true;
    }

    /**
     * @return skill that player get when has equipped armor +4 or more
     */
    @Override
    public Skill getEnchant4Skill() {
        return enchant4Skill == null ? null : enchant4Skill.getSkill();
    }
}
