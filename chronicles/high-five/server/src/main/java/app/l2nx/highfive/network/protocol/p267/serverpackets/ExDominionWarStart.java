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
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author JIV
 */
public class ExDominionWarStart extends ServerPacket {
    private final int objId;
    private final int terId;
    private final boolean disguised;

    public ExDominionWarStart(Player player) {
        objId = player.getObjectId();
        terId = TerritoryWarManager.getInstance().getRegisteredTerritoryId(player);
        disguised = TerritoryWarManager.getInstance().isDisguised(objId);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_DOMINION_WAR_START.writeId(this, buffer);
        buffer.writeInt(objId);
        buffer.writeInt(1); // ??
        buffer.writeInt(terId);
        buffer.writeInt(disguised);
        buffer.writeInt(disguised ? terId : 0);
    }
}
