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

import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.FortSiege;
import app.l2nx.highfive.mechanics.siege.FortSpawnHolder;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.List;

/**
 * @author KenM
 */
public class ExShowFortressMapInfo extends ServerPacket {
    private final Fort fortress;
    private final FortSiege siege;
    private final List<FortSpawnHolder> commanders;

    public ExShowFortressMapInfo(Fort fortress) {
        this.fortress = fortress;
        siege = fortress.getSiege();
        commanders = FortSiegeManager.getInstance().getCommanderSpawnList(fortress.getResidenceId());
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_FORTRESS_MAP_INFO.writeId(this, buffer);
        buffer.writeInt(fortress.getResidenceId());
        buffer.writeInt(siege.isInProgress()); // fortress siege status
        buffer.writeInt(fortress.getFortSize()); // barracks count
        if ((commanders != null) && !commanders.isEmpty() && siege.isInProgress()) {
            switch (commanders.size()) {
                case 3: {
                    for (FortSpawnHolder spawn : commanders) {
                        if (isSpawned(spawn.getId())) {
                            buffer.writeInt(0);
                        } else {
                            buffer.writeInt(1);
                        }
                    }
                    break;
                }
                case 4: // TODO: change 4 to 5 once control room supported
                {
                    int count = 0;
                    for (FortSpawnHolder spawn : commanders) {
                        count++;
                        if (count == 4) {
                            buffer.writeInt(1); // TODO: control room emulated
                        }

                        if (isSpawned(spawn.getId())) {
                            buffer.writeInt(0);
                        } else {
                            buffer.writeInt(1);
                        }
                    }
                    break;
                }
            }
        } else {
            for (int i = 0; i < fortress.getFortSize(); i++) {
                buffer.writeInt(0);
            }
        }
    }

    private boolean isSpawned(int npcId) {
        boolean ret = false;
        for (Spawn spawn : siege.getCommanders()) {
            if (spawn.getId() == npcId) {
                ret = true;
                break;
            }
        }

        return ret;
    }
}
