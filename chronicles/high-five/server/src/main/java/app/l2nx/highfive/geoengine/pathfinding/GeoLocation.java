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
package app.l2nx.highfive.geoengine.pathfinding;

import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.geoengine.geodata.Cell;
import lombok.Getter;

/**
 * @author Mobius
 */
public class GeoLocation {
    @Getter
    private int nodeX;

    @Getter
    private int nodeY;

    private int nswe;

    @Getter
    private int z;

    public GeoLocation(int x, int y, int z) {
        set(x, y, z);
    }

    public void set(int x, int y, int z) {
        nodeX = x;
        nodeY = y;
        nswe = 0; // Reset the bitmask.

        // Set the NSWE bitmask based on movement possibilities.
        final GeoEngine geoEngine = GeoEngine.getInstance();
        if (geoEngine.checkNearestNswe(x, y, z, Cell.NSWE_NORTH)) {
            nswe |= Cell.NSWE_NORTH;
        }

        if (geoEngine.checkNearestNswe(x, y, z, Cell.NSWE_EAST)) {
            nswe |= Cell.NSWE_EAST;
        }

        if (geoEngine.checkNearestNswe(x, y, z, Cell.NSWE_SOUTH)) {
            nswe |= Cell.NSWE_SOUTH;
        }

        if (geoEngine.checkNearestNswe(x, y, z, Cell.NSWE_WEST)) {
            nswe |= Cell.NSWE_WEST;
        }

        this.z = geoEngine.getNearestZ(x, y, z);
    }

    public boolean canGoNorth() {
        return (nswe & Cell.NSWE_NORTH) != 0;
    }

    public boolean canGoEast() {
        return (nswe & Cell.NSWE_EAST) != 0;
    }

    public boolean canGoSouth() {
        return (nswe & Cell.NSWE_SOUTH) != 0;
    }

    public boolean canGoWest() {
        return (nswe & Cell.NSWE_WEST) != 0;
    }

    public boolean canGoNone() {
        return nswe == 0;
    }

    public boolean canGoAll() {
        return nswe == Cell.NSWE_ALL;
    }

    public int getX() {
        return GeoEngine.getWorldX(nodeX);
    }

    public int getY() {
        return GeoEngine.getWorldY(nodeY);
    }

    public void setZ(short z) {}

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + nodeX;
        result = (prime * result) + nodeY;

        // Combine the geo height and the NSWE bitmask into the hash.
        result = (prime * result) + (((z & 0xFFFF) << 1) | nswe);

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        // Compare _x, _y, _geoHeight, and the bitmask nswe directly.
        final GeoLocation other = (GeoLocation) obj;
        return (nodeX == other.nodeX) && (nodeY == other.nodeY) && (z == other.z) && (nswe == other.nswe);
    }
}
