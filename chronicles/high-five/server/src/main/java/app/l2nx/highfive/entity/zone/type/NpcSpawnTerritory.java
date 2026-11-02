/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.zone.ZoneForm;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Just dummy zone, needs only for geometry calculations
 * @author GKR, Mobius
 */
@SingleWriter
public class NpcSpawnTerritory {
    private final String name;

    private final ZoneForm territory;
    private ObjectArrayList<ZoneForm> bannedTerritories;

    public NpcSpawnTerritory(String name, ZoneForm territory) {
        this.name = name;
        this.territory = territory;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void addBannedTerritory(ZoneForm territory) {
        WorldPartition.checkOwner();
        if (bannedTerritories == null) {
            bannedTerritories = new ObjectArrayList<>(1);
        }

        bannedTerritories.add(territory);
    }

    /** Drops the banned parts, which the spawn data declares again on each load of it. */
    public void clearBannedTerritories() {
        WorldPartition.checkOwner();
        bannedTerritories = null;
    }

    public Location getRandomPoint() {
        WorldPartition.checkOwner();
        int count = 0; // Prevent infinite loop.
        Location location;
        int randomX;
        int randomY;
        int randomZ;

        if (bannedTerritories != null) {
            SEARCH:
            while (count++ < 100) {
                location = this.territory.getRandomPoint();
                randomX = location.getX();
                randomY = location.getY();
                randomZ = location.getZ();

                for (ZoneForm territory : bannedTerritories) {
                    if (territory.isInsideZone(randomX, randomY, randomZ)) {
                        continue SEARCH;
                    }
                }

                if (GeoEngine.getInstance().getHeight(randomX, randomY, randomZ) > this.territory.getHighZ()) {
                    continue;
                }

                return location;
            }

            count = 0;
            SEARCH_NO_GEO:
            while (count++ < 100) {
                location = this.territory.getRandomPoint();
                randomX = location.getX();
                randomY = location.getY();
                randomZ = location.getZ();

                for (ZoneForm territory : bannedTerritories) {
                    if (territory.isInsideZone(randomX, randomY, randomZ)) {
                        continue SEARCH_NO_GEO;
                    }
                }

                return location;
            }
        }

        count = 0;
        while (count++ < 100) {
            location = this.territory.getRandomPoint();
            randomX = location.getX();
            randomY = location.getY();
            randomZ = location.getZ();

            if (GeoEngine.getInstance().getHeight(randomX, randomY, randomZ) > this.territory.getHighZ()) {
                continue;
            }

            return location;
        }

        return this.territory.getRandomPoint();
    }

    public boolean isInsideZone(int x, int y, int z) {
        WorldPartition.checkOwner();
        return territory.isInsideZone(x, y, z);
    }

    /**
     * Tests containment on the XY plane only, ignoring the Z band.<br>
     * Banned territories are excluded, like getRandomPoint does.
     */
    public boolean isInsideZone(int x, int y) {
        WorldPartition.checkOwner();
        final int z = this.territory.getLowZ();
        if (!this.territory.isInsideZone(x, y, z)) {
            return false;
        }

        if (bannedTerritories != null) {
            for (ZoneForm territory : bannedTerritories) {
                if (territory.isInsideZone(x, y, z)) {
                    return false;
                }
            }
        }

        return true;
    }

    public int getHighZ() {
        WorldPartition.checkOwner();
        return territory.getHighZ();
    }

    public void visualizeZone(int z) {
        WorldPartition.checkOwner();
        territory.visualizeZone(z);
    }
}
