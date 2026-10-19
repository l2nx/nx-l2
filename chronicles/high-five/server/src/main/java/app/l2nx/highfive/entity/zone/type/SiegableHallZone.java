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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * @author BiggBoss
 */
public class SiegableHallZone extends ClanHallZone {
    @Getter
    private List<Location> challengerSpawns;

    public SiegableHallZone(int id) {
        super(id);
    }

    @Override
    public void parseLoc(int x, int y, int z, String type) {
        if ((type != null) && type.equals("challenger")) {
            if (challengerSpawns == null) {
                challengerSpawns = new ArrayList<>();
            }

            challengerSpawns.add(new Location(x, y, z));
        } else {
            super.parseLoc(x, y, z, type);
        }
    }

    public void banishNonSiegeParticipants() {
        for (Player player : getPlayersInside()) {
            if ((player != null) && player.isInHideoutSiege()) {
                player.teleToLocation(getBanishSpawnLoc(), true);
            }
        }
    }
}
