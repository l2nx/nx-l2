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
import app.l2nx.highfive.ai.AirShipAI;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Vehicle;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.managers.AirShipManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExAirShipInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetOffAirShip;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExGetOnAirShip;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExMoveToLocationAirShip;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExStopMoveAirShip;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * Flying airships. Very similar to Maktakien boats (see Boat) but these do fly :P
 * @author DrHouse, DS
 */
@SingleWriter
public class AirShip extends Vehicle {
    public AirShip(CreatureTemplate template) {
        super(template);
        setInstanceType(InstanceType.AirShip);
        setAI(new AirShipAI(this));
    }

    @Override
    public boolean isAirShip() {
        WorldPartition.checkOwner();
        return true;
    }

    public boolean isOwner(Player player) {
        WorldPartition.checkOwner();
        return false;
    }

    public int getOwnerId() {
        WorldPartition.checkOwner();
        return 0;
    }

    public boolean isCaptain(Player player) {
        WorldPartition.checkOwner();
        return false;
    }

    public int getCaptainId() {
        WorldPartition.checkOwner();
        return 0;
    }

    public int getHelmObjectId() {
        WorldPartition.checkOwner();
        return 0;
    }

    public int getHelmItemId() {
        WorldPartition.checkOwner();
        return 0;
    }

    public boolean setCaptain(Player player) {
        WorldPartition.checkOwner();
        return false;
    }

    public int getFuel() {
        WorldPartition.checkOwner();
        return 0;
    }

    public void setFuel(int f) {
        WorldPartition.checkOwner();
    }

    public int getMaxFuel() {
        WorldPartition.checkOwner();
        return 0;
    }

    public void setMaxFuel(int mf) {
        WorldPartition.checkOwner();
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
            broadcastPacket(new ExMoveToLocationAirShip(this));
        }

        return result;
    }

    @Override
    public boolean addPassenger(Player player) {
        WorldPartition.checkOwner();
        if (!super.addPassenger(player)) {
            return false;
        }

        player.setVehicle(this);
        player.setInVehiclePosition(new Location(0, 0, 0));
        player.broadcastPacket(new ExGetOnAirShip(player, this));
        player.setXYZ(getX(), getY(), getZ());
        player.revalidateZone(true);
        player.stopMove(null);
        return true;
    }

    @Override
    public void oustPlayer(Player player) {
        WorldPartition.checkOwner();
        super.oustPlayer(player);

        final Location loc = getOustLoc();
        if (player.isOnline()) {
            player.broadcastPacket(new ExGetOffAirShip(player, this, loc.getX(), loc.getY(), loc.getZ()));
            player.teleToLocation(loc.getX(), loc.getY(), loc.getZ());
        } else {
            player.setXYZInvisible(loc.getX(), loc.getY(), loc.getZ());
        }
    }

    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
        if (!super.deleteMe()) {
            return false;
        }

        AirShipManager.getInstance().removeAirShip(this);
        return true;
    }

    @Override
    public void stopMove(Location loc) {
        WorldPartition.checkOwner();
        super.stopMove(loc);

        broadcastPacket(new ExStopMoveAirShip(this));
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
        broadcastPacket(new ExAirShipInfo(this));
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        if (isVisibleFor(player)) {
            player.sendPacket(new ExAirShipInfo(this));
        }
    }
}
