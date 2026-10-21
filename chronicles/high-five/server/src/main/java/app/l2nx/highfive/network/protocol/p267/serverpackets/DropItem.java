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

import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class DropItem extends ServerPacket {
    private final Item item;
    private final int objectId;

    /**
     * Constructor of the DropItem server packet
     * @param item : Item designating the item
     * @param playerObjId : int designating the player ID who dropped the item
     */
    public DropItem(Item item, int playerObjId) {
        this.item = item;
        objectId = playerObjId;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.DROP_ITEM.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(item.getObjectId());
        buffer.writeInt(item.getDisplayId());
        buffer.writeInt(item.getX());
        buffer.writeInt(item.getY());
        buffer.writeInt(item.getZ());

        // Only show item count if it is a stackable item.
        buffer.writeInt(item.isStackable());
        buffer.writeLong(item.getCount());
        buffer.writeInt(1); // unknown
    }
}
