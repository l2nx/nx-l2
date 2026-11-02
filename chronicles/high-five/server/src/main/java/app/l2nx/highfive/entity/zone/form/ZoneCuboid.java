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
package app.l2nx.highfive.entity.zone.form;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.zone.ZoneForm;
import app.l2nx.highfive.geoengine.GeoEngine;
import java.awt.Rectangle;
import java.util.random.RandomGenerator;
import lombok.Getter;

/**
 * A primitive rectangular zone
 * @author durgus, Mobius
 */
public class ZoneCuboid extends ZoneForm {
    @Getter
    private final int lowZ;

    @Getter
    private final int highZ;

    private final Rectangle r;

    private final Location centerPoint;

    public ZoneCuboid(int x1, int x2, int y1, int y2, int z1, int z2) {
        final int minX = Math.min(x1, x2);
        final int maxX = Math.max(x1, x2);
        final int minY = Math.min(y1, y2);
        final int maxY = Math.max(y1, y2);

        r = new Rectangle(minX, minY, maxX - minX, maxY - minY);

        lowZ = Math.min(z1, z2);
        highZ = Math.max(z1, z2);

        centerPoint = new Location((minX + maxX) / 2, (minY + maxY) / 2, (lowZ + highZ) / 2);
    }

    @Override
    public boolean isInsideZone(int x, int y, int z) {
        return r.contains(x, y) && (z >= lowZ) && (z <= highZ);
    }

    @Override
    public boolean intersectsRectangle(int ax1, int ax2, int ay1, int ay2) {
        return r.intersects(Math.min(ax1, ax2), Math.min(ay1, ay2), Math.abs(ax2 - ax1), Math.abs(ay2 - ay1));
    }

    @Override
    public double getDistanceToZone(int x, int y) {
        final int x1 = r.x;
        final int x2 = r.x + r.width;
        final int y1 = r.y;
        final int y2 = r.y + r.height;
        final double dx1 = x1 - x;
        final double dx2 = x2 - x;
        final double dy1 = y1 - y;
        final double dy2 = y2 - y;
        final double dx1Sq = dx1 * dx1;
        final double dx2Sq = dx2 * dx2;
        final double dy1Sq = dy1 * dy1;
        final double dy2Sq = dy2 * dy2;
        double test = dx1Sq + dy2Sq;
        double shortestDist = dx1Sq + dy1Sq;

        if (test < shortestDist) {
            shortestDist = test;
        }

        test = dx2Sq + dy1Sq;
        if (test < shortestDist) {
            shortestDist = test;
        }

        test = dx2Sq + dy2Sq;
        if (test < shortestDist) {
            shortestDist = test;
        }

        return Math.sqrt(shortestDist);
    }

    @Override
    public void visualizeZone(int z) {
        final int x1 = r.x;
        final int x2 = r.x + r.width;
        final int y1 = r.y;
        final int y2 = r.y + r.height;

        for (int x = x1; x < x2; x += STEP) {
            dropDebugItem(Inventory.ADENA_ID, 1, x, y1, z);
            dropDebugItem(Inventory.ADENA_ID, 1, x, y2, z);
        }

        for (int y = y1; y < y2; y += STEP) {
            dropDebugItem(Inventory.ADENA_ID, 1, x1, y, z);
            dropDebugItem(Inventory.ADENA_ID, 1, x2, y, z);
        }
    }

    // A copy, so a caller that moves the point leaves the center of the zone as it is.
    @Override
    public Location getCenterPoint() {
        return centerPoint.clone();
    }

    @Override
    public Location getRandomPoint() {
        final RandomGenerator random = ClockBridge.random();
        final int x = random.nextInt(r.x, r.x + r.width + 1);
        final int y = random.nextInt(r.y, r.y + r.height + 1);

        return new Location(x, y, GeoEngine.getInstance().getHeight(x, y, (lowZ + highZ) / 2));
    }
}
