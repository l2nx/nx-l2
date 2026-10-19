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

import app.l2nx.highfive.entity.actor.Player;

/**
 * Task dedicated to put player to stand up.
 * @author UnAfraid
 */
public class StandUpTask implements Runnable {
    private final Player player;

    public StandUpTask(Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        if (player == null) {
            return;
        }

        player.setParalyzed(false);
        player.setSitting(false);
        player.setSittingProgress(false);
        player.getAI().setIntentionIdle();
    }
}
