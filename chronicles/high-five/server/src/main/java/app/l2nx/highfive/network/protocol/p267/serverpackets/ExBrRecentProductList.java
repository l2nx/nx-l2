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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.data.holders.PrimeShopProductHolder;
import app.l2nx.highfive.data.xml.PrimeShopData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.PrimeShopManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author Mobius
 */
@Slf4j
public class ExBrRecentProductList extends ServerPacket {
    private final List<PrimeShopProductHolder> itemList = new ArrayList<>();

    public ExBrRecentProductList(Player player) {
        try {
            for (int productId : PrimeShopManager.getInstance().findPurchasedProductIds(player.getObjectId())) {
                final PrimeShopProductHolder product =
                        PrimeShopData.getInstance().getProduct(productId);
                if ((product != null) && !itemList.contains(product)) {
                    itemList.add(product);
                }
            }
        } catch (DataAccessException e) {
            log.warn("Could not restore Item Mall transaction: {}", e.getMessage());
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        if (itemList.isEmpty()) {
            return;
        }

        ServerPackets.EX_BR_RECENT_PRODUCT_LIST.writeId(this, buffer);
        buffer.writeInt(itemList.size());
        for (PrimeShopProductHolder product : itemList) {
            buffer.writeInt(product.getProductId());
            buffer.writeShort(product.getCategory());
            buffer.writeInt(product.getPrice());
            buffer.writeInt(0); // category
            buffer.writeInt(0); // start sale
            buffer.writeInt(0); // end sale
            buffer.writeByte(0); // day week
            buffer.writeByte(0); // start hour
            buffer.writeByte(0); // start min
            buffer.writeByte(0); // end hour
            buffer.writeByte(0); // end min
            buffer.writeInt(0); // current stock
            buffer.writeInt(0); // max stock
        }
    }
}
