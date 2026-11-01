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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author NightMarez
 */
@SingleWriter
public class BroadcastingTower extends Npc {
    public BroadcastingTower(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.BroadcastingTower);
    }

    @Override
    public void showChatWindow(Player player, int value) {
        WorldPartition.checkOwner();
        String filename = null;
        if (isInsideRadius2D(-79884, 86529, 0, 50)
                || isInsideRadius2D(-78858, 111358, 0, 50)
                || isInsideRadius2D(-76973, 87136, 0, 50)
                || isInsideRadius2D(-75850, 111968, 0, 50)) {
            if (value == 0) {
                filename = "data/html/observation/" + getId() + "-Oracle.htm";
            } else {
                filename = "data/html/observation/" + getId() + "-Oracle-" + value + ".htm";
            }
        } else {
            if (value == 0) {
                filename = "data/html/observation/" + getId() + ".htm";
            } else {
                filename = "data/html/observation/" + getId() + "-" + value + ".htm";
            }
        }

        final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
        html.setFile(player, filename);
        html.replace("%objectId%", String.valueOf(getObjectId()));
        player.sendPacket(html);
    }
}
