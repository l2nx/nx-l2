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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;

/**
 * @author KenM
 */
public class RequestDispel extends ClientPacket {
    private int objectId;
    private int skillId;
    private int skillLevel;

    @Override
    protected void readImpl() {
        objectId = readInt();
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

        final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
        if (skill == null) {
            return;
        }

        if (!skill.canBeDispeled() || skill.isStayAfterDeath() || skill.isDebuff()) {
            return;
        }

        if (skill.getAbnormalType() == AbnormalType.TRANSFORM) {
            return;
        }

        if (skill.isDance() && !PlayerConfig.DANCE_CANCEL_BUFF) {
            return;
        }

        if (player.getObjectId() == objectId) {
            player.stopSkillEffects(SkillFinishType.REMOVED, skillId);
        } else {
            if (player.hasSummon() && (player.getSummon().getObjectId() == objectId)) {
                player.getSummon().stopSkillEffects(SkillFinishType.REMOVED, skillId);
            }
        }
    }
}
