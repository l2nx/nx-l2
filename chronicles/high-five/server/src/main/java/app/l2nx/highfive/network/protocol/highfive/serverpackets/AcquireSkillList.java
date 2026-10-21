/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.mechanics.skill.enums.AcquireSkillType;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Acquire Skill List server packet implementation.
 */
public class AcquireSkillList extends ServerPacket {
    private final List<Skill> skills;
    private final AcquireSkillType skillType;

    /**
     * Private class containing learning skill information.
     */
    private static class Skill {
        public int id;
        public int nextLevel;
        public int maxLevel;
        public int spCost;
        public int requirements;

        public Skill(int pId, int pNextLevel, int pMaxLevel, int pSpCost, int pRequirements) {
            id = pId;
            nextLevel = pNextLevel;
            maxLevel = pMaxLevel;
            spCost = pSpCost;
            requirements = pRequirements;
        }
    }

    public AcquireSkillList(AcquireSkillType type) {
        skillType = type;
        skills = new ArrayList<>();
    }

    public void addSkill(int id, int nextLevel, int maxLevel, int spCost, int requirements) {
        skills.add(new Skill(id, nextLevel, maxLevel, spCost, requirements));
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        if (skills.isEmpty()) {
            return;
        }

        ServerPackets.ACQUIRE_SKILL_LIST.writeId(this, buffer);
        buffer.writeInt(skillType.ordinal());
        buffer.writeInt(skills.size());
        for (Skill temp : skills) {
            buffer.writeInt(temp.id);
            buffer.writeInt(temp.nextLevel);
            buffer.writeInt(temp.maxLevel);
            buffer.writeInt(temp.spCost);
            buffer.writeInt(temp.requirements);
            if (skillType == AcquireSkillType.SUBPLEDGE) {
                buffer.writeInt(0); // TODO: ?
            }
        }
    }
}
