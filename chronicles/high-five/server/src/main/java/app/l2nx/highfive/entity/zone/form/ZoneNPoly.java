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
import java.awt.Polygon;
import java.util.random.RandomGenerator;
import lombok.Getter;

/**
 * A not so primitive npoly zone
 * @author durgus, Mobius
 */
public class ZoneNPoly extends ZoneForm {
    private final Polygon p;

    @Getter
    private final int lowZ;

    @Getter
    private final int highZ;

    @Getter
    private final Location centerPoint;

    /**
     * @param x
     * @param y
     * @param z1
     * @param z2
     */
    public ZoneNPoly(int[] x, int[] y, int z1, int z2) {
        p = new Polygon(x, y, x.length);

        lowZ = Math.min(z1, z2);
        highZ = Math.max(z1, z2);

        double area = 0;
        double cx = 0;
        double cy = 0;
        for (int i = 0; i < p.npoints; i++) {
            final int nextIndex = (i + 1) % p.npoints;
            final double crossProduct = (p.xpoints[i] * p.ypoints[nextIndex]) - (p.xpoints[nextIndex] * p.ypoints[i]);
            area += crossProduct;
            cx += (p.xpoints[i] + p.xpoints[nextIndex]) * crossProduct;
            cy += (p.ypoints[i] + p.ypoints[nextIndex]) * crossProduct;
        }

        area /= 2.0;
        if (area == 0) {
            cx = 0;
            cy = 0;
            for (int i = 0; i < p.npoints; i++) {
                cx += p.xpoints[i];
                cy += p.ypoints[i];
            }

            cx /= p.npoints;
            cy /= p.npoints;
        } else {
            cx /= (6.0 * area);
            cy /= (6.0 * area);
        }

        centerPoint = new Location((int) cx, (int) cy, (lowZ + highZ) / 2);
    }

    @Override
    public boolean isInsideZone(int x, int y, int z) {
        return p.contains(x, y) && (z >= lowZ) && (z <= highZ);
    }

    @Override
    public boolean intersectsRectangle(int ax1, int ax2, int ay1, int ay2) {
        return p.intersects(Math.min(ax1, ax2), Math.min(ay1, ay2), Math.abs(ax2 - ax1), Math.abs(ay2 - ay1));
    }

    @Override
    public double getDistanceToZone(int x, int y) {
        final int[] xPoints = p.xpoints;
        final int[] yPoints = p.ypoints;
        double test;
        double dx = xPoints[0] - x;
        double dy = yPoints[0] - y;
        double shortestDist = (dx * dx) + (dy * dy);

        for (int i = 1; i < p.npoints; i++) {
            dx = xPoints[i] - x;
            dy = yPoints[i] - y;
            test = (dx * dx) + (dy * dy);
            if (test < shortestDist) {
                shortestDist = test;
            }
        }

        return Math.sqrt(shortestDist);
    }

    @Override
    public void visualizeZone(int z) {
        for (int i = 0; i < p.npoints; i++) {
            final int nextIndex = (i + 1) == p.xpoints.length ? 0 : i + 1;
            final int vx = p.xpoints[nextIndex] - p.xpoints[i];
            final int vy = p.ypoints[nextIndex] - p.ypoints[i];
            final float length = (float) Math.sqrt((vx * vx) + (vy * vy)) / STEP;
            for (int o = 1; o <= length; o++) {
                dropDebugItem(
                        Inventory.ADENA_ID,
                        1,
                        (int) (p.xpoints[i] + ((o / length) * vx)),
                        (int) (p.ypoints[i] + ((o / length) * vy)),
                        z);
            }
        }
    }

    @Override
    public Location getRandomPoint() {
        final int minX = p.getBounds().x;
        final int maxX = p.getBounds().x + p.getBounds().width;
        final int minY = p.getBounds().y;
        final int maxY = p.getBounds().y + p.getBounds().height;

        final RandomGenerator random = ClockBridge.random();
        int x = random.nextInt(minX, maxX + 1);
        int y = random.nextInt(minY, maxY + 1);

        int antiBlocker = 0;
        while (!p.contains(x, y) && (antiBlocker++ < 1000)) {
            x = random.nextInt(minX, maxX + 1);
            y = random.nextInt(minY, maxY + 1);
        }

        return new Location(x, y, GeoEngine.getInstance().getHeight(x, y, (lowZ + highZ) / 2));
    }

    public int[] getX() {
        return p.xpoints;
    }

    public int[] getY() {
        return p.ypoints;
    }
}
