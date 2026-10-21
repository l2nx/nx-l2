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
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Luca Baldi
 */
public class EtcStatusUpdate extends ServerPacket {
    private final Player player;

    public EtcStatusUpdate(Player player) {
        this.player = player;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.ETC_STATUS_UPDATE.writeId(this, buffer);
        buffer.writeInt(player.getCharges()); // 1-7 increase force, level
        buffer.writeInt(player.getWeightPenalty()); // 1-4 weight penalty, level (1=50%, 2=66.6%, 3=80%, 4=100%)
        buffer.writeInt(
                player.getMessageRefusal() || player.isChatBanned() || player.isSilenceMode()); // 1 = block all chat
        buffer.writeInt(player.isInsideZone(ZoneId.DANGER_AREA)); // 1 = danger area
        buffer.writeInt(player.getExpertiseWeaponPenalty()); // Weapon Grade Penalty [1-4]
        buffer.writeInt(player.getExpertiseArmorPenalty()); // Armor Grade Penalty [1-4]
        buffer.writeInt(
                player.hasCharmOfCourage()); // 1 = charm of courage (allows resurrection on the same spot upon death
        // on the
        // siege battlefield)
        buffer.writeInt(
                player.getDeathPenaltyBuffLevel()); // 1-15 death penalty, level (combat ability decreased due to
        // death)
        buffer.writeInt(player.getChargedSouls());
    }
}
