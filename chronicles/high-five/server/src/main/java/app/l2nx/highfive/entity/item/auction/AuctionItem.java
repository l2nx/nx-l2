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
package app.l2nx.highfive.entity.item.auction;

import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.mechanics.options.Augmentation;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * @author Forsaiken
 */
public class AuctionItem {
    @Getter
    private final int auctionItemId;

    @Getter
    private final int auctionLength;

    @Getter
    private final long auctionInitBid;

    @Getter
    private final int itemId;

    @Getter
    private final long itemCount;

    private final StatSet itemExtra;

    public AuctionItem(
            int auctionItemId, int auctionLength, long auctionInitBid, int itemId, long itemCount, StatSet itemExtra) {
        this.auctionItemId = auctionItemId;
        this.auctionLength = auctionLength;
        this.auctionInitBid = auctionInitBid;
        this.itemId = itemId;
        this.itemCount = itemCount;
        this.itemExtra = itemExtra;
    }

    public boolean checkItemExists() {
        return ItemData.getInstance().getTemplate(itemId) != null;
    }

    public Item createNewItemInstance() {
        final Item item = ItemManager.createItem(ItemProcessType.RESTORE, itemId, itemCount, null, null);
        item.setEnchantLevel(item.getDefaultEnchantLevel());

        final int augmentationId = itemExtra.getInt("augmentation_id", 0);
        if (augmentationId > 0) {
            item.setAugmentation(new Augmentation(augmentationId));
        }

        return item;
    }
}
