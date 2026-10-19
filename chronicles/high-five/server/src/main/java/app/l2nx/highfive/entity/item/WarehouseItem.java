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
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.CrystalType;
import app.l2nx.highfive.entity.item.type.ItemType;
import lombok.Getter;

/**
 * This class contains Item<br>
 * Use to sort Item of :
 * <ul>
 * <li>Armor</li>
 * <li>EtcItem</li>
 * <li>Weapon</li>
 * </ul>
 * @version $Revision: 1.7.2.2.2.5 $ $Date: 2005/04/06 18:25:18 $
 */
public class WarehouseItem {
    @Getter
    private final ItemTemplate item;

    @Getter
    private final int objectId;

    @Getter
    private final long count;

    @Getter
    private final int ownerId;

    @Getter
    private final int locationSlot;

    @Getter
    private final int enchantLevel;

    @Getter
    private final CrystalType itemGrade;

    @Getter
    private boolean augmented;

    @Getter
    private int augmentationId;

    @Getter
    private final int customType1;

    @Getter
    private final int customType2;

    @Getter
    private final int mana;

    @Getter
    private int attackElementType = -2;

    @Getter
    private int attackElementPower = 0;

    private final int[] elemDefAttr = {0, 0, 0, 0, 0, 0};

    @Getter
    private final int[] enchantOptions;

    @Getter
    private final int time;

    public WarehouseItem(Item item) {
        this.item = item.getTemplate();
        objectId = item.getObjectId();
        count = item.getCount();
        ownerId = item.getOwnerId();
        locationSlot = item.getLocationSlot();
        enchantLevel = item.getEnchantLevel();
        customType1 = item.getCustomType1();
        customType2 = item.getCustomType2();
        itemGrade = item.getTemplate().getCrystalType();
        if (item.isAugmented()) {
            augmented = true;
            augmentationId = item.getAugmentation().getAugmentationId();
        } else {
            augmented = false;
        }

        mana = item.getMana();
        time = item.isTimeLimitedItem() ? (int) (item.getRemainingTime() / 1000) : -1;
        attackElementType = item.getAttackElementType();
        attackElementPower = item.getAttackElementPower();
        for (byte i = 0; i < 6; i++) {
            elemDefAttr[i] = item.getElementDefAttr(i);
        }

        enchantOptions = item.getEnchantOptions();
    }

    /**
     * @return the first type.
     */
    public int getType1() {
        return item.getType1();
    }

    /**
     * @return the second type.
     */
    public int getType2() {
        return item.getType2();
    }

    /**
     * @return the second type.
     */
    public ItemType getItemType() {
        return item.getItemType();
    }

    /**
     * @return the ItemId.
     */
    public int getItemId() {
        return item.getId();
    }

    /**
     * @return the part of body used with this item.
     */
    public BodyPart getBodyPart() {
        return item.getBodyPart();
    }

    /**
     * @return {@code true} if the item is a weapon, {@code false} otherwise.
     */
    public boolean isWeapon() {
        return item instanceof Weapon;
    }

    /**
     * @return {@code true} if the item is an armor, {@code false} otherwise.
     */
    public boolean isArmor() {
        return item instanceof Armor;
    }

    /**
     * @return {@code true} if the item is an etc item, {@code false} otherwise.
     */
    public boolean isEtcItem() {
        return item instanceof EtcItem;
    }

    /**
     * @return the name of the item
     */
    public String getItemName() {
        return item.getName();
    }

    /**
     * @return the name of the item
     */
    public String getName() {
        return item.getName();
    }

    public int getElementDefAttr(byte i) {
        return elemDefAttr[i];
    }

    /**
     * @return the name of the item
     */
    @Override
    public String toString() {
        return item.toString();
    }
}
