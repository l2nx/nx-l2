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
package app.l2nx.highfive.mechanics.skill.holders;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class EnchantSkillGroup {
    @Getter
    private final int id;

    @Getter
    private final List<EnchantSkillHolder> enchantGroupDetails = new ArrayList<>();

    public EnchantSkillGroup(int id) {
        this.id = id;
    }

    public void addEnchantDetail(EnchantSkillHolder detail) {
        enchantGroupDetails.add(detail);
    }

    public static class EnchantSkillHolder {
        @Getter
        private final int level;

        @Getter
        private final int adenaCost;

        @Getter
        private final int expCost;

        @Getter
        private final int spCost;

        private final byte[] rate;

        public EnchantSkillHolder(StatSet set) {
            level = set.getInt("level");
            adenaCost = set.getInt("adena", 0);
            expCost = set.getInt("exp", 0);
            spCost = set.getInt("sp", 0);
            rate = new byte[24];
            for (int i = 0; i < 24; i++) {
                rate[i] = set.getByte("chance" + (76 + i), (byte) 0);
            }
        }

        public byte getRate(Player ply) {
            return ply.getLevel() < 76 ? 0 : rate[ply.getLevel() - 76];
        }
    }
}
