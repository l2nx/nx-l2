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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.recipe.ManufactureItem;
import app.l2nx.highfive.entity.item.recipe.RecipeList;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;
import java.util.Iterator;

public class RecipeShopManageList extends ServerPacket {
    private final Player seller;
    private final boolean dwarven;
    private Collection<RecipeList> recipes;

    public RecipeShopManageList(Player seller, boolean isDwarven) {
        this.seller = seller;
        dwarven = isDwarven;
        if (dwarven && this.seller.hasDwarvenCraft()) {
            recipes = this.seller.getDwarvenRecipeBook();
        } else {
            recipes = this.seller.getCommonRecipeBook();
        }

        if (this.seller.hasManufactureShop()) {
            final Iterator<ManufactureItem> it =
                    this.seller.getManufactureItems().values().iterator();
            ManufactureItem item;
            while (it.hasNext()) {
                item = it.next();
                if ((item.isDwarven() != dwarven) || !seller.hasRecipeList(item.getRecipeId())) {
                    it.remove();
                }
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.RECIPE_SHOP_MANAGE_LIST.writeId(this, buffer);
        buffer.writeInt(seller.getObjectId());
        buffer.writeInt((int) seller.getAdena());
        buffer.writeInt(!dwarven);
        if (recipes == null) {
            buffer.writeInt(0);
        } else {
            buffer.writeInt(recipes.size()); // number of items in recipe book
            int count = 0;
            for (RecipeList recipe : recipes) {
                count++;
                buffer.writeInt(recipe.getId());
                buffer.writeInt(count);
            }
        }

        if (!seller.hasManufactureShop()) {
            buffer.writeInt(0);
        } else {
            buffer.writeInt(seller.getManufactureItems().size());
            for (ManufactureItem item : seller.getManufactureItems().values()) {
                buffer.writeInt(item.getRecipeId());
                buffer.writeInt(0);
                buffer.writeLong(item.getCost());
            }
        }
    }
}
