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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.data.xml.HennaData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.List;

public class HennaEquipList extends ServerPacket {
    private final Player _player;
    private final List<Henna> _hennaEquipList;

    public HennaEquipList(Player player) {
        _player = player;
        _hennaEquipList = HennaData.getInstance().getHennaList(player.getPlayerClass());
    }

    public HennaEquipList(Player player, List<Henna> list) {
        _player = player;
        _hennaEquipList = list;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.HENNA_EQUIP_LIST.writeId(this, buffer);
        buffer.writeLong(_player.getAdena()); // activeChar current amount of Adena
        buffer.writeInt(3); // available equip slot
        buffer.writeInt(_hennaEquipList.size());
        for (Henna henna : _hennaEquipList) {
            // Player must have at least one dye in inventory.
            // to be able to see the Henna that can be applied with it.
            if ((_player.getInventory().getItemByItemId(henna.getDyeItemId())) != null) {
                buffer.writeInt(henna.getDyeId()); // dye Id
                buffer.writeInt(henna.getDyeItemId()); // item Id of the dye
                buffer.writeLong(henna.getWearCount()); // amount of dyes required
                buffer.writeLong(henna.getWearFee()); // amount of Adena required
                buffer.writeInt(henna.isAllowedClass(_player.getPlayerClass())); // meet the requirement or not
            }
        }
    }
}
