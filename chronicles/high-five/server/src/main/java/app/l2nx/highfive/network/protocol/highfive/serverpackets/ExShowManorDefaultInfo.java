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

import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.mechanics.siege.manor.Seed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.List;

/**
 * @author l3x
 */
public class ExShowManorDefaultInfo extends ServerPacket {
    private final List<Seed> crops;
    private final boolean hideButtons;

    public ExShowManorDefaultInfo(boolean hideButtons) {
        crops = CastleManorManager.getInstance().getCrops();
        this.hideButtons = hideButtons;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_MANOR_DEFAULT_INFO.writeId(this, buffer);
        buffer.writeByte(hideButtons); // Hide "Seed Purchase" and "Crop Sales" buttons
        buffer.writeInt(crops.size());
        for (Seed crop : crops) {
            buffer.writeInt(crop.getCropId()); // crop Id
            buffer.writeInt(crop.getLevel()); // level
            buffer.writeInt(crop.getSeedReferencePrice()); // seed price
            buffer.writeInt(crop.getCropReferencePrice()); // crop price
            buffer.writeByte(1); // Reward 1 type
            buffer.writeInt(crop.getReward(1)); // Reward 1 itemId
            buffer.writeByte(1); // Reward 2 type
            buffer.writeInt(crop.getReward(2)); // Reward 2 itemId
        }
    }
}
