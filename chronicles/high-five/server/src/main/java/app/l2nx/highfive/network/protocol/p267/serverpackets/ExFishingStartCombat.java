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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author -Wooden-
 */
public class ExFishingStartCombat extends ServerPacket {
    private final Creature creature;
    private final int time;
    private final int hp;
    private final int lureType;
    private final int deceptiveMode;
    private final int mode;

    public ExFishingStartCombat(Creature creature, int time, int hp, int mode, int lureType, int deceptiveMode) {
        this.creature = creature;
        this.time = time;
        this.hp = hp;
        this.mode = mode;
        this.lureType = lureType;
        this.deceptiveMode = deceptiveMode;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_FISHING_START_COMBAT.writeId(this, buffer);
        buffer.writeInt(creature.getObjectId());
        buffer.writeInt(time);
        buffer.writeInt(hp);
        buffer.writeByte(mode); // mode: 0 = resting, 1 = fighting
        buffer.writeByte(lureType); // 0 = newbie lure, 1 = normal lure, 2 = night lure
        buffer.writeByte(deceptiveMode); // Fish Deceptive Mode: 0 = no, 1 = yes
    }
}
