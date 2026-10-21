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

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author VISTALL
 */
public class ExSubPledgeSkillAdd extends ServerPacket {
    private final int type;
    private final int skillId;
    private final int skillLevel;

    public ExSubPledgeSkillAdd(int type, int skillId, int skillLevel) {
        this.type = type;
        this.skillId = skillId;
        this.skillLevel = skillLevel;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SUB_PLEDGE_SKILL_ADD.writeId(this, buffer);
        buffer.writeInt(type);
        buffer.writeInt(skillId);
        buffer.writeInt(skillLevel);
    }
}
