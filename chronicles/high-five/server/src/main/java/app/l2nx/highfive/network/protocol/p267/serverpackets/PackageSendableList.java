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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;

/**
 * @author -Wooden-, UnAfraid, mrTJO
 */
public class PackageSendableList extends AbstractItemPacket {
    private final Collection<Item> items;
    private final int playerObjId;
    private final long adena;

    public PackageSendableList(Collection<Item> items, int playerObjId, long adena) {
        this.items = items;
        this.playerObjId = playerObjId;
        this.adena = adena;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PACKAGE_SENDABLE_LIST.writeId(this, buffer);
        buffer.writeInt(playerObjId);
        buffer.writeLong(adena);
        buffer.writeInt(items.size());
        for (Item item : items) {
            writeItem(item, buffer);
            buffer.writeInt(item.getObjectId());
        }
    }
}
