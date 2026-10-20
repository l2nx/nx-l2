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
package app.l2nx.highfive.mechanics.sevensigns;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.spawns.Spawn;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Dimensional Rift Room.
 * @author xban1x
 */
public class DimensionalRiftRoom {
    @Getter
    private final byte type;

    @Getter
    private final byte room;

    private final int xMin;
    private final int xMax;
    private final int yMin;
    private final int yMax;
    private final int zMin;
    private final int zMax;

    @Getter
    private final Location teleportCoorinates;

    private final Shape s;

    @Getter
    private final boolean bossRoom;

    @Getter
    private final List<Spawn> spawns = new ArrayList<>();

    @Getter
    @Setter
    private boolean partyInside = false;

    public DimensionalRiftRoom(
            byte type,
            byte room,
            int xMin,
            int xMax,
            int yMin,
            int yMax,
            int zMin,
            int zMax,
            int xT,
            int yT,
            int zT,
            boolean isBossRoom) {
        this.type = type;
        this.room = room;
        this.xMin = (xMin + 128);
        this.xMax = (xMax - 128);
        this.yMin = (yMin + 128);
        this.yMax = (yMax - 128);
        this.zMin = zMin;
        this.zMax = zMax;
        teleportCoorinates = new Location(xT, yT, zT);
        bossRoom = isBossRoom;
        s = new Polygon(new int[] {xMin, xMax, xMax, xMin}, new int[] {yMin, yMin, yMax, yMax}, 4);
    }

    public int getRandomX() {
        return xMin < xMax ? ClockBridge.random().nextInt(xMin, xMax + 1) : xMin;
    }

    public int getRandomY() {
        return yMin < yMax ? ClockBridge.random().nextInt(yMin, yMax + 1) : yMin;
    }

    public boolean checkIfInZone(int x, int y, int z) {
        return s.contains(x, y) && (z >= zMin) && (z <= zMax);
    }

    public void spawn() {
        for (Spawn spawn : spawns) {
            spawn.doSpawn(false);
            spawn.startRespawn();
        }
    }

    public DimensionalRiftRoom unspawn() {
        for (Spawn spawn : spawns) {
            spawn.stopRespawn();
            if (spawn.getLastSpawn() != null) {
                spawn.getLastSpawn().deleteMe();
            }
        }

        return this;
    }
}
