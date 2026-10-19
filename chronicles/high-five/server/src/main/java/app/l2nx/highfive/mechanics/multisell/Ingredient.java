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
package app.l2nx.highfive.mechanics.multisell;

import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.Armor;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;
import lombok.Setter;

/**
 * @author DS
 */
public class Ingredient {
    @Getter
    @Setter
    private int itemId;

    @Getter
    @Setter
    private long itemCount;

    private final int enchantmentLevel;

    @Getter
    @Setter
    private boolean taxIngredient;

    @Setter
    private boolean maintainIngredient;

    @Getter
    private ItemTemplate template = null;

    @Getter
    private ItemInfo itemInfo = null;

    public Ingredient(StatSet set) {
        this(
                set.getInt("id"),
                set.getLong("count"),
                set.getInt("enchantmentLevel", 0),
                set.getBoolean("isTaxIngredient", false),
                set.getBoolean("maintainIngredient", false));
    }

    public Ingredient(
            int itemId, long itemCount, int enchantmentLevel, boolean isTaxIngredient, boolean maintainIngredient) {
        this.itemId = itemId;
        this.itemCount = itemCount;
        this.enchantmentLevel = enchantmentLevel;
        taxIngredient = isTaxIngredient;
        this.maintainIngredient = maintainIngredient;
        if (this.itemId > 0) {
            template = ItemData.getInstance().getTemplate(this.itemId);
        }
    }

    /**
     * @return a new Ingredient instance with the same values as this.
     */
    public Ingredient getCopy() {
        return new Ingredient(itemId, itemCount, enchantmentLevel, taxIngredient, maintainIngredient);
    }

    public void setItemInfo(Item item) {
        itemInfo = new ItemInfo(item);
    }

    public void setItemInfo(ItemInfo info) {
        itemInfo = info;
    }

    public int getEnchantLevel() {
        return itemInfo == null ? enchantmentLevel : itemInfo.getEnchantLevel();
    }

    public boolean getMaintainIngredient() {
        return maintainIngredient;
    }

    public boolean isStackable() {
        return template == null ? true : template.isStackable();
    }

    public boolean isArmorOrWeapon() {
        return template == null ? false : (template instanceof Armor) || (template instanceof Weapon);
    }

    public int getWeight() {
        return template == null ? 0 : template.getWeight();
    }
}
