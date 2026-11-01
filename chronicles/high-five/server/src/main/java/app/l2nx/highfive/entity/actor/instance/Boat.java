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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.ai.BoatAI;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Vehicle;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.VehicleDeparture;
import app.l2nx.highfive.network.protocol.p267.serverpackets.VehicleInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.VehicleStarted;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author Maktakien, DS
 */
@SingleWriter
public class Boat extends Vehicle {

    public Boat(CreatureTemplate template) {
        super(template);
        setInstanceType(InstanceType.Boat);
        setAI(new BoatAI(this));
    }

    @Override
    public boolean isBoat() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return 0;
    }

    @Override
    public boolean moveToNextRoutePoint() {
        WorldPartition.checkOwner();
        final boolean result = super.moveToNextRoutePoint();
        if (result) {
            broadcastPacket(new VehicleDeparture(this));
        }

        return result;
    }

    @Override
    public void oustPlayer(Player player) {
        WorldPartition.checkOwner();
        super.oustPlayer(player);

        final Location loc = getOustLoc();
        if (player.isOnline()) {
            player.teleToLocation(loc.getX(), loc.getY(), loc.getZ());
        } else {
            player.setXYZInvisible(loc.getX(), loc.getY(), loc.getZ()); // offline player: no client to teleport
        }
    }

    @Override
    public void stopMove(Location loc) {
        WorldPartition.checkOwner();
        super.stopMove(loc);

        broadcastPacket(new VehicleStarted(this, 0));
        broadcastPacket(new VehicleInfo(this));
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        player.sendPacket(new VehicleInfo(this));
    }
}
