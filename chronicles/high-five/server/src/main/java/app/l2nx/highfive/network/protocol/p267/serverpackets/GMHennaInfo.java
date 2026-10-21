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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This server packet sends the player's henna information using the Game Master's UI.
 * @author KenM
 */
public class GMHennaInfo extends ServerPacket {
    private final Player player;
    private final List<Henna> hennas = new ArrayList<>();

    public GMHennaInfo(Player player) {
        this.player = player;
        for (Henna henna : this.player.getHennaList()) {
            if (henna != null) {
                hennas.add(henna);
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.GM_HENNA_INFO.writeId(this, buffer);
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
