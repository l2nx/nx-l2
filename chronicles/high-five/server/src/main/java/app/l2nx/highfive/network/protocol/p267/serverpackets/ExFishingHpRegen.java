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
public class ExFishingHpRegen extends ServerPacket {
    private final Creature creature;
    private final int time;
    private final int fishHP;
    private final int hpMode;
    private final int anim;
    private final int goodUse;
    private final int penalty;
    private final int hpBarColor;

    public ExFishingHpRegen(
            Creature creature, int time, int fishHP, int hpMode, int goodUse, int anim, int penalty, int hpBarColor) {
        this.creature = creature;
        this.time = time;
        this.fishHP = fishHP;
        this.hpMode = hpMode;
        this.goodUse = goodUse;
        this.anim = anim;
        this.penalty = penalty;
        this.hpBarColor = hpBarColor;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_FISHING_HP_REGEN.writeId(this, buffer);
        buffer.writeInt(creature.getObjectId());
        buffer.writeInt(time);
        buffer.writeInt(fishHP);
        buffer.writeByte(hpMode); // 0 = HP stop, 1 = HP raise
        buffer.writeByte(goodUse); // 0 = none, 1 = success, 2 = failed
        buffer.writeByte(anim); // Anim: 0 = none, 1 = reeling, 2 = pumping
        buffer.writeInt(penalty); // Penalty
        buffer.writeByte(hpBarColor); // 0 = normal hp bar, 1 = purple hp bar
    }
}
