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
package app.l2nx.highfive.entity.itemcontainer;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author DS
 */
@Slf4j
public class PlayerRefund extends ItemContainer {
    @Getter
    private final Player owner;

    public PlayerRefund(Player owner) {
        this.owner = owner;
    }

    @Override
    public String getName() {
        return "Refund";
    }

    @Override
    public ItemLocation getBaseLocation() {
        return ItemLocation.REFUND;
    }

    @Override
    protected void addItem(Item item) {
        super.addItem(item);
        try {
            if (getSize() > 12) {
                final Item removedItem = items.stream().findFirst().get();
                if (items.remove(removedItem)) {
                    ItemManager.destroyItem(ItemProcessType.REFUND, removedItem, getOwner(), null);
                    removedItem.updateDatabase(true);
                }
            }
        } catch (Exception e) {
            log.error("addItem()", e);
        }
    }

    @Override
    public void refreshWeight() {}

    @Override
    public void deleteMe() {
        try {
            for (Item item : items) {
                if (item != null) {
                    ItemManager.destroyItem(ItemProcessType.REFUND, item, getOwner(), null);
                    item.updateDatabase(true);
                }
            }
        } catch (Exception e) {
            log.error("deleteMe()", e);
        }

        items.clear();
    }

    @Override
    public void restore() {}
}
