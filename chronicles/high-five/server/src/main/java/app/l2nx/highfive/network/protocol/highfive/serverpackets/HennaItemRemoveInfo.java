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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

public class HennaItemRemoveInfo extends ServerPacket {
    private final Player player;
    private final Henna henna;

    public HennaItemRemoveInfo(Henna henna, Player player) {
        this.henna = henna;
        this.player = player;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.HENNA_UNEQUIP_INFO.writeId(this, buffer);
        buffer.writeInt(henna.getDyeId()); // symbol Id
        buffer.writeInt(henna.getDyeItemId()); // item id of dye
        buffer.writeLong(henna.getCancelCount()); // total amount of dye require
        buffer.writeLong(henna.getCancelFee()); // total amount of Adena require to remove symbol
        buffer.writeInt(henna.isAllowedClass(player.getPlayerClass())); // able to remove or not
        buffer.writeLong(player.getAdena());
        buffer.writeInt(player.getINT()); // current INT
        buffer.writeByte(player.getINT() - henna.getStatINT()); // equip INT
        buffer.writeInt(player.getSTR()); // current STR
        buffer.writeByte(player.getSTR() - henna.getStatSTR()); // equip STR
        buffer.writeInt(player.getCON()); // current CON
        buffer.writeByte(player.getCON() - henna.getStatCON()); // equip CON
        buffer.writeInt(player.getMEN()); // current MEN
        buffer.writeByte(player.getMEN() - henna.getStatMEN()); // equip MEN
        buffer.writeInt(player.getDEX()); // current DEX
        buffer.writeByte(player.getDEX() - henna.getStatDEX()); // equip DEX
        buffer.writeInt(player.getWIT()); // current WIT
        buffer.writeByte(player.getWIT() - henna.getStatWIT()); // equip WIT
    }
}
