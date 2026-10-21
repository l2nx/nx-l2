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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExBaseAttributeCancelResult;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;

public class RequestExRemoveItemAttribute extends ClientPacket {
    private int objectId;
    private long price;
    private byte element;

    @Override
    protected void readImpl() {
        objectId = readInt();
        element = (byte) readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final Item targetItem = player.getInventory().getItemByObjectId(objectId);
        if (targetItem == null) {
            return;
        }

        if ((targetItem.getElementals() == null) || (targetItem.getElemental(element) == null)) {
            return;
        }

        if (player.reduceAdena(ItemProcessType.FEE, getPrice(targetItem), player, true)) {
            if (targetItem.isEquipped()) {
                targetItem.getElemental(element).removeBonus(player);
            }

            targetItem.clearElementAttr(element);
            player.updateUserInfo();

            final InventoryUpdate iu = new InventoryUpdate();
            iu.addModifiedItem(targetItem);
            player.sendInventoryUpdate(iu);
            SystemMessage sm;
            final byte realElement = targetItem.isArmor() ? Elementals.getOppositeElement(element) : element;
            if (targetItem.isEnchanted()) {
                if (targetItem.isArmor()) {
                    sm = new SystemMessage(
                            SystemMessageId.S1_S2_S_S3_ATTRIBUTE_WAS_REMOVED_SO_RESISTANCE_TO_S4_WAS_DECREASED);
                } else {
                    sm = new SystemMessage(SystemMessageId.S1_S2_S_S3_ATTRIBUTE_HAS_BEEN_REMOVED);
                }

                sm.addInt(targetItem.getEnchantLevel());
                sm.addItemName(targetItem);
                sm.addElemental(realElement);
                if (targetItem.isArmor()) {
                    sm.addElemental(Elementals.getOppositeElement(realElement));
                }
            } else {
                if (targetItem.isArmor()) {
                    sm = new SystemMessage(
                            SystemMessageId.S1_S_S2_ATTRIBUTE_WAS_REMOVED_AND_RESISTANCE_TO_S3_WAS_DECREASED);
                } else {
                    sm = new SystemMessage(SystemMessageId.S1_S_S2_ATTRIBUTE_HAS_BEEN_REMOVED);
                }

                sm.addItemName(targetItem);
                sm.addElemental(realElement);
                if (targetItem.isArmor()) {
                    sm.addElemental(Elementals.getOppositeElement(realElement));
                }
            }

            player.sendPacket(sm);
            player.sendPacket(new ExBaseAttributeCancelResult(targetItem.getObjectId(), element));
        } else {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_FUNDS_TO_CANCEL_THIS_ATTRIBUTE);
        }
    }

    private long getPrice(Item item) {
        switch (item.getTemplate().getCrystalType()) {
            case S: {
                if (item.getTemplate() instanceof Weapon) {
                    price = 50000;
                } else {
                    price = 40000;
                }
                break;
            }
            case S80: {
                if (item.getTemplate() instanceof Weapon) {
                    price = 100000;
                } else {
                    price = 80000;
                }
                break;
            }
            case S84: {
                if (item.getTemplate() instanceof Weapon) {
                    price = 200000;
                } else {
                    price = 160000;
                }
                break;
            }
        }

        return price;
    }
}
