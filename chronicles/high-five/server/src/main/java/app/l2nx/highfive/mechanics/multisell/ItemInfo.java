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

import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import lombok.Getter;

/**
 * @author DS
 */
public class ItemInfo {
    @Getter
    private final int enchantLevel;

    @Getter
    private final int augmentId;

    @Getter
    private final byte elementId;

    @Getter
    private final int elementPower;

    @Getter
    private final int[] elementals = new int[6];

    public ItemInfo(Item item) {
        enchantLevel = item.getEnchantLevel();
        augmentId = item.getAugmentation() != null ? item.getAugmentation().getAugmentationId() : 0;
        elementId = item.getAttackElementType();
        elementPower = item.getAttackElementPower();
        elementals[0] = item.getElementDefAttr(Elementals.FIRE);
        elementals[1] = item.getElementDefAttr(Elementals.WATER);
        elementals[2] = item.getElementDefAttr(Elementals.WIND);
        elementals[3] = item.getElementDefAttr(Elementals.EARTH);
        elementals[4] = item.getElementDefAttr(Elementals.HOLY);
        elementals[5] = item.getElementDefAttr(Elementals.DARK);
    }

    public ItemInfo(int enchantLevel) {
        this.enchantLevel = enchantLevel;
        augmentId = 0;
        elementId = Elementals.NONE;
        elementPower = 0;
        elementals[0] = 0;
        elementals[1] = 0;
        elementals[2] = 0;
        elementals[3] = 0;
        elementals[4] = 0;
        elementals[5] = 0;
    }
}
