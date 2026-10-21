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
package app.l2nx.highfive.entity.actor.tasks.player;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;

/**
 * Task dedicated to reward player with fame while standing on siege zone.
 * @author UnAfraid
 */
public class FameTask implements Runnable {
    private final Player player;
    private final int value;

    public FameTask(Player player, int value) {
        this.player = player;
        this.value = value;
    }

    @Override
    public void run() {
        if ((player == null) || (player.isDead() && !PlayerConfig.FAME_FOR_DEAD_PLAYERS)) {
            return;
        }

        if (((player.getClient() == null) || player.getClient().isDetached()) && !OfflineTradeConfig.OFFLINE_FAME) {
            return;
        }

        player.setFame(player.getFame() + value);
        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_REPUTATION);
        sm.addInt(value);
        player.sendPacket(sm);
        player.updateUserInfo();
    }
}
