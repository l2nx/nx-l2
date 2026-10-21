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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * A packet used to draw points and lines on client.<br/>
 * <b>Note:</b> Names in points and lines are bugged they will appear even when not looking at them.
 * @author NosBit
 */
public class ExServerPrimitive extends ServerPacket {
    private final String name;
    private final int x;
    private final int y;
    private final int z;
    private final List<Point> points = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();

    /**
     * @param name A unique name this will be used to replace lines if second packet is sent
     * @param x the x coordinate usually middle of drawing area
     * @param y the y coordinate usually middle of drawing area
     * @param z the z coordinate usually middle of drawing area
     */
    public ExServerPrimitive(String name, int x, int y, int z) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * @param name A unique name this will be used to replace lines if second packet is sent
     * @param locational the ILocational to take coordinates usually middle of drawing area
     */
    public ExServerPrimitive(String name, ILocational locational) {
        this(name, locational.getX(), locational.getY(), locational.getZ());
    }

    /**
     * Adds a point to be displayed on client.
     * @param name the name that will be displayed over the point
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param x the x coordinate for this point
     * @param y the y coordinate for this point
     * @param z the z coordinate for this point
     */
    public void addPoint(String name, int color, boolean isNameColored, int x, int y, int z) {
        points.add(new Point(name, color, isNameColored, x, y, z));
    }

    /**
     * Adds a point to be displayed on client.
     * @param name the name that will be displayed over the point
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param locational the ILocational to take coordinates for this point
     */
    public void addPoint(String name, int color, boolean isNameColored, ILocational locational) {
        addPoint(name, color, isNameColored, locational.getX(), locational.getY(), locational.getZ());
    }

    /**
     * Adds a point to be displayed on client.
     * @param color the color
     * @param x the x coordinate for this point
     * @param y the y coordinate for this point
     * @param z the z coordinate for this point
     */
    public void addPoint(int color, int x, int y, int z) {
        addPoint("", color, false, x, y, z);
    }

    /**
     * Adds a point to be displayed on client.
     * @param color the color
     * @param locational the ILocational to take coordinates for this point
     */
    public void addPoint(int color, ILocational locational) {
        addPoint("", color, false, locational);
    }

    /**
     * Adds a point to be displayed on client.
     * @param name the name that will be displayed over the point
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param x the x coordinate for this point
     * @param y the y coordinate for this point
     * @param z the z coordinate for this point
     */
    public void addPoint(String name, Color color, boolean isNameColored, int x, int y, int z) {
        addPoint(name, color.getRGB(), isNameColored, x, y, z);
    }

    /**
     * Adds a point to be displayed on client.
     * @param name the name that will be displayed over the point
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param locational the ILocational to take coordinates for this point
     */
    public void addPoint(String name, Color color, boolean isNameColored, ILocational locational) {
        addPoint(name, color.getRGB(), isNameColored, locational);
    }

    /**
     * Adds a point to be displayed on client.
     * @param color the color
     * @param x the x coordinate for this point
     * @param y the y coordinate for this point
     * @param z the z coordinate for this point
     */
    public void addPoint(Color color, int x, int y, int z) {
        addPoint("", color, false, x, y, z);
    }

    /**
     * Adds a point to be displayed on client.
     * @param color the color
     * @param locational the ILocational to take coordinates for this point
     */
    public void addPoint(Color color, ILocational locational) {
        addPoint("", color, false, locational);
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(String name, int color, boolean isNameColored, int x, int y, int z, int x2, int y2, int z2) {
        lines.add(new Line(name, color, isNameColored, x, y, z, x2, y2, z2));
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param locational the ILocational to take coordinates for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(String name, int color, boolean isNameColored, ILocational locational, int x2, int y2, int z2) {
        addLine(name, color, isNameColored, locational.getX(), locational.getY(), locational.getZ(), x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(String name, int color, boolean isNameColored, int x, int y, int z, ILocational locational2) {
        addLine(name, color, isNameColored, x, y, z, locational2.getX(), locational2.getY(), locational2.getZ());
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param locational the ILocational to take coordinates for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(
            String name, int color, boolean isNameColored, ILocational locational, ILocational locational2) {
        addLine(name, color, isNameColored, locational, locational2.getX(), locational2.getY(), locational2.getZ());
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(int color, int x, int y, int z, int x2, int y2, int z2) {
        addLine("", color, false, x, y, z, x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param locational the ILocational to take coordinates for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(int color, ILocational locational, int x2, int y2, int z2) {
        addLine("", color, false, locational, x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(int color, int x, int y, int z, ILocational locational2) {
        addLine("", color, false, x, y, z, locational2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param locational the ILocational to take coordinates for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(int color, ILocational locational, ILocational locational2) {
        addLine("", color, false, locational, locational2);
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(String name, Color color, boolean isNameColored, int x, int y, int z, int x2, int y2, int z2) {
        addLine(name, color.getRGB(), isNameColored, x, y, z, x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param locational the ILocational to take coordinates for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(
            String name, Color color, boolean isNameColored, ILocational locational, int x2, int y2, int z2) {
        addLine(name, color.getRGB(), isNameColored, locational, x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(String name, Color color, boolean isNameColored, int x, int y, int z, ILocational locational2) {
        addLine(name, color.getRGB(), isNameColored, x, y, z, locational2);
    }

    /**
     * Adds a line to be displayed on client
     * @param name the name that will be displayed over the middle of line
     * @param color the color
     * @param isNameColored if {@code true} name will be colored as well.
     * @param locational the ILocational to take coordinates for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(
            String name, Color color, boolean isNameColored, ILocational locational, ILocational locational2) {
        addLine(name, color.getRGB(), isNameColored, locational, locational2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(Color color, int x, int y, int z, int x2, int y2, int z2) {
        addLine("", color, false, x, y, z, x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param locational the ILocational to take coordinates for this line start point
     * @param x2 the x coordinate for this line end point
     * @param y2 the y coordinate for this line end point
     * @param z2 the z coordinate for this line end point
     */
    public void addLine(Color color, ILocational locational, int x2, int y2, int z2) {
        addLine("", color, false, locational, x2, y2, z2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param x the x coordinate for this line start point
     * @param y the y coordinate for this line start point
     * @param z the z coordinate for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(Color color, int x, int y, int z, ILocational locational2) {
        addLine("", color, false, x, y, z, locational2);
    }

    /**
     * Adds a line to be displayed on client
     * @param color the color
     * @param locational the ILocational to take coordinates for this line start point
     * @param locational2 the ILocational to take coordinates for this line end point
     */
    public void addLine(Color color, ILocational locational, ILocational locational2) {
        addLine("", color, false, locational, locational2);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SERVER_PRIMITIVE.writeId(this, buffer);
        buffer.writeString(name);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(65535); // Has to do something with display range and angle.
        buffer.writeInt(65535); // Has to do something with display range and angle.
        buffer.writeInt(points.size() + lines.size());
        for (Point point : points) {
            buffer.writeByte(1); // It is the type in this case Point.
            buffer.writeString(point.getName());
            final int color = point.getColor();
            buffer.writeInt((color >> 16) & 0xFF); // R
            buffer.writeInt((color >> 8) & 0xFF); // G
            buffer.writeInt(color & 0xFF); // B
            buffer.writeInt(point.isNameColored());
            buffer.writeInt(point.getX());
            buffer.writeInt(point.getY());
            buffer.writeInt(point.getZ());
        }

        for (Line line : lines) {
            buffer.writeByte(2); // It is the type in this case Line.
            buffer.writeString(line.getName());
            final int color = line.getColor();
            buffer.writeInt((color >> 16) & 0xFF); // R
            buffer.writeInt((color >> 8) & 0xFF); // G
            buffer.writeInt(color & 0xFF); // B
            buffer.writeInt(line.isNameColored());
            buffer.writeInt(line.getX());
            buffer.writeInt(line.getY());
            buffer.writeInt(line.getZ());
            buffer.writeInt(line.getX2());
            buffer.writeInt(line.getY2());
            buffer.writeInt(line.getZ2());
        }
    }

    private static class Point {
        @Getter
        private final String name;

        @Getter
        private final int color;

        @Getter
        private final boolean nameColored;

        @Getter
        private final int x;

        @Getter
        private final int y;

        @Getter
        private final int z;

        public Point(String name, int color, boolean isNameColored, int x, int y, int z) {
            this.name = name;
            this.color = color;
            nameColored = isNameColored;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class Line extends Point {
        @Getter
        private final int x2;

        @Getter
        private final int y2;

        @Getter
        private final int z2;

        public Line(String name, int color, boolean isNameColored, int x, int y, int z, int x2, int y2, int z2) {
            super(name, color, isNameColored, x, y, z);
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
        }
    }
}
