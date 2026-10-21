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

import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author KenM
 */
public class ExPartyPetWindowUpdate extends ServerPacket {
    private final Summon summon;

    public ExPartyPetWindowUpdate(Summon summon) {
        this.summon = summon;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_PARTY_PET_WINDOW_UPDATE.writeId(this, buffer);
        buffer.writeInt(summon.getObjectId());
        buffer.writeInt(summon.getTemplate().getDisplayId() + 1000000);
        buffer.writeInt(summon.getSummonType());
        buffer.writeInt(summon.getOwner().getObjectId());
        buffer.writeString(summon.getName());
        buffer.writeInt((int) summon.getCurrentHp());
        buffer.writeInt(summon.getMaxHp());
        buffer.writeInt((int) summon.getCurrentMp());
        buffer.writeInt(summon.getMaxMp());
        buffer.writeInt(summon.getLevel());
    }
}
