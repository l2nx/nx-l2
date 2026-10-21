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

import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.mechanics.siege.manor.CropProcure;
import app.l2nx.highfive.mechanics.siege.manor.Seed;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author l3x
 */
public class ExShowCropSetting extends ServerPacket {
    private final int manorId;
    private final Set<Seed> seeds;
    private final Map<Integer, CropProcure> current = new HashMap<>();
    private final Map<Integer, CropProcure> next = new HashMap<>();

    public ExShowCropSetting(int manorId) {
        final CastleManorManager manor = CastleManorManager.getInstance();
        this.manorId = manorId;
        seeds = manor.getSeedsForCastle(this.manorId);
        for (Seed s : seeds) {
            // Current period
            CropProcure cp = manor.getCropProcure(manorId, s.getCropId(), false);
            if (cp != null) {
                current.put(s.getCropId(), cp);
            }

            // Next period
            cp = manor.getCropProcure(manorId, s.getCropId(), true);
            if (cp != null) {
                next.put(s.getCropId(), cp);
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_CROP_SETTING.writeId(this, buffer);
        buffer.writeInt(manorId); // manor id
        buffer.writeInt(seeds.size()); // size
        CropProcure cp;
        for (Seed s : seeds) {
            buffer.writeInt(s.getCropId()); // crop id
            buffer.writeInt(s.getLevel()); // seed level
            buffer.writeByte(1);
            buffer.writeInt(s.getReward(1)); // reward 1 id
            buffer.writeByte(1);
            buffer.writeInt(s.getReward(2)); // reward 2 id
            buffer.writeInt(s.getCropLimit()); // next sale limit
            buffer.writeInt(0); // ???
            buffer.writeInt(s.getCropMinPrice()); // min crop price
            buffer.writeInt(s.getCropMaxPrice()); // max crop price

            // Current period
            if (current.containsKey(s.getCropId())) {
                cp = current.get(s.getCropId());
                buffer.writeLong(cp.getStartAmount()); // buy
                buffer.writeLong(cp.getPrice()); // price
                buffer.writeByte(cp.getReward()); // reward
            } else {
                buffer.writeLong(0);
                buffer.writeLong(0);
                buffer.writeByte(0);
            }

            // Next period
            if (next.containsKey(s.getCropId())) {
                cp = next.get(s.getCropId());
                buffer.writeLong(cp.getStartAmount()); // buy
                buffer.writeLong(cp.getPrice()); // price
                buffer.writeByte(cp.getReward()); // reward
            } else {
                buffer.writeLong(0);
                buffer.writeLong(0);
                buffer.writeByte(0);
            }
        }

        next.clear();
        current.clear();
    }
}
