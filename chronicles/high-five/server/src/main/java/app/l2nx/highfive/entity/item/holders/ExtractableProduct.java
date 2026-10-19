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

import lombok.Getter;

/**
 * @author JIV, Mobius
 */
public class ExtractableProduct {
    @Getter
    private final int id;

    @Getter
    private final long min;

    @Getter
    private final long max;

    @Getter
    private final int chance;

    @Getter
    private final int minEnchant;

    @Getter
    private final int maxEnchant;

    /**
     * Create Extractable product
     * @param id create item id
     * @param min item count max
     * @param max item count min
     * @param chance chance for creating
     * @param minEnchant item min enchant
     * @param maxEnchant item max enchant
     */
    public ExtractableProduct(int id, long min, long max, double chance, int minEnchant, int maxEnchant) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.chance = (int) (chance * 1000);
        this.minEnchant = minEnchant;
        this.maxEnchant = maxEnchant;
    }
}
