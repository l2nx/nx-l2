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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillGroup.EnchantSkillHolder;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillLearn;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.List;

public class ExEnchantSkillInfo extends ServerPacket {
    private final List<Integer> routes = new ArrayList<>(); // skill levels for each route
    private final int id;
    private final int level;
    private boolean maxEnchanted = false;

    public ExEnchantSkillInfo(int id, int level) {
        this.id = id;
        this.level = level;
        final EnchantSkillLearn enchantLearn =
                EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(this.id);

        // do we have this skill?
        if (enchantLearn != null) {
            // skill already enchanted?
            if (this.level > 100) {
                maxEnchanted = enchantLearn.isMaxEnchant(this.level);

                // get detail for next level
                final EnchantSkillHolder esd = enchantLearn.getEnchantSkillHolder(this.level);

                // if it exists add it
                if (esd != null) {
                    routes.add(this.level); // current enchant add firts
                }

                final int skillLevel = (this.level % 100);
                for (int route : enchantLearn.getAllRoutes()) {
                    if (((route * 100) + skillLevel) == this.level) {
                        continue;
                    }

                    // add other levels of all routes - same level as enchanted
                    // level
                    routes.add((route * 100) + skillLevel);
                }
            } else
            // not already enchanted
            {
                for (int route : enchantLearn.getAllRoutes()) {
                    // add first level (+1) of all routes
                    routes.add((route * 100) + 1);
                }
            }
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_ENCHANT_SKILL_INFO.writeId(this, buffer);
        buffer.writeInt(id);
        buffer.writeInt(this.level);
        buffer.writeInt(!maxEnchanted);
        buffer.writeInt(this.level > 100); // enchanted?
        buffer.writeInt(routes.size());
        for (int level : routes) {
            buffer.writeInt(level);
        }
    }
}
