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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Kerberos
 */
public class ExBrExtraUserInfo extends ServerPacket {
    /** Player object ID. */
    private final int objectId;
    /** Event abnormal visual effects map. */
    private final int abnormalVisualEffectsEvent;
    /** Lecture mark. */
    private final int lectureMark;

    public ExBrExtraUserInfo(Player player) {
        objectId = player.getObjectId();
        abnormalVisualEffectsEvent = player.getAbnormalVisualEffectEvent();
        lectureMark = 1; // TODO: Implement.
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_BR_EXTRA_USER_INFO.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(abnormalVisualEffectsEvent);
        buffer.writeByte(lectureMark);
    }
}
