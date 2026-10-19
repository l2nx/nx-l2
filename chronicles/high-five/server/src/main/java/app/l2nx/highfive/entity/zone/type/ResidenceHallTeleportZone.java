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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;

/**
 * Teleport residence zone for clan hall sieges
 * @author BiggBoss
 */
public class ResidenceHallTeleportZone extends ResidenceTeleportZone {
    @Getter
    private int residenceZoneId;

    private ScheduledFuture<?> teleTask;

    /**
     * @param id
     */
    public ResidenceHallTeleportZone(int id) {
        super(id);
    }

    @Override
    public void setParameter(String name, String value) {
        if (name.equals("residenceZoneId")) {
            residenceZoneId = Integer.parseInt(value);
        } else {
            super.setParameter(name, value);
        }
    }

    public synchronized void checkTeleportTask() {
        if ((teleTask == null) || teleTask.isDone()) {
            teleTask = ThreadPool.schedule(new TeleportTask(), 30000);
        }
    }

    protected class TeleportTask implements Runnable {
        @Override
        public void run() {
            final int index = getSpawns().size() > 1
                    ? ThreadLocalRandom.current().nextInt(getSpawns().size())
                    : 0;
            final Location loc = getSpawns().get(index);
            if (loc == null) {
                throw new NullPointerException();
            }

            for (Player pc : getPlayersInside()) {
                if (pc != null) {
                    pc.teleToLocation(loc, false);
                }
            }
        }
    }
}
