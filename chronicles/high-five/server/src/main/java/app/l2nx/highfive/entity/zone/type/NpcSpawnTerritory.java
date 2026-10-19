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

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.zone.ZoneForm;
import app.l2nx.highfive.geoengine.GeoEngine;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * Just dummy zone, needs only for geometry calculations
 * @author GKR, Mobius
 */
public class NpcSpawnTerritory {
    @Getter
    private final String name;

    private final ZoneForm territory;
    private List<ZoneForm> bannedTerritories;

    public NpcSpawnTerritory(String name, ZoneForm territory) {
        this.name = name;
        this.territory = territory;
    }

    public void addBannedTerritory(ZoneForm territory) {
        if (bannedTerritories == null) {
            bannedTerritories = new ArrayList<>(1);
        }

        bannedTerritories.add(territory);
    }

    public Location getRandomPoint() {
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
        return territory.isInsideZone(x, y, z);
    }

    /**
     * Tests containment on the XY plane only, ignoring the Z band.<br>
     * Banned territories are excluded, like getRandomPoint does.
     * @param x
     * @param y
     * @return {@code true} if the point lies inside the territory polygon
     */
    public boolean isInsideZone(int x, int y) {
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
        return territory.getHighZ();
    }

    public void visualizeZone(int z) {
        territory.visualizeZone(z);
    }
}
