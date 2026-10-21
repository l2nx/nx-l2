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
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author godson
 */
public class ExOlympiadSpelledInfo extends ServerPacket {
    private final int playerId;
    private final List<BuffInfo> effects = new ArrayList<>();

    public ExOlympiadSpelledInfo(Player player) {
        playerId = player.getObjectId();
    }

    public void addSkill(BuffInfo info) {
        effects.add(info);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_OLYMPIAD_SPELLED_INFO.writeId(this, buffer);
        buffer.writeInt(playerId);
        buffer.writeInt(effects.size());
        for (BuffInfo info : effects) {
            if ((info != null) && info.isInUse()) {
                buffer.writeInt(info.getSkill().getDisplayId());
                buffer.writeShort(info.getSkill().getDisplayLevel());
                buffer.writeInt(info.getTime());
            }
        }
    }
}
