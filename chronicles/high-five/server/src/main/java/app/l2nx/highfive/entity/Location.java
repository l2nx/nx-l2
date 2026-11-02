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
package app.l2nx.highfive.entity;

import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.interfaces.IPositionable;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Plain value type without the ownership check, so no field needs {@code volatile}: loader-built data is published
 * with its owner, world-state copies belong to the world loop. A {@code @SingleWriter} subclass adds the check.
 */
public class Location implements IPositionable {
    private static final int MAX_HEADING = 65535;

    @Getter
    protected int x;

    @Getter
    protected int y;

    @Getter
    protected int z;

    @Getter
    protected int heading;

    @Getter
    @Setter
    protected int instanceId;

    public Location(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        heading = 0;
        instanceId = 0;
    }

    public Location(int x, int y, int z, int heading) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
        instanceId = 0;
    }

    public Location(WorldObject obj) {
        this(obj.getX(), obj.getY(), obj.getZ(), obj.getHeading(), obj.getInstanceId());
    }

    public Location(int x, int y, int z, int heading, int instanceId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
        this.instanceId = instanceId;
    }

    public Location(StatSet set) {
        x = set.getInt("x", 0);
        y = set.getInt("y", 0);
        z = set.getInt("z", 0);
        heading = set.getInt("heading", 0);
        instanceId = set.getInt("instanceId", 0);
    }

    @Override
    public void setXYZ(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void setXYZ(ILocational loc) {
        setXYZ(loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public void setHeading(int heading) {
        this.heading = Math.clamp(heading, 0, MAX_HEADING);
    }

    @Override
    public IPositionable getLocation() {
        return this;
    }

    @Override
    public void setLocation(Location loc) {
        x = loc.getX();
        y = loc.getY();
        z = loc.getZ();
        heading = loc.getHeading();
        instanceId = loc.getInstanceId();
    }

    @Override
    public Location clone() {
        return new Location(x, y, z, heading, instanceId);
    }

    /**
     * Copies locations for a caller outside their owner, so adjusting one leaves the owner's.
     * Returns {@code null} for {@code null}.
     */
    public static List<Location> copiesOf(List<? extends Location> locations) {
        if (locations == null) {
            return null;
        }

        final ObjectArrayList<Location> copies = new ObjectArrayList<>(locations.size());
        for (Location location : locations) {
            copies.add(location.clone());
        }

        return ObjectLists.unmodifiable(copies);
    }

    @Override
    public int hashCode() {
        return (31 * Objects.hash(x, y)) + Objects.hash(z);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Location) {
            // Fields, not getters: a single-writer subclass checks its getters, which equals must not.
            final Location loc = (Location) obj;
            return (x == loc.x)
                    && (y == loc.y)
                    && (z == loc.z)
                    && (heading == loc.heading)
                    && (instanceId == loc.instanceId);
        }

        return false;
    }

    @Override
    public String toString() {
        return "[" + getClass().getSimpleName() + "] X: " + x + " Y: " + y + " Z: " + z + " Heading: " + heading
                + " InstanceId: " + instanceId;
    }
}
