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
 * @author Index, Mobius
 */
public class ItemEnchantHolder extends ItemHolder {
    @Getter
    private final int enchantLevel;

    public ItemEnchantHolder(int id, long count) {
        super(id, count);
        enchantLevel = 0;
    }

    public ItemEnchantHolder(int id, long count, int enchantLevel) {
        super(id, count);
        this.enchantLevel = enchantLevel;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ItemEnchantHolder objInstance)) {
            return false;
        } else if (obj == this) {
            return true;
        }

        return (getId() == objInstance.getId())
                && ((getCount() == objInstance.getCount()) && (enchantLevel == objInstance.getEnchantLevel()));
    }

    @Override
    public String toString() {
        return "[" + getClass().getSimpleName() + "] ID: " + getId() + ", count: " + getCount() + ", enchant level: "
                + enchantLevel;
    }
}
