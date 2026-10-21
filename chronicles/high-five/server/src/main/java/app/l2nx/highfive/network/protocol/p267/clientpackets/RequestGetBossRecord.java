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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.RaidBossPointsManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetBossRecord;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Format: (ch) d
 * @author -Wooden-
 */
@Slf4j
public class RequestGetBossRecord extends ClientPacket {
    private int bossId;

    @Override
    protected void readImpl() {
        bossId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (bossId != 0) {
            log.info(
                    "C5: RequestGetBossRecord: d: {} ActiveChar: {}",
                    bossId,
                    player); // should be always 0, log it if is not 0 for furture research
        }

        final int points = RaidBossPointsManager.getInstance().getPointsByOwnerId(player.getObjectId());
        final int ranking = RaidBossPointsManager.getInstance().calculateRanking(player.getObjectId());
        final Map<Integer, Integer> list = RaidBossPointsManager.getInstance().getList(player);

        // trigger packet
        player.sendPacket(new ExGetBossRecord(ranking, points, list));
    }
}
