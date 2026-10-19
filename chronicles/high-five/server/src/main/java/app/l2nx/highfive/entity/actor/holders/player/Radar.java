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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.serverpackets.RadarControl;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author dalrond
 */
public class Radar {
    private final Player player;
    private final Set<RadarMarker> markers = ConcurrentHashMap.newKeySet();

    public Radar(Player player) {
        this.player = player;
    }

    // Add a marker to player's radar.
    public void addMarker(int x, int y, int z) {
        final RadarMarker newMarker = new RadarMarker(x, y, z);
        markers.add(newMarker);
        player.sendPacket(new RadarControl(2, 2, x, y, z));
        player.sendPacket(new RadarControl(0, 1, x, y, z));
    }

    // Remove a marker from player's radar.
    public void removeMarker(int x, int y, int z) {
        for (RadarMarker rm : markers) {
            if ((rm.x == x) && (rm.y == y) && (rm.z == z)) {
                markers.remove(rm);
            }
        }

        player.sendPacket(new RadarControl(1, 1, x, y, z));
    }

    public void removeAllMarkers() {
        for (RadarMarker tempMarker : markers) {
            player.sendPacket(new RadarControl(2, 2, tempMarker.x, tempMarker.y, tempMarker.z));
        }

        markers.clear();
    }

    public void loadMarkers() {
        player.sendPacket(new RadarControl(2, 2, player.getX(), player.getY(), player.getZ()));
        for (RadarMarker tempMarker : markers) {
            player.sendPacket(new RadarControl(0, 1, tempMarker.x, tempMarker.y, tempMarker.z));
        }
    }

    public static class RadarMarker {
        // Simple class to model radar points.
        public int type;
        public int x;
        public int y;
        public int z;

        public RadarMarker(int type, int x, int y, int z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public RadarMarker(int x, int y, int z) {
            type = 1;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = (prime * result) + type;
            result = (prime * result) + x;
            result = (prime * result) + y;
            result = (prime * result) + z;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof RadarMarker)) {
                return false;
            }

            final RadarMarker other = (RadarMarker) obj;
            return (type == other.type) && (x == other.x) && (y == other.y) && (z == other.z);
        }
    }
}
