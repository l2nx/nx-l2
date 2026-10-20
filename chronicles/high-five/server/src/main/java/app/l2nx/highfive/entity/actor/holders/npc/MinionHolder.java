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
package app.l2nx.highfive.entity.actor.holders.npc;

import app.l2nx.highfive.clock.ClockBridge;
import lombok.Getter;

/**
 * This class hold info needed for minions spawns<br>
 * @author Zealar
 */
public class MinionHolder {
    @Getter
    private final int id;

    private final int count;
    private final int max;

    @Getter
    private final long respawnTime;

    @Getter
    private final int weightPoint;

    /**
     * Constructs a minion holder.
     * @param id the id
     * @param count the count
     * @param max the max count
     * @param respawnTime the respawn time
     * @param weightPoint the weight point
     */
    public MinionHolder(int id, int count, int max, long respawnTime, int weightPoint) {
        this.id = id;
        this.count = count;
        this.max = max;
        this.respawnTime = respawnTime;
        this.weightPoint = weightPoint;
    }

    /**
     * @return the count of the Minions to spawn.
     */
    public int getCount() {
        if (max > count) {
            return ClockBridge.random().nextInt(count, max + 1);
        }

        return count;
    }
}
