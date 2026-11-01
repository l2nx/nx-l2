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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RadarControl;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import lombok.Builder;

/**
 * @author dalrond
 */
@SingleWriter
public class Radar {
    private final Player player;
    private final ObjectLinkedOpenHashSet<RadarMarker> markers = new ObjectLinkedOpenHashSet<>();

    public Radar(Player player) {
        this.player = player;
    }

    public void addMarker(int x, int y, int z) {
        WorldPartition.checkOwner();
        markers.add(new RadarMarker(x, y, z));
        player.sendPacket(new RadarControl(2, 2, x, y, z));
        player.sendPacket(new RadarControl(0, 1, x, y, z));
    }

    public void removeMarker(int x, int y, int z) {
        WorldPartition.checkOwner();
        markers.removeIf(marker -> (marker.x() == x) && (marker.y() == y) && (marker.z() == z));
        player.sendPacket(new RadarControl(1, 1, x, y, z));
    }

    public void removeAllMarkers() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<RadarMarker> removed = ScratchBuffers.snapshot(markers)) {
            markers.clear();
            for (RadarMarker marker : removed) {
                player.sendPacket(new RadarControl(2, 2, marker.x(), marker.y(), marker.z()));
            }
        }
    }

    public void loadMarkers() {
        WorldPartition.checkOwner();
        player.sendPacket(new RadarControl(2, 2, player.getX(), player.getY(), player.getZ()));
        try (ScratchBuffers.Snapshot<RadarMarker> shown = ScratchBuffers.snapshot(markers)) {
            for (RadarMarker marker : shown) {
                player.sendPacket(new RadarControl(0, 1, marker.x(), marker.y(), marker.z()));
            }
        }
    }

    @Builder(toBuilder = true)
    private record RadarMarker(int type, int x, int y, int z) {
        private RadarMarker(int x, int y, int z) {
            this(1, x, y, z);
        }
    }
}
