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

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.util.LocationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zabbix
 */
@Slf4j
public class RequestLinkHtml extends ClientPacket {
    private String link;

    @Override
    protected void readImpl() {
        link = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (link.isEmpty()) {
            log.warn("{} sent empty html link!", player);
            return;
        }

        if (link.contains("..")) {
            log.warn("{} sent invalid html link: link {}", player, link);
            return;
        }

        final int htmlObjectId = player.validateHtmlAction("link " + link);
        if (htmlObjectId == -1) {
            log.warn("{} sent non cached html link: link {}", player, link);
            return;
        }

        if ((htmlObjectId > 0)
                && !LocationUtil.isInsideRangeOfObjectId(player, htmlObjectId, Npc.INTERACTION_DISTANCE)) {
            // No logging here, this could be a common case.
            return;
        }

        final String filename = "data/html/" + link;
        final NpcHtmlMessage msg = new NpcHtmlMessage(htmlObjectId);
        msg.setFile(player, filename);
        player.sendPacket(msg);
    }
}
