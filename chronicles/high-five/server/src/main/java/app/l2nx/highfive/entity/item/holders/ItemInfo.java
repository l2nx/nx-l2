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
package app.l2nx.highfive.entity.item.holders;

import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.holders.TradeItem;
import lombok.Getter;

/**
 * Get all information from Item to generate ItemInfo.
 */
public class ItemInfo {
    /** Identifier of the Item */
    @Getter
    private int objectId;

    /** The Item template of the Item */
    @Getter
    private ItemTemplate item;

    /** The level of enchant on the Item */
    @Getter
    private int enchant;

    /** The augmentation of the item */
    @Getter
    private int augmentationBonus;

    /** The quantity of Item */
    @Getter
    private long count;

    /** The price of the Item */
    @Getter
    private int price;

    /** The custom Item types (used loto, race tickets) */
    @Getter
    private int customType1;

    @Getter
    private int customType2;

    /** If True the Item is equipped */
    @Getter
    private int equipped;

    /** The action to do clientside (1=ADD, 2=MODIFY, 3=REMOVE) */
    @Getter
    private int change;

    /** The mana of this item */
    @Getter
    private int mana;

    @Getter
    private int time;

    @Getter
    private int location;

    @Getter
    private int attackElementType = -2;

    @Getter
    private int attackElementPower = 0;

    private final int[] elemDefAttr = {0, 0, 0, 0, 0, 0};

    @Getter
    private int[] enchantOptions;

    /**
     * Get all information from Item to generate ItemInfo.
     * @param item
     */
    public ItemInfo(Item item) {
        if (item == null) {
            return;
        }

        // Get the Identifier of the Item.
        objectId = item.getObjectId();

        // Get the Item of the Item.
        this.item = item.getTemplate();

        // Get the enchant level of the Item.
        enchant = item.getEnchantLevel();

        // Get the augmentation bonus.
        augmentationBonus = item.isAugmented() ? item.getAugmentation().getAugmentationId() : 0;

        // Get the quantity of the Item.
        count = item.getCount();

        // Get custom item types (used loto, race tickets).
        customType1 = item.getCustomType1();
        customType2 = item.getCustomType2();

        // Verify if the Item is equipped.
        equipped = item.isEquipped() ? 1 : 0;

        // Get the action to do clientside.
        switch (item.getLastChange()) {
            case Item.ADDED: {
                change = 1;
                break;
            }
            case Item.MODIFIED: {
                change = 2;
                break;
            }
            case Item.REMOVED: {
                change = 3;
                break;
            }
        }

        // Get shadow item mana.
        mana = item.getMana();
        time = item.isTimeLimitedItem() ? (int) (item.getRemainingTime() / 1000) : -9999;
        location = item.getLocationSlot();
        attackElementType = item.getAttackElementType();
        attackElementPower = item.getAttackElementPower();
        for (byte i = 0; i < 6; i++) {
            elemDefAttr[i] = item.getElementDefAttr(i);
        }

        enchantOptions = item.getEnchantOptions();
    }

    public ItemInfo(Item item, int change) {
        this(item);
        this.change = change;
    }

    public ItemInfo(TradeItem item) {
        if (item == null) {
            return;
        }

        // Get the Identifier of the Item.
        objectId = item.getObjectId();

        // Get the Item of the Item.
        this.item = item.getItem();

        // Get the enchant level of the Item.
        enchant = item.getEnchant();

        // Get the augmentation bonus.
        augmentationBonus = 0;

        // Get the quantity of the Item.
        count = item.getCount();

        // Get custom item types (used loto, race tickets).
        customType1 = item.getCustomType1();
        customType2 = item.getCustomType2();

        // Verify if the Item is equipped.
        equipped = 0;

        // Get the action to do clientside.
        change = 0;

        // Get shadow item mana.
        mana = -1;
        time = -9999;
        location = item.getLocationSlot();
        attackElementType = item.getAttackElementType();
        attackElementPower = item.getAttackElementPower();
        for (byte i = 0; i < 6; i++) {
            elemDefAttr[i] = item.getElementDefAttr(i);
        }

        enchantOptions = item.getEnchantOptions();
    }

    public int getElementDefAttr(byte i) {
        return elemDefAttr[i];
    }
}
