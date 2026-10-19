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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.data.xml.EnchantSkillGroupsData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.serverpackets.ExEnchantSkillInfo;

/**
 * Format (ch) dd c: (id) 0xD0 h: (subid) 0x06 d: skill id d: skill level
 * @author -Wooden-
 */
public class RequestExEnchantSkillInfo extends ClientPacket {
    private int skillId;
    private int skillLevel;

    @Override
    protected void readImpl() {
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

        if (player.getLevel() < 76) {
            return;
        }

        final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
        if ((skill == null) || (skill.getId() != skillId)) {
            return;
        }

        if (EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(skillId) == null) {
            return;
        }

        final int playerskillLevel = player.getSkillLevel(skillId);
        if ((playerskillLevel == -1) || (playerskillLevel != skillLevel)) {
            return;
        }

        player.sendPacket(new ExEnchantSkillInfo(skillId, skillLevel));
    }
}
