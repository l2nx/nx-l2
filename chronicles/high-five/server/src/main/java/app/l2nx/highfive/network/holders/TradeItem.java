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
package app.l2nx.highfive.network.holders;

import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import lombok.Getter;
import lombok.Setter;

public class TradeItem {
    @Getter
    @Setter
    private int objectId;

    @Getter
    private final ItemTemplate item;

    @Getter
    private final int locationSlot;

    @Getter
    @Setter
    private int enchant;

    @Getter
    private final int customType1;

    @Getter
    private final int customType2;

    @Getter
    @Setter
    private long count;

    @Getter
    private long storeCount;

    @Getter
    @Setter
    private long price;

    @Getter
    private final byte attackElementType;

    @Getter
    private final int attackElementPower;

    private final int[] elemDefAttr = {0, 0, 0, 0, 0, 0};

    @Getter
    private final int[] enchantOptions;

    public TradeItem(Item item, long count, long price) {
        objectId = item.getObjectId();
        this.item = item.getTemplate();
        locationSlot = item.getLocationSlot();
        enchant = item.getEnchantLevel();
        customType1 = item.getCustomType1();
        customType2 = item.getCustomType2();
        this.count = count;
        this.price = price;
        attackElementType = item.getAttackElementType();
        attackElementPower = item.getAttackElementPower();
        for (byte i = 0; i < 6; i++) {
            elemDefAttr[i] = item.getElementDefAttr(i);
        }

        enchantOptions = item.getEnchantOptions();
    }

    public TradeItem(ItemTemplate item, long count, long price) {
        objectId = 0;
        this.item = item;
        locationSlot = 0;
        enchant = 0;
        customType1 = 0;
        customType2 = 0;
        this.count = count;
        storeCount = count;
        this.price = price;
        attackElementType = Elementals.NONE;
        attackElementPower = 0;
        enchantOptions = Item.DEFAULT_ENCHANT_OPTIONS;
    }

    public TradeItem(TradeItem item, long count, long price) {
        objectId = item.getObjectId();
        this.item = item.getItem();
        locationSlot = item.getLocationSlot();
        enchant = item.getEnchant();
        customType1 = item.getCustomType1();
        customType2 = item.getCustomType2();
        this.count = count;
        storeCount = count;
        this.price = price;
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
