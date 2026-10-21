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

import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class ExPutItemResultForVariationCancel extends ServerPacket {
    private final int itemObjId;
    private final int itemId;
    private final int itemAug1;
    private final int itemAug2;
    private final int price;

    public ExPutItemResultForVariationCancel(Item item, int price) {
        itemObjId = item.getObjectId();
        itemId = item.getDisplayId();
        this.price = price;
        itemAug1 = ((short) item.getAugmentation().getAugmentationId());
        itemAug2 = item.getAugmentation().getAugmentationId() >> 16;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_PUT_ITEM_RESULT_FOR_VARIATION_CANCEL.writeId(this, buffer);
        buffer.writeInt(itemObjId);
        buffer.writeInt(itemId);
        buffer.writeInt(itemAug1);
        buffer.writeInt(itemAug2);
        buffer.writeLong(price);
        buffer.writeInt(1);
    }
}
