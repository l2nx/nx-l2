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
package app.l2nx.highfive.util;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @version 0.1, 2005-03-12
 * @author Balancer
 */
@Slf4j
public class PolygonTerritory {

    protected static class Point {
        protected int x;
        protected int y;
        protected int zmin;
        protected int zmax;
        protected int proc;

        Point(int x, int y, int zmin, int zmax, int proc) {
            this.x = x;
            this.y = y;
            this.zmin = zmin;
            this.zmax = zmax;
            this.proc = proc;
        }
    }

    private final List<Point> points = new CopyOnWriteArrayList<>();
    private final int terr;
    private int xMin;
    private int xMax;
    private int yMin;
    private int yMax;
    private int zMin;
    private int zMax;

    @Getter
    private int procMax;

    public PolygonTerritory(int terr) {
        this.terr = terr;
        xMin = 999999;
        xMax = -999999;
        yMin = 999999;
        yMax = -999999;
        zMin = 999999;
        zMax = -999999;
        procMax = 0;
    }

    public void add(int x, int y, int zmin, int zmax, int proc) {
        points.add(new Point(x, y, zmin, zmax, proc));
        if (x < xMin) {
            xMin = x;
        }

        if (y < yMin) {
            yMin = y;
        }

        if (x > xMax) {
            xMax = x;
        }

        if (y > yMax) {
            yMax = y;
        }

        if (zmin < zMin) {
            zMin = zmin;
        }

        if (zmax > zMax) {
            zMax = zmax;
        }

        procMax += proc;
    }

    public boolean isIntersect(int x, int y, Point p1, Point p2) {
        final double dy1 = p1.y - y;
        final double dy2 = p2.y - y;
        if (Math.abs(Math.signum(dy1) - Math.signum(dy2)) <= 1e-6) {
            return false;
        }

        final double dx1 = p1.x - x;
        final double dx2 = p2.x - x;
        if ((dx1 >= 0) && (dx2 >= 0)) {
            return true;
        }

        if ((dx1 < 0) && (dx2 < 0)) {
            return false;
        }

        final double dx0 = (dy1 * (p1.x - p2.x)) / (p1.y - p2.y);
        return dx0 <= dx1;
    }

    public boolean isInside(int x, int y) {
        int intersectCount = 0;
        for (int i = 0; i < points.size(); i++) {
            final Point p1 = points.get(i > 0 ? i - 1 : points.size() - 1);
            final Point p2 = points.get(i);
            if (isIntersect(x, y, p1, p2)) {
                intersectCount++;
            }
        }

        return (intersectCount % 2) == 1;
    }

    public Location getRandomPoint() {
        final RandomGenerator random = ClockBridge.random();
        if (procMax > 0) {
            int pos = 0;
            final int rnd = random.nextInt(procMax);
            for (Point p1 : points) {
                pos += p1.proc;
                if (rnd <= pos) {
                    return new Location(p1.x, p1.y, p1.zmin < p1.zmax ? random.nextInt(p1.zmin, p1.zmax + 1) : p1.zmin);
                }
            }
        }

        for (int i = 0; i < 100; i++) {
            final int x = xMin < xMax ? random.nextInt(xMin, xMax + 1) : xMin;
            final int y = yMin < yMax ? random.nextInt(yMin, yMax + 1) : yMin;
            if (isInside(x, y)) {
                double curdistance = 0;
                int zmin = zMin;
                for (Point p1 : points) {
                    final double dx = p1.x - x;
                    final double dy = p1.y - y;
                    final double distance = Math.sqrt((dx * dx) + (dy * dy));
                    if ((curdistance == 0) || (distance < curdistance)) {
                        curdistance = distance;
                        zmin = p1.zmin;
                    }
                }

                return new Location(x, y, zmin < zMax ? random.nextInt(zmin, zMax + 1) : zmin);
            }
        }

        log.warn("Can't make point for territory {}", terr);
        return null;
    }
}
