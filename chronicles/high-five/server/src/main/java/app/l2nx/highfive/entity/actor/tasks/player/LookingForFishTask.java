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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Task dedicated for looking for fishes.
 * @author UnAfraid
 */
public class LookingForFishTask implements Runnable {
    private final Player player;
    private final boolean noob;
    private final boolean upperGrade;
    private final int fishGroup;
    private final double fishGutsCheck;
    private final long endTaskTime;

    public LookingForFishTask(
            Player player,
            int startCombatTime,
            double fishGutsCheck,
            int fishGroup,
            boolean isNoob,
            boolean isUpperGrade) {
        this.player = player;
        this.fishGutsCheck = fishGutsCheck;
        endTaskTime = System.currentTimeMillis() + (startCombatTime * 1000) + 10000;
        this.fishGroup = fishGroup;
        noob = isNoob;
        upperGrade = isUpperGrade;
    }

    @Override
    public void run() {
        if (player != null) {
            if (System.currentTimeMillis() >= endTaskTime) {
                player.endFishing(false);
                return;
            }

            if (fishGroup == -1) {
                return;
            }

            final int check = ThreadLocalRandom.current().nextInt(100);
            if (fishGutsCheck > check) {
                player.stopLookingForFishTask();
                player.startFishCombat(noob, upperGrade);
            }
        }
    }
}
