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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.holders.EnchantSkillLearn;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExEnchantSkillInfoDetail;

/**
 * Format (ch) ddd c: (id) 0xD0 h: (subid) 0x31 d: type d: skill id d: skill level
 * @author -Wooden-
 */
public class RequestExEnchantSkillInfoDetail extends ClientPacket {
    private int type;
    private int skillId;
    private int skillLevel;

    @Override
    protected void readImpl() {
        type = readInt();
        skillId = readInt();
        skillLevel = readInt();
    }

    @Override
    protected void runImpl() {
        if ((skillId <= 0) || (skillLevel <= 0)) {
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        int reqskillLevel = -2;
        if ((type == 0) || (type == 1)) {
            reqskillLevel = skillLevel - 1; // enchant
        } else if (type == 2) {
            reqskillLevel = skillLevel + 1; // untrain
        } else if (type == 3) {
            reqskillLevel = skillLevel; // change route
        }

        final int playerskillLevel = player.getSkillLevel(skillId);

        // does not have such skill
        if (playerskillLevel == 0) {
            return;
        }

        // if reqlevel is 100,200,.. check base skill level enchant
        if ((reqskillLevel % 100) == 0) {
            final EnchantSkillLearn esl = EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(skillId);
            if (esl != null) {
                // if player does not have min level to enchant
                if (playerskillLevel != esl.getBaseLevel()) {
                    return;
                }
            }
            // enchant data does not exist?
            else {
                return;
            }
        }
        // change route is different skill level but same enchant
        else if ((playerskillLevel != reqskillLevel)
                && (type == 3)
                && ((playerskillLevel % 100) != (skillLevel % 100))) {
            return;
        }

        // send skill enchantment detail
        player.sendPacket(new ExEnchantSkillInfoDetail(type, skillId, skillLevel, player));
    }
}
