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
import app.l2nx.highfive.mechanics.siege.manor.SeedProduction;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author l3x
 */
public class ExShowSeedSetting extends ServerPacket {
    private final int manorId;
    private final Set<Seed> seeds;
    private final Map<Integer, SeedProduction> current = new HashMap<>();
    private final Map<Integer, SeedProduction> next = new HashMap<>();

    public ExShowSeedSetting(int manorId) {
        final CastleManorManager manor = CastleManorManager.getInstance();
        this.manorId = manorId;
        seeds = manor.getSeedsForCastle(this.manorId);
        for (Seed s : seeds) {
            // Current period
            SeedProduction sp = manor.getSeedProduct(manorId, s.getSeedId(), false);
            if (sp != null) {
                current.put(s.getSeedId(), sp);
            }

            // Next period
            sp = manor.getSeedProduct(manorId, s.getSeedId(), true);
            if (sp != null) {
                next.put(s.getSeedId(), sp);
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_SEED_SETTING.writeId(this, buffer);
        buffer.writeInt(manorId); // manor id
        buffer.writeInt(seeds.size()); // size
        SeedProduction sp;
        for (Seed s : seeds) {
            buffer.writeInt(s.getSeedId()); // seed id
            buffer.writeInt(s.getLevel()); // level
            buffer.writeByte(1);
            buffer.writeInt(s.getReward(1)); // reward 1 id
            buffer.writeByte(1);
            buffer.writeInt(s.getReward(2)); // reward 2 id
            buffer.writeInt(s.getSeedLimit()); // next sale limit
            buffer.writeInt(s.getSeedReferencePrice()); // price for castle to produce 1
            buffer.writeInt(s.getSeedMinPrice()); // min seed price
            buffer.writeInt(s.getSeedMaxPrice()); // max seed price

            // Current period
            if (current.containsKey(s.getSeedId())) {
                sp = current.get(s.getSeedId());
                buffer.writeLong(sp.getStartAmount()); // sales
                buffer.writeLong(sp.getPrice()); // price
            } else {
                buffer.writeLong(0);
                buffer.writeLong(0);
            }

            // Next period
            if (next.containsKey(s.getSeedId())) {
                sp = next.get(s.getSeedId());
                buffer.writeLong(sp.getStartAmount()); // sales
                buffer.writeLong(sp.getPrice()); // price
            } else {
                buffer.writeLong(0);
                buffer.writeLong(0);
            }
        }

        current.clear();
        next.clear();
    }
}
