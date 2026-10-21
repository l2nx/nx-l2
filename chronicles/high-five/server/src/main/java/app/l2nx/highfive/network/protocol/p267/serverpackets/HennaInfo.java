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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class HennaInfo extends ServerPacket {
    private final Player player;
    private final List<Henna> hennas = new ArrayList<>();

    public HennaInfo(Player player) {
        this.player = player;
        for (Henna henna : this.player.getHennaList()) {
            if (henna != null) {
                hennas.add(henna);
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.HENNA_INFO.writeId(this, buffer);
        buffer.writeByte(player.getHennaStatINT()); // equip INT
        buffer.writeByte(player.getHennaStatSTR()); // equip STR
        buffer.writeByte(player.getHennaStatCON()); // equip CON
        buffer.writeByte(player.getHennaStatMEN()); // equip MEN
        buffer.writeByte(player.getHennaStatDEX()); // equip DEX
        buffer.writeByte(player.getHennaStatWIT()); // equip WIT
        buffer.writeInt(3); // Slots
        buffer.writeInt(hennas.size()); // Size
        for (Henna henna : hennas) {
            buffer.writeInt(henna.getDyeId());
            buffer.writeInt(1);
        }
    }
}
