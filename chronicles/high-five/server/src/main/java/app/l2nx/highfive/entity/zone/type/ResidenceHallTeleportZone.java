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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Teleport residence zone for clan hall sieges
 * @author BiggBoss
 */
@SingleWriter
public class ResidenceHallTeleportZone extends ResidenceTeleportZone {
    private int residenceZoneId;

    private ScheduledFuture<?> teleTask;

    public ResidenceHallTeleportZone(int id) {
        super(id);
    }

    public int getResidenceZoneId() {
        WorldPartition.checkOwner();
        return residenceZoneId;
    }

    @Override
    public void setParameter(String name, String value) {
        WorldPartition.checkOwner();
        if (name.equals("residenceZoneId")) {
            residenceZoneId = Integer.parseInt(value);
        } else {
            super.setParameter(name, value);
        }
    }

    public void checkTeleportTask() {
        WorldPartition.checkOwner();
        if ((teleTask == null) || teleTask.isDone()) {
            teleTask = WorldPartition.loop().schedule(new TeleportTask(), 30000, TimeUnit.MILLISECONDS);
        }
    }

    private class TeleportTask implements Runnable {
        @Override
        public void run() {
            final List<Location> spawns = getSpawns();
            final int index = spawns.size() > 1 ? ClockBridge.random().nextInt(spawns.size()) : 0;
            final Location loc = spawns.get(index);
            if (loc == null) {
                throw new NullPointerException();
            }

            try (Snapshot<Player> players = ScratchBuffers.empty()) {
                collectPlayersInside(players);
                for (Player pc : players) {
                    pc.teleToLocation(loc, false);
                }
            }
        }
    }
}
