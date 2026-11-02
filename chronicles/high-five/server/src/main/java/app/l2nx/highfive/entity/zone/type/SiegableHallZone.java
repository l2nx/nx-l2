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
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

/**
 * @author BiggBoss
 */
@SingleWriter
public class SiegableHallZone extends ClanHallZone {
    private ObjectArrayList<Location> challengerSpawns;

    public SiegableHallZone(int id) {
        super(id);
    }

    /** Read-only snapshot of copies; {@code null} without any. */
    public List<Location> getChallengerSpawns() {
        WorldPartition.checkOwner();
        return Location.copiesOf(challengerSpawns);
    }

    @Override
    public void parseLoc(int x, int y, int z, String type) {
        WorldPartition.checkOwner();
        if ((type != null) && type.equals("challenger")) {
            if (challengerSpawns == null) {
                challengerSpawns = new ObjectArrayList<>();
            }

            challengerSpawns.add(new Location(x, y, z));
        } else {
            super.parseLoc(x, y, z, type);
        }
    }

    public void banishNonSiegeParticipants() {
        WorldPartition.checkOwner();
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player player : players) {
                if (player.isInHideoutSiege()) {
                    player.teleToLocation(getBanishSpawnLoc(), true);
                }
            }
        }
    }
}
