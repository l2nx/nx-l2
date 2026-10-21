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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.AcquireSkillType;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.AcquireSkillInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Request Acquire Skill Info client packet implementation.
 * @author Zoey76
 */
@Slf4j
public class RequestAcquireSkillInfo extends ClientPacket {
    private int id;
    private int level;
    private AcquireSkillType skillType;

    @Override
    protected void readImpl() {
        id = readInt();
        level = readInt();
        skillType = AcquireSkillType.getAcquireSkillType(readInt());
    }

    @Override
    protected void runImpl() {
        if ((id <= 0) || (level <= 0)) {
            log.warn("{}: Invalid Id: {} or level: {}!", RequestAcquireSkillInfo.class.getSimpleName(), id, level);
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final Npc trainer = player.getLastFolkNPC();
        if ((trainer == null) || !trainer.isNpc() || (!trainer.canInteract(player) && !player.isGM())) {
            return;
        }

        final Skill skill = SkillData.getInstance().getSkill(id, level);
        if (skill == null) {
            log.warn(
                    "{}: Skill Id: {} level: {} is undefined. {} failed.",
                    RequestAcquireSkillInfo.class.getSimpleName(),
                    id,
                    level,
                    RequestAcquireSkillInfo.class.getName());
            return;
        }

        final SkillLearn s = SkillTreeData.getInstance().getSkillLearn(skillType, id, level, player);
        if (s == null) {
            return;
        }

        switch (skillType) {
            case TRANSFORM:
            case FISHING:
            case SUBCLASS:
            case COLLECT:
            case TRANSFER: {
                player.sendPacket(new AcquireSkillInfo(skillType, s));
                break;
            }
            case CLASS: {
                if (trainer.getTemplate().canTeach(player.getLearningClass())) {
                    final int customSp = s.getCalculatedLevelUpSp(player.getPlayerClass(), player.getLearningClass());
                    player.sendPacket(new AcquireSkillInfo(skillType, s, customSp));
                }
                break;
            }
            case PLEDGE: {
                if (!player.isClanLeader()) {
                    return;
                }

                player.sendPacket(new AcquireSkillInfo(skillType, s));
                break;
            }
            case SUBPLEDGE: {
                if (!player.isClanLeader() || !player.hasAccess(ClanAccess.MEMBER_FAME)) {
                    return;
                }

                player.sendPacket(new AcquireSkillInfo(skillType, s));
                break;
            }
        }
    }
}
