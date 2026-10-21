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
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ValidateLocation;
import app.l2nx.highfive.util.LocationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Format:(ch) dddddc
 * @author -Wooden-
 */
@Slf4j
public class RequestExMagicSkillUseGround extends ClientPacket {
    private int x;
    private int y;
    private int z;
    private int skillId;
    private boolean ctrlPressed;
    private boolean shiftPressed;

    @Override
    protected void readImpl() {
        x = readInt();
        y = readInt();
        z = readInt();
        skillId = readInt();
        ctrlPressed = readInt() != 0;
        shiftPressed = readByte() != 0;
    }

    @Override
    protected void runImpl() {
        // Get the current Player of the player.
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Get the level of the used skill.
        final int level = player.getSkillLevel(skillId);
        if (level <= 0) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Get the Skill template corresponding to the skillID received from the client.
        final Skill skill = SkillData.getInstance().getSkill(skillId, level);

        // Check the validity of the skill.
        if (skill != null) {
            player.setCurrentSkillWorldPosition(new Location(x, y, z));

            // Normally magicskilluse packet turns char client side but for these skills, it doesn't (even with correct
            // target).
            player.setHeading(LocationUtil.calculateHeadingFrom(player.getX(), player.getY(), x, y));
            World.broadcastToVisiblePlayers(player, new ValidateLocation(player));
            player.useMagic(skill, ctrlPressed, shiftPressed);
        } else {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            log.warn("No skill found with id {} and level {} !!", skillId, level);
        }
    }
}
