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
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a 3D coordinate (x, y, z) with an optional heading and instance id.
 */
public class Location implements IPositionable {
    private static final int MAX_HEADING = 65535; // Max unsigned 16-bit value.

    @Getter
    protected volatile int x;

    @Getter
    protected volatile int y;

    @Getter
    protected volatile int z;

    @Getter
    protected volatile int heading;

    @Getter
    @Setter
    protected volatile int instanceId;

    /**
     * Constructs a Location at a specified x, y and z coordinate, with a default heading and instance id of 0.
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    public Location(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        heading = 0;
        instanceId = 0;
    }

    /**
     * Constructs a Location at a specified x, y, z coordinate and heading, with a default instance id of 0.
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param heading the heading (direction)
     */
    public Location(int x, int y, int z, int heading) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
        instanceId = 0;
    }

    /**
     * Constructs a Location based on the position and heading of a WorldObject.
     * @param obj the WorldObject to derive the location from.
     */
    public Location(WorldObject obj) {
        this(obj.getX(), obj.getY(), obj.getZ(), obj.getHeading(), obj.getInstanceId());
    }

    /**
     * Constructs a Location based on the position, heading and instance id of a WorldObject.
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param heading the heading (direction)
     * @param instanceId the instance id
     */
    public Location(int x, int y, int z, int heading, int instanceId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
        this.instanceId = instanceId;
    }

    /**
     * Constructs a Location from a {@link StatSet}, retrieving values for x, y, z, heading and instance id.
     * @param set a StatSet containing x, y, z and heading properties.
     */
    public Location(StatSet set) {
        x = set.getInt("x", 0);
        y = set.getInt("y", 0);
        z = set.getInt("z", 0);
        heading = set.getInt("heading", 0);
        instanceId = set.getInt("instanceId", 0);
    }

    /**
     * Sets the x, y and z coordinates of this Location.
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param z the new z coordinate
     */
    @Override
    public void setXYZ(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Updates the x, y and z coordinates of this Location based on another {@link ILocational}.
     * @param loc the source ILocational to copy coordinates from.
     */
    @Override
    public void setXYZ(ILocational loc) {
        setXYZ(loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Sets the heading (direction) for this Location.
     * @param heading the new the heading for this location.
     */
    @Override
    public void setHeading(int heading) {
        this.heading = Math.clamp(heading, 0, MAX_HEADING);
    }

    /**
     * Provides the current Location instance, as this class implements {@link IPositionable}.
     * @return the current Location instance
     */
    @Override
    public IPositionable getLocation() {
        return this;
    }

    /**
     * Sets this Location's coordinates and heading to match another Location's.
     * @param loc the source Location to copy from.
     */
    @Override
    public void setLocation(Location loc) {
        x = loc.getX();
        y = loc.getY();
        z = loc.getZ();
        heading = loc.getHeading();
        instanceId = loc.getInstanceId();
    }

    /**
     * Creates a shallow copy of this Location.
     * @return a new Location instance with the same x, y, z, heading and instance id values.
     */
    @Override
    public Location clone() {
        return new Location(x, y, z, heading, instanceId);
    }

    /**
     * Computes a hash code based on this Location's coordinates and heading.
     * @return the hash code for this Location.
     */
    @Override
    public int hashCode() {
        return (31 * Objects.hash(x, y)) + Objects.hash(z);
    }

    /**
     * Checks equality between this Location and another object.<br>
     * Two Locations are considered equal if they have the same x, y, z coordinates and heading.
     * @param obj the object to compare
     * @return {@code true} if the given object is a Location with the same x, y, z and heading values; otherwise, {@code false}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Location) {
            final Location loc = (Location) obj;
            return (getX() == loc.getX())
                    && (getY() == loc.getY())
                    && (getZ() == loc.getZ())
                    && (getHeading() == loc.getHeading())
                    && (getInstanceId() == loc.getInstanceId());
        }

        return false;
    }

    /**
     * Provides a string representation of this Location.
     * @return a formatted string containing the class name, x, y, z coordinates and heading.
     */
    @Override
    public String toString() {
        return "[" + getClass().getSimpleName() + "] X: " + x + " Y: " + y + " Z: " + z + " Heading: " + heading
                + " InstanceId: " + instanceId;
    }
}
