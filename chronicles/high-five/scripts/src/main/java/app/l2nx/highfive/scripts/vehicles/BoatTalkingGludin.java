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
package app.l2nx.highfive.scripts.vehicles;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.entity.actor.holders.creature.VehiclePathPoint;
import app.l2nx.highfive.entity.actor.instance.Boat;
import app.l2nx.highfive.managers.BoatManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author DS
 */
@Slf4j
public class BoatTalkingGludin implements Runnable {

    // Time: 919s
    private static final VehiclePathPoint[] TALKING_TO_GLUDIN = {
        new VehiclePathPoint(-121385, 261660, -3610, 180, 800),
        new VehiclePathPoint(-127694, 253312, -3610, 200, 800),
        new VehiclePathPoint(-129274, 237060, -3610, 250, 800),
        new VehiclePathPoint(-114688, 139040, -3610, 200, 800),
        new VehiclePathPoint(-109663, 135704, -3610, 180, 800),
        new VehiclePathPoint(-102151, 135704, -3610, 180, 800),
        new VehiclePathPoint(-96686, 140595, -3610, 180, 800),
        new VehiclePathPoint(-95686, 147718, -3610, 180, 800),
        new VehiclePathPoint(-95686, 148718, -3610, 180, 800),
        new VehiclePathPoint(-95686, 149718, -3610, 150, 800)
    };

    private static final VehiclePathPoint[] GLUDIN_DOCK = {new VehiclePathPoint(-95686, 150514, -3610, 150, 800)};

    // Time: 780s
    private static final VehiclePathPoint[] GLUDIN_TO_TALKING = {
        new VehiclePathPoint(-95686, 155514, -3610, 180, 800),
        new VehiclePathPoint(-95686, 185514, -3610, 250, 800),
        new VehiclePathPoint(-60136, 238816, -3610, 200, 800),
        new VehiclePathPoint(-60520, 259609, -3610, 180, 1800),
        new VehiclePathPoint(-65344, 261460, -3610, 180, 1800),
        new VehiclePathPoint(-83344, 261560, -3610, 180, 1800),
        new VehiclePathPoint(-88344, 261660, -3610, 180, 1800),
        new VehiclePathPoint(-92344, 261660, -3610, 150, 1800),
        new VehiclePathPoint(-94242, 261659, -3610, 150, 1800)
    };

    private static final VehiclePathPoint[] TALKING_DOCK = {new VehiclePathPoint(-96622, 261660, -3610, 150, 1800)};

    private final Boat boat;
    private int cycle = 0;
    private int shoutCount = 0;

    private final CreatureSay ARRIVED_AT_TALKING;
    private final CreatureSay ARRIVED_AT_TALKING_2;
    private final CreatureSay LEAVE_TALKING5;
    private final CreatureSay LEAVE_TALKING1;
    private final CreatureSay LEAVE_TALKING1_2;
    private final CreatureSay LEAVE_TALKING0;
    private final CreatureSay LEAVING_TALKING;
    private final CreatureSay ARRIVED_AT_GLUDIN;
    private final CreatureSay ARRIVED_AT_GLUDIN_2;
    private final CreatureSay LEAVE_GLUDIN5;
    private final CreatureSay LEAVE_GLUDIN1;
    private final CreatureSay LEAVE_GLUDIN0;
    private final CreatureSay LEAVING_GLUDIN;
    private final CreatureSay BUSY_TALKING;
    private final CreatureSay BUSY_GLUDIN;

    private final CreatureSay ARRIVAL_GLUDIN10;
    private final CreatureSay ARRIVAL_GLUDIN5;
    private final CreatureSay ARRIVAL_GLUDIN1;
    private final CreatureSay ARRIVAL_TALKING10;
    private final CreatureSay ARRIVAL_TALKING5;
    private final CreatureSay ARRIVAL_TALKING1;

    private final PlaySound TALKING_SOUND;
    private final PlaySound GLUDIN_SOUND;

    public BoatTalkingGludin(Boat boat) {
        this.boat = boat;
        cycle = 0;

        ARRIVED_AT_TALKING =
                new CreatureSay(ChatType.BOAT, 801, SystemMessageId.THE_FERRY_HAS_ARRIVED_AT_TALKING_ISLAND_HARBOR);
        ARRIVED_AT_TALKING_2 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_GLUDIN_HARBOR_AFTER_ANCHORING_FOR_TEN_MINUTES);
        LEAVE_TALKING5 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_GLUDIN_HARBOR_IN_FIVE_MINUTES);
        LEAVE_TALKING1 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_GLUDIN_HARBOR_IN_ONE_MINUTE);
        LEAVE_TALKING1_2 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THOSE_WISHING_TO_RIDE_THE_FERRY_SHOULD_MAKE_HASTE_TO_GET_ON);
        LEAVE_TALKING0 =
                new CreatureSay(ChatType.BOAT, 801, SystemMessageId.THE_FERRY_WILL_BE_LEAVING_SOON_FOR_GLUDIN_HARBOR);
        LEAVING_TALKING = new CreatureSay(ChatType.BOAT, 801, SystemMessageId.THE_FERRY_IS_LEAVING_FOR_GLUDIN_HARBOR);
        ARRIVED_AT_GLUDIN = new CreatureSay(ChatType.BOAT, 801, SystemMessageId.THE_FERRY_HAS_ARRIVED_AT_GLUDIN_HARBOR);
        ARRIVED_AT_GLUDIN_2 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_TALKING_ISLAND_HARBOR_AFTER_ANCHORING_FOR_TEN_MINUTES);
        LEAVE_GLUDIN5 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_TALKING_ISLAND_HARBOR_IN_FIVE_MINUTES);
        LEAVE_GLUDIN1 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_TALKING_ISLAND_HARBOR_IN_ONE_MINUTE);
        LEAVE_GLUDIN0 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_WILL_BE_LEAVING_SOON_FOR_TALKING_ISLAND_HARBOR);
        LEAVING_GLUDIN =
                new CreatureSay(ChatType.BOAT, 801, SystemMessageId.THE_FERRY_IS_LEAVING_FOR_TALKING_ISLAND_HARBOR);
        BUSY_TALKING = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_FROM_GLUDIN_HARBOR_TO_TALKING_ISLAND_HAS_BEEN_DELAYED);
        BUSY_GLUDIN = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_FROM_TALKING_ISLAND_TO_GLUDIN_HARBOR_HAS_BEEN_DELAYED);

        ARRIVAL_GLUDIN10 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.THE_FERRY_FROM_TALKING_ISLAND_WILL_ARRIVE_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_10_MINUTES);
        ARRIVAL_GLUDIN5 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_TALKING_ISLAND_WILL_BE_ARRIVING_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_5_MINUTES);
        ARRIVAL_GLUDIN1 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_TALKING_ISLAND_WILL_BE_ARRIVING_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_1_MINUTE);
        ARRIVAL_TALKING10 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_TALKING_ISLAND_IN_APPROXIMATELY_10_MINUTES);
        ARRIVAL_TALKING5 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_TALKING_ISLAND_IN_APPROXIMATELY_5_MINUTES);
        ARRIVAL_TALKING1 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_TALKING_ISLAND_IN_APPROXIMATELY_1_MINUTE);

        TALKING_SOUND = new PlaySound(
                0,
                "itemsound.ship_arrival_departure",
                1,
                this.boat.getObjectId(),
                TALKING_DOCK[0].getX(),
                TALKING_DOCK[0].getY(),
                TALKING_DOCK[0].getZ());
        GLUDIN_SOUND = new PlaySound(
                0,
                "itemsound.ship_arrival_departure",
                1,
                this.boat.getObjectId(),
                GLUDIN_DOCK[0].getX(),
                GLUDIN_DOCK[0].getY(),
                GLUDIN_DOCK[0].getZ());
    }

    @Override
    public void run() {
        try {
            switch (cycle) {
                case 0: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVE_TALKING5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 1: {
                    BoatManager.getInstance()
                            .broadcastPackets(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVE_TALKING1, LEAVE_TALKING1_2);
                    WorldPartition.loop().schedule(this, 40000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 2: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVE_TALKING0);
                    WorldPartition.loop().schedule(this, 20000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 3: {
                    BoatManager.getInstance().dockShip(BoatManager.TALKING_ISLAND, false);
                    BoatManager.getInstance().broadcastPackets(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVING_TALKING);
                    boat.broadcastPacket(TALKING_SOUND);
                    boat.payForRide(1074, 1, -96777, 258970, -3623);
                    boat.executePath(TALKING_TO_GLUDIN);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 4: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], TALKING_DOCK[0], ARRIVAL_GLUDIN10);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 5: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], TALKING_DOCK[0], ARRIVAL_GLUDIN5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 6: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], TALKING_DOCK[0], ARRIVAL_GLUDIN1);
                    break;
                }
                case 7: {
                    if (BoatManager.getInstance().dockBusy(BoatManager.GLUDIN_HARBOR)) {
                        if (shoutCount == 0) {
                            BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], TALKING_DOCK[0], BUSY_GLUDIN);
                        }

                        shoutCount++;
                        if (shoutCount > 35) {
                            shoutCount = 0;
                        }

                        WorldPartition.loop().schedule(this, 5000, TimeUnit.MILLISECONDS);
                        return;
                    }

                    boat.executePath(GLUDIN_DOCK);
                    break;
                }
                case 8: {
                    BoatManager.getInstance().dockShip(BoatManager.GLUDIN_HARBOR, true);
                    BoatManager.getInstance()
                            .broadcastPackets(GLUDIN_DOCK[0], TALKING_DOCK[0], ARRIVED_AT_GLUDIN, ARRIVED_AT_GLUDIN_2);
                    boat.broadcastPacket(GLUDIN_SOUND);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 9: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVE_GLUDIN5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 10: {
                    BoatManager.getInstance()
                            .broadcastPackets(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVE_GLUDIN1, LEAVE_TALKING1_2);
                    WorldPartition.loop().schedule(this, 40000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 11: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVE_GLUDIN0);
                    WorldPartition.loop().schedule(this, 20000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 12: {
                    BoatManager.getInstance().dockShip(BoatManager.GLUDIN_HARBOR, false);
                    BoatManager.getInstance().broadcastPackets(TALKING_DOCK[0], GLUDIN_DOCK[0], LEAVING_GLUDIN);
                    boat.broadcastPacket(GLUDIN_SOUND);
                    boat.payForRide(1075, 1, -90015, 150422, -3610);
                    boat.executePath(GLUDIN_TO_TALKING);
                    WorldPartition.loop().schedule(this, 150000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 13: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_TALKING10);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 14: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_TALKING5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 15: {
                    BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_TALKING1);
                    break;
                }
                case 16: {
                    if (BoatManager.getInstance().dockBusy(BoatManager.TALKING_ISLAND)) {
                        if (shoutCount == 0) {
                            BoatManager.getInstance().broadcastPacket(TALKING_DOCK[0], GLUDIN_DOCK[0], BUSY_TALKING);
                        }

                        shoutCount++;
                        if (shoutCount > 35) {
                            shoutCount = 0;
                        }

                        WorldPartition.loop().schedule(this, 5000, TimeUnit.MILLISECONDS);
                        return;
                    }

                    boat.executePath(TALKING_DOCK);
                    break;
                }
                case 17: {
                    BoatManager.getInstance().dockShip(BoatManager.TALKING_ISLAND, true);
                    BoatManager.getInstance()
                            .broadcastPackets(
                                    TALKING_DOCK[0], GLUDIN_DOCK[0], ARRIVED_AT_TALKING, ARRIVED_AT_TALKING_2);
                    boat.broadcastPacket(TALKING_SOUND);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
            }

            shoutCount = 0;
            cycle++;
            if (cycle > 17) {
                cycle = 0;
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    public static void main(String[] args) {
        final Boat boat = BoatManager.getInstance().getNewBoat(1, -96622, 261660, -3610, 32768);
        if (boat != null) {
            boat.registerEngine(new BoatTalkingGludin(boat));
            boat.runEngine(180000);
            BoatManager.getInstance().dockShip(BoatManager.TALKING_ISLAND, true);
        }
    }
}
