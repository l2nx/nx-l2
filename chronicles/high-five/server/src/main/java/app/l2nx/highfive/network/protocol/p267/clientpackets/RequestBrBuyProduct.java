/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.holders.PrimeShopProductHolder;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.PrimeShopData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.managers.PrimeShopManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExBrBuyProduct;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExBrGamePoint;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author Mobius
 */
@Slf4j
public class RequestBrBuyProduct extends ClientPacket {
    private int productId;
    private int count;

    @Override
    protected void readImpl() {
        productId = readInt();
        count = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if ((count > 99) || (count <= 0)) {
            return;
        }

        final PrimeShopProductHolder product = PrimeShopData.getInstance().getProduct(productId);
        if (product == null) {
            player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_WRONG_PRODUCT));
            return;
        }

        final long totalPoints = product.getPrice() * count;
        if (totalPoints < 0) {
            player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_WRONG_PRODUCT));
            return;
        }

        final long gamePointSize = GeneralConfig.PRIME_SHOP_ITEM_ID == -1
                ? player.getGamePoints()
                : player.getInventory().getInventoryItemCount(GeneralConfig.PRIME_SHOP_ITEM_ID, -1);
        if (totalPoints > gamePointSize) {
            player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_NOT_ENOUGH_POINTS));
            return;
        }

        final ItemTemplate item = ItemData.getInstance().getTemplate(product.getItemId());
        if (item == null) {
            player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_WRONG_PRODUCT));
            return;
        }

        final int totalWeight = product.getItemWeight() * product.getItemCount() * count;
        int totalCount = 0;
        totalCount += item.isStackable() ? 1 : product.getItemCount() * count;
        if (!player.getInventory().validateCapacity(totalCount)
                || !player.getInventory().validateWeight(totalWeight)) {
            player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_INVENTORY_FULL));
            return;
        }

        // Pay for Item
        if (GeneralConfig.PRIME_SHOP_ITEM_ID == -1) {
            player.setGamePoints(player.getGamePoints() - totalPoints);
        } else if (player.getInventory()
                        .destroyItemByItemId(
                                ItemProcessType.FEE, GeneralConfig.PRIME_SHOP_ITEM_ID, totalPoints, player, null)
                == null) {
            player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_NOT_ENOUGH_POINTS));
            return;
        }

        // Buy Item
        player.addItem(ItemProcessType.BUY, product.getItemId(), product.getItemCount() * count, player, true);

        final StatusUpdate su = new StatusUpdate(player.getObjectId());
        su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
        player.sendPacket(su);

        player.sendPacket(new ExBrGamePoint(player));
        player.sendPacket(new ExBrBuyProduct(ExBrBuyProduct.RESULT_OK));
        player.broadcastUserInfo();

        // Save transaction info at SQL table prime_shop_transactions
        try {
            PrimeShopManager.getInstance().recordPurchase(player.getObjectId(), product.getProductId(), count);
        } catch (DataAccessException e) {
            log.warn("Could not save Item Mall transaction: {}", e.getMessage());
        }
    }
}
