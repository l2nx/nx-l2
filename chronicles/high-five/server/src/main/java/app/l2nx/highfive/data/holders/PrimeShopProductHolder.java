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
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import lombok.Getter;

/**
 * @author Mobius
 */
public class PrimeShopProductHolder {
    @Getter
    private final int productId;

    @Getter
    private final int category;

    @Getter
    private final int price;

    @Getter
    private final int itemId;

    @Getter
    private final int itemCount;

    @Getter
    private final int itemWeight;

    @Getter
    private final boolean tradable;

    public PrimeShopProductHolder(int productId, int category, int points, int item, int count) {
        this.productId = productId;
        this.category = category;
        price = points;
        itemId = item;
        itemCount = count;

        final ItemTemplate itemTemplate = ItemData.getInstance().getTemplate(item);
        if (itemTemplate != null) {
            itemWeight = itemTemplate.getWeight();
            tradable = itemTemplate.isTradeable();
        } else {
            itemWeight = 0;
            tradable = true;
        }
    }
}
