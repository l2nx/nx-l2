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
package app.l2nx.highfive.entity.zone;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Abstract zone with spawn locations
 * @author DS, Nyaran
 */
@SingleWriter
public abstract class ZoneRespawn extends ZoneType {
    private ObjectArrayList<Location> spawns = null;

    private ObjectArrayList<Location> otherSpawnLocs = null;
    private ObjectArrayList<Location> chaoticSpawnLocs = null;
    private ObjectArrayList<Location> banishSpawnLocs = null;

    protected ZoneRespawn(int id) {
        super(id);
    }

    protected ZoneRespawn(int id, AbstractZoneSettings settings) {
        super(id, settings);
    }

    /** Copies, so a caller that adjusts a spawn leaves the zone's own; {@code null} without any. */
    public List<Location> getSpawns() {
        WorldPartition.checkOwner();
        return Location.copiesOf(spawns);
    }

    public void parseLoc(int x, int y, int z, String type) {
        WorldPartition.checkOwner();
        if ((type == null) || type.isEmpty()) {
            addSpawn(x, y, z);
        } else {
            switch (type) {
                case "other": {
                    addOtherSpawn(x, y, z);
                    break;
                }
                case "chaotic": {
                    addChaoticSpawn(x, y, z);
                    break;
                }
                case "banish": {
                    addBanishSpawn(x, y, z);
                    break;
                }
                default: {
                    LoggerFactory.getLogger(getClass()).warn("Unknown location type: {}", type);
                }
            }
        }
    }

    public void addSpawn(int x, int y, int z) {
        WorldPartition.checkOwner();
        if (spawns == null) {
            spawns = new ObjectArrayList<>();
        }

        spawns.add(new Location(x, y, z));
    }

    public void addOtherSpawn(int x, int y, int z) {
        WorldPartition.checkOwner();
        if (otherSpawnLocs == null) {
            otherSpawnLocs = new ObjectArrayList<>();
        }

        otherSpawnLocs.add(new Location(x, y, z));
    }

    public void addChaoticSpawn(int x, int y, int z) {
        WorldPartition.checkOwner();
        if (chaoticSpawnLocs == null) {
            chaoticSpawnLocs = new ObjectArrayList<>();
        }

        chaoticSpawnLocs.add(new Location(x, y, z));
    }

    public void addBanishSpawn(int x, int y, int z) {
        WorldPartition.checkOwner();
        if (banishSpawnLocs == null) {
            banishSpawnLocs = new ObjectArrayList<>();
        }

        banishSpawnLocs.add(new Location(x, y, z));
    }

    public Location getSpawnLoc() {
        WorldPartition.checkOwner();
        return pick(spawns);
    }

    public Location getOtherSpawnLoc() {
        WorldPartition.checkOwner();
        return pick(otherSpawnLocs != null ? otherSpawnLocs : spawns);
    }

    public Location getChaoticSpawnLoc() {
        WorldPartition.checkOwner();
        return pick(chaoticSpawnLocs != null ? chaoticSpawnLocs : spawns);
    }

    public Location getBanishSpawnLoc() {
        WorldPartition.checkOwner();
        return pick(banishSpawnLocs != null ? banishSpawnLocs : spawns);
    }

    // A copy: callers set the instance id or heading on the location they teleport to.
    private static Location pick(List<Location> locations) {
        if (PlayerConfig.RANDOM_RESPAWN_IN_TOWN_ENABLED) {
            return locations.get(ClockBridge.random().nextInt(locations.size())).clone();
        }

        return locations.get(0).clone();
    }
}
