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

import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.residences.ClanHall.ClanHallFunction;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Steuf
 */
public class AgitDecoInfo extends ServerPacket {
    private final AuctionableHall clanHall;

    public AgitDecoInfo(AuctionableHall clanHall) {
        this.clanHall = clanHall;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.AGIT_DECO_INFO.writeId(this, buffer);
        buffer.writeInt(clanHall.getId());

        // Fireplace
        ClanHallFunction function = clanHall.getFunction(ClanHall.FUNC_RESTORE_HP);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (((clanHall.getGrade() == 0) && (function.getLevel() < 220))
                || ((clanHall.getGrade() == 1) && (function.getLevel() < 160))
                || ((clanHall.getGrade() == 2) && (function.getLevel() < 260))
                || ((clanHall.getGrade() == 3) && (function.getLevel() < 300))) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }

        // Carpet - Statue
        function = clanHall.getFunction(ClanHall.FUNC_RESTORE_MP);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
            buffer.writeByte(0);
        } else if ((((clanHall.getGrade() == 0) || (clanHall.getGrade() == 1)) && (function.getLevel() < 25))
                || ((clanHall.getGrade() == 2) && (function.getLevel() < 30))
                || ((clanHall.getGrade() == 3) && (function.getLevel() < 40))) {
            buffer.writeByte(1);
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
            buffer.writeByte(2);
        }

        // Chandelier
        function = clanHall.getFunction(ClanHall.FUNC_RESTORE_EXP);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (((clanHall.getGrade() == 0) && (function.getLevel() < 25))
                || ((clanHall.getGrade() == 1) && (function.getLevel() < 30))
                || ((clanHall.getGrade() == 2) && (function.getLevel() < 40))
                || ((clanHall.getGrade() == 3) && (function.getLevel() < 50))) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }

        // Mirror
        function = clanHall.getFunction(ClanHall.FUNC_TELEPORT);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (function.getLevel() < 2) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }

        // Crystal
        buffer.writeByte(0);

        // Curtain
        function = clanHall.getFunction(ClanHall.FUNC_DECO_CURTAINS);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (function.getLevel() <= 1) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }

        // Magic Curtain
        function = clanHall.getFunction(ClanHall.FUNC_ITEM_CREATE);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (((clanHall.getGrade() == 0) && (function.getLevel() < 2)) || (function.getLevel() < 3)) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }

        // Support? - Flag
        function = clanHall.getFunction(ClanHall.FUNC_SUPPORT);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
            buffer.writeByte(0);
        } else if (((clanHall.getGrade() == 0) && (function.getLevel() < 2))
                || ((clanHall.getGrade() == 1) && (function.getLevel() < 4))
                || ((clanHall.getGrade() == 2) && (function.getLevel() < 5))
                || ((clanHall.getGrade() == 3) && (function.getLevel() < 8))) {
            buffer.writeByte(1);
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
            buffer.writeByte(2);
        }

        // Front platform
        function = clanHall.getFunction(ClanHall.FUNC_DECO_FRONTPLATEFORM);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (function.getLevel() <= 1) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }

        // Item create?
        function = clanHall.getFunction(ClanHall.FUNC_ITEM_CREATE);
        if ((function == null) || (function.getLevel() == 0)) {
            buffer.writeByte(0);
        } else if (((clanHall.getGrade() == 0) && (function.getLevel() < 2)) || (function.getLevel() < 3)) {
            buffer.writeByte(1);
        } else {
            buffer.writeByte(2);
        }
    }
}
