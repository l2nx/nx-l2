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

import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;

/**
 * @author -Wooden-
 */
public class PledgeSkillList extends ServerPacket {
    private final Collection<Skill> skills;
    private final Collection<SubPledgeSkill> subSkills;

    public static class SubPledgeSkill {
        int subType;
        int skillId;
        int skillLevel;

        public SubPledgeSkill(int subType, int skillId, int skillLevel) {
            this.subType = subType;
            this.skillId = skillId;
            this.skillLevel = skillLevel;
        }
    }

    public PledgeSkillList(Clan clan) {
        skills = clan.getAllSkills();
        subSkills = clan.getAllSubSkills();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PLEDGE_SKILL_LIST.writeId(this, buffer);
        buffer.writeInt(skills.size());
        buffer.writeInt(subSkills.size()); // Squad skill length
        for (Skill sk : skills) {
            buffer.writeInt(sk.getDisplayId());
            buffer.writeInt(sk.getDisplayLevel());
        }

        for (SubPledgeSkill sk : subSkills) {
            buffer.writeInt(sk.subType); // Clan Sub-unit types
            buffer.writeInt(sk.skillId);
            buffer.writeInt(sk.skillLevel);
        }
    }
}
