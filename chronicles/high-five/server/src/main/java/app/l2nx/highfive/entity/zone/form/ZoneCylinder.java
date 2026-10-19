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

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.zone.ZoneForm;
import app.l2nx.highfive.geoengine.GeoEngine;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;

/**
 * A primitive circular zone
 * @author durgus, Mobius
 */
public class ZoneCylinder extends ZoneForm {
    private final int x;
    private final int y;

    @Getter
    private final int lowZ;

    @Getter
    private final int highZ;

    private final int rad;
    private final int radS;

    @Getter
    private final Location centerPoint;

    public ZoneCylinder(int x, int y, int z1, int z2, int rad) {
        this.x = x;
        this.y = y;
        lowZ = z1;
        highZ = z2;
        this.rad = rad;
        radS = rad * rad;
        centerPoint = new Location(this.x, this.y, (lowZ + highZ) / 2);
    }

    @Override
    public boolean isInsideZone(int x, int y, int z) {
        final double dx = this.x - x;
        final double dy = this.y - y;
        return (((dx * dx) + (dy * dy)) <= radS) && (z >= lowZ) && (z <= highZ);
    }

    @Override
    public boolean intersectsRectangle(int ax1, int ax2, int ay1, int ay2) {
        // Circles point inside the rectangle?
        if ((x > ax1) && (x < ax2) && (y > ay1) && (y < ay2)) {
            return true;
        }

        // Any point of the rectangle intersecting the Circle?
        final double dx1 = ax1 - x;
        final double dy1 = ay1 - y;
        final double dx1Sq = dx1 * dx1;
        final double dy1Sq = dy1 * dy1;
        if ((dx1Sq + dy1Sq) < radS) {
            return true;
        }

        final double dy2 = ay2 - y;
        final double dy2Sq = dy2 * dy2;
        if ((dx1Sq + dy2Sq) < radS) {
            return true;
        }

        final double dx2 = ax2 - x;
        final double dx2Sq = dx2 * dx2;
        if ((dx2Sq + dy1Sq) < radS) {
            return true;
        }

        if ((dx2Sq + dy2Sq) < radS) {
            return true;
        }

        // Collision on any side of the rectangle?
        if ((x > ax1) && (x < ax2)) {
            if (Math.abs(y - ay2) < rad) {
                return true;
            }

            if (Math.abs(y - ay1) < rad) {
                return true;
            }
        }

        if ((y > ay1) && (y < ay2)) {
            if (Math.abs(x - ax2) < rad) {
                return true;
            }

            if (Math.abs(x - ax1) < rad) {
                return true;
            }
        }

        return false;
    }

    @Override
    public double getDistanceToZone(int x, int y) {
        final double dx = this.x - x;
        final double dy = this.y - y;
        return Math.sqrt((dx * dx) + (dy * dy)) - rad;
    }

    @Override
    public void visualizeZone(int z) {
        final int count = (int) ((2 * Math.PI * rad) / STEP);
        final double angle = (2 * Math.PI) / count;
        for (int i = 0; i < count; i++) {
            dropDebugItem(
                    Inventory.ADENA_ID,
                    1,
                    x + (int) (Math.cos(angle * i) * rad),
                    y + (int) (Math.sin(angle * i) * rad),
                    z);
        }
    }

    @Override
    public Location getRandomPoint() {
        int x = 0;
        int y = 0;
        final int x2 = this.x - rad;
        final int y2 = this.y - rad;
        final int x3 = this.x + rad;
        final int y3 = this.y + rad;
        double dx = this.x - x;
        double dy = this.y - y;
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        while (((dx * dx) + (dy * dy)) > radS) {
            x = random.nextInt(x2, x3 + 1);
            y = random.nextInt(y2, y3 + 1);
            dx = this.x - x;
            dy = this.y - y;
        }

        return new Location(x, y, GeoEngine.getInstance().getHeight(x, y, (lowZ + highZ) / 2));
    }
}
