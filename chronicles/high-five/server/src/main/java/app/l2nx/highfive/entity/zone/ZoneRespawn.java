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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.Location;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * Abstract zone with spawn locations
 * @author DS, Nyaran
 */
@Slf4j
public abstract class ZoneRespawn extends ZoneType {
    @Getter
    private List<Location> spawns = null;

    private List<Location> otherSpawnLocs = null;
    private List<Location> chaoticSpawnLocs = null;
    private List<Location> banishSpawnLocs = null;

    protected ZoneRespawn(int id) {
        super(id);
    }

    public void parseLoc(int x, int y, int z, String type) {
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
        if (spawns == null) {
            spawns = new ArrayList<>();
        }

        spawns.add(new Location(x, y, z));
    }

    public void addOtherSpawn(int x, int y, int z) {
        if (otherSpawnLocs == null) {
            otherSpawnLocs = new ArrayList<>();
        }

        otherSpawnLocs.add(new Location(x, y, z));
    }

    public void addChaoticSpawn(int x, int y, int z) {
        if (chaoticSpawnLocs == null) {
            chaoticSpawnLocs = new ArrayList<>();
        }

        chaoticSpawnLocs.add(new Location(x, y, z));
    }

    public void addBanishSpawn(int x, int y, int z) {
        if (banishSpawnLocs == null) {
            banishSpawnLocs = new ArrayList<>();
        }

        banishSpawnLocs.add(new Location(x, y, z));
    }

    public Location getSpawnLoc() {
        if (PlayerConfig.RANDOM_RESPAWN_IN_TOWN_ENABLED) {
            return spawns.get(ClockBridge.random().nextInt(spawns.size()));
        }

        return spawns.get(0);
    }

    public Location getOtherSpawnLoc() {
        if (otherSpawnLocs != null) {
            if (PlayerConfig.RANDOM_RESPAWN_IN_TOWN_ENABLED) {
                return otherSpawnLocs.get(ClockBridge.random().nextInt(otherSpawnLocs.size()));
            }

            return otherSpawnLocs.get(0);
        }

        return getSpawnLoc();
    }

    public Location getChaoticSpawnLoc() {
        if (chaoticSpawnLocs != null) {
            if (PlayerConfig.RANDOM_RESPAWN_IN_TOWN_ENABLED) {
                return chaoticSpawnLocs.get(ClockBridge.random().nextInt(chaoticSpawnLocs.size()));
            }

            return chaoticSpawnLocs.get(0);
        }

        return getSpawnLoc();
    }

    public Location getBanishSpawnLoc() {
        if (banishSpawnLocs != null) {
            if (PlayerConfig.RANDOM_RESPAWN_IN_TOWN_ENABLED) {
                return banishSpawnLocs.get(ClockBridge.random().nextInt(banishSpawnLocs.size()));
            }

            return banishSpawnLocs.get(0);
        }

        return getSpawnLoc();
    }
}
