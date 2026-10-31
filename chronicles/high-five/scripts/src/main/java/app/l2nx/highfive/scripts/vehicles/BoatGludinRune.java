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
public class BoatGludinRune implements Runnable {

    // Time: 1151s
    private static final VehiclePathPoint[] GLUDIN_TO_RUNE = {
        new VehiclePathPoint(-95686, 155514, -3610, 150, 800),
        new VehiclePathPoint(-98112, 159040, -3610, 150, 800),
        new VehiclePathPoint(-104192, 160608, -3610, 200, 1800),
        new VehiclePathPoint(-109952, 159616, -3610, 250, 1800),
        new VehiclePathPoint(-112768, 154784, -3610, 290, 1800),
        new VehiclePathPoint(-114688, 139040, -3610, 290, 1800),
        new VehiclePathPoint(-115232, 134368, -3610, 290, 1800),
        new VehiclePathPoint(-113888, 121696, -3610, 290, 1800),
        new VehiclePathPoint(-107808, 104928, -3610, 290, 1800),
        new VehiclePathPoint(-97152, 75520, -3610, 290, 800),
        new VehiclePathPoint(-85536, 67264, -3610, 290, 1800),
        new VehiclePathPoint(-64640, 55840, -3610, 290, 1800),
        new VehiclePathPoint(-60096, 44672, -3610, 290, 1800),
        new VehiclePathPoint(-52672, 37440, -3610, 290, 1800),
        new VehiclePathPoint(-46144, 33184, -3610, 290, 1800),
        new VehiclePathPoint(-36096, 24928, -3610, 290, 1800),
        new VehiclePathPoint(-33792, 8448, -3610, 290, 1800),
        new VehiclePathPoint(-23776, 3424, -3610, 290, 1000),
        new VehiclePathPoint(-12000, -1760, -3610, 290, 1000),
        new VehiclePathPoint(672, 480, -3610, 290, 1800),
        new VehiclePathPoint(15488, 200, -3610, 290, 1000),
        new VehiclePathPoint(24736, 164, -3610, 290, 1000),
        new VehiclePathPoint(32192, -1156, -3610, 290, 1000),
        new VehiclePathPoint(39200, -8032, -3610, 270, 1000),
        new VehiclePathPoint(44320, -25152, -3610, 270, 1000),
        new VehiclePathPoint(40576, -31616, -3610, 250, 800),
        new VehiclePathPoint(36819, -35315, -3610, 220, 800)
    };

    private static final VehiclePathPoint[] RUNE_DOCK = {new VehiclePathPoint(34381, -37680, -3610, 200, 800)};

    // Time: 967s
    private static final VehiclePathPoint[] RUNE_TO_GLUDIN = {
        new VehiclePathPoint(32750, -39300, -3610, 150, 800),
        new VehiclePathPoint(27440, -39328, -3610, 180, 1000),
        new VehiclePathPoint(21456, -34272, -3610, 200, 1000),
        new VehiclePathPoint(6608, -29520, -3610, 250, 800),
        new VehiclePathPoint(4160, -27828, -3610, 270, 800),
        new VehiclePathPoint(2432, -25472, -3610, 270, 1000),
        new VehiclePathPoint(-8000, -16272, -3610, 220, 1000),
        new VehiclePathPoint(-18976, -9760, -3610, 290, 800),
        new VehiclePathPoint(-23776, 3408, -3610, 290, 800),
        new VehiclePathPoint(-33792, 8432, -3610, 290, 800),
        new VehiclePathPoint(-36096, 24912, -3610, 290, 800),
        new VehiclePathPoint(-46144, 33184, -3610, 290, 800),
        new VehiclePathPoint(-52688, 37440, -3610, 290, 800),
        new VehiclePathPoint(-60096, 44672, -3610, 290, 800),
        new VehiclePathPoint(-64640, 55840, -3610, 290, 800),
        new VehiclePathPoint(-85552, 67248, -3610, 290, 800),
        new VehiclePathPoint(-97168, 85264, -3610, 290, 800),
        new VehiclePathPoint(-107824, 104912, -3610, 290, 800),
        new VehiclePathPoint(-102151, 135704, -3610, 290, 800),
        new VehiclePathPoint(-96686, 140595, -3610, 290, 800),
        new VehiclePathPoint(-95686, 147717, -3610, 250, 800),
        new VehiclePathPoint(-95686, 148218, -3610, 200, 800)
    };

    private static final VehiclePathPoint[] GLUDIN_DOCK = {new VehiclePathPoint(-95686, 150514, -3610, 150, 800)};

    private final Boat boat;
    private int cycle = 0;
    private int shoutCount = 0;

    private final CreatureSay ARRIVED_AT_GLUDIN;
    private final CreatureSay ARRIVED_AT_GLUDIN_2;
    private final CreatureSay LEAVE_GLUDIN5;
    private final CreatureSay LEAVE_GLUDIN1;
    private final CreatureSay LEAVE_GLUDIN0;
    private final CreatureSay LEAVING_GLUDIN;
    private final CreatureSay ARRIVED_AT_RUNE;
    private final CreatureSay ARRIVED_AT_RUNE_2;
    private final CreatureSay LEAVE_RUNE5;
    private final CreatureSay LEAVE_RUNE1;
    private final CreatureSay LEAVE_RUNE0;
    private final CreatureSay LEAVING_RUNE;
    private final CreatureSay BUSY_GLUDIN;
    private final CreatureSay BUSY_RUNE;

    private final CreatureSay ARRIVAL_RUNE15;
    private final CreatureSay ARRIVAL_RUNE10;
    private final CreatureSay ARRIVAL_RUNE5;
    private final CreatureSay ARRIVAL_RUNE1;
    private final CreatureSay ARRIVAL_GLUDIN15;
    private final CreatureSay ARRIVAL_GLUDIN10;
    private final CreatureSay ARRIVAL_GLUDIN5;
    private final CreatureSay ARRIVAL_GLUDIN1;

    private final PlaySound GLUDIN_SOUND;
    private final PlaySound RUNE_SOUND;

    public BoatGludinRune(Boat boat) {
        this.boat = boat;

        ARRIVED_AT_GLUDIN = new CreatureSay(ChatType.BOAT, 801, SystemMessageId.THE_FERRY_HAS_ARRIVED_AT_GLUDIN_HARBOR);
        ARRIVED_AT_GLUDIN_2 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.DEPARTURE_FOR_RUNE_HARBOR_WILL_TAKE_PLACE_AFTER_ANCHORING_FOR_TEN_MINUTES);
        LEAVE_GLUDIN5 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.DEPARTURE_FOR_RUNE_HARBOR_WILL_TAKE_PLACE_IN_FIVE_MINUTES);
        LEAVE_GLUDIN1 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.DEPARTURE_FOR_RUNE_HARBOR_WILL_TAKE_PLACE_IN_ONE_MINUTE);
        LEAVE_GLUDIN0 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.MAKE_HASTE_WE_WILL_BE_DEPARTING_FOR_GLUDIN_HARBOR_SHORTLY_2);
        LEAVING_GLUDIN = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.WE_ARE_NOW_DEPARTING_FOR_GLUDIN_HARBOR_HOLD_ON_AND_ENJOY_THE_RIDE);
        ARRIVED_AT_RUNE = new CreatureSay(ChatType.BOAT, 801, SystemMessageId.WELCOME_TO_RUNE_HARBOR);
        ARRIVED_AT_RUNE_2 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.THE_FERRY_WILL_LEAVE_FOR_GLUDIN_HARBOR_AFTER_ANCHORING_FOR_TEN_MINUTES);
        LEAVE_RUNE5 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.DEPARTURE_FOR_GLUDIN_HARBOR_WILL_TAKE_PLACE_IN_FIVE_MINUTES);
        LEAVE_RUNE1 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.DEPARTURE_FOR_GLUDIN_HARBOR_WILL_TAKE_PLACE_IN_ONE_MINUTE);
        LEAVE_RUNE0 = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.MAKE_HASTE_WE_WILL_BE_DEPARTING_FOR_GLUDIN_HARBOR_SHORTLY);
        LEAVING_RUNE = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.WE_ARE_NOW_DEPARTING_FOR_GLUDIN_HARBOR_HOLD_ON_AND_ENJOY_THE_RIDE);
        BUSY_GLUDIN = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_FROM_RUNE_HARBOR_TO_GLUDIN_HARBOR_HAS_BEEN_DELAYED);
        BUSY_RUNE = new CreatureSay(
                ChatType.BOAT, 801, SystemMessageId.THE_FERRY_FROM_GLUDIN_HARBOR_TO_RUNE_HARBOR_HAS_BEEN_DELAYED);

        ARRIVAL_RUNE15 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_RUNE_HARBOR_IN_APPROXIMATELY_15_MINUTES);
        ARRIVAL_RUNE10 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_RUNE_HARBOR_IN_APPROXIMATELY_10_MINUTES);
        ARRIVAL_RUNE5 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_RUNE_HARBOR_IN_APPROXIMATELY_5_MINUTES);
        ARRIVAL_RUNE1 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.THE_FERRY_FROM_GLUDIN_HARBOR_WILL_BE_ARRIVING_AT_RUNE_HARBOR_IN_APPROXIMATELY_1_MINUTE);
        ARRIVAL_GLUDIN15 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_RUNE_HARBOR_WILL_BE_ARRIVING_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_15_MINUTES);
        ARRIVAL_GLUDIN10 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_RUNE_HARBOR_WILL_BE_ARRIVING_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_10_MINUTES);
        ARRIVAL_GLUDIN5 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId
                        .THE_FERRY_FROM_RUNE_HARBOR_WILL_BE_ARRIVING_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_5_MINUTES);
        ARRIVAL_GLUDIN1 = new CreatureSay(
                ChatType.BOAT,
                801,
                SystemMessageId.THE_FERRY_FROM_RUNE_HARBOR_WILL_BE_ARRIVING_AT_GLUDIN_HARBOR_IN_APPROXIMATELY_1_MINUTE);

        GLUDIN_SOUND = new PlaySound(
                0,
                "itemsound.ship_arrival_departure",
                1,
                this.boat.getObjectId(),
                GLUDIN_DOCK[0].getX(),
                GLUDIN_DOCK[0].getY(),
                GLUDIN_DOCK[0].getZ());
        RUNE_SOUND = new PlaySound(
                0,
                "itemsound.ship_arrival_departure",
                1,
                this.boat.getObjectId(),
                RUNE_DOCK[0].getX(),
                RUNE_DOCK[0].getY(),
                RUNE_DOCK[0].getZ());
    }

    @Override
    public void run() {
        try {
            switch (cycle) {
                case 0: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], LEAVE_GLUDIN5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 1: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], LEAVE_GLUDIN1);
                    WorldPartition.loop().schedule(this, 40000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 2: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], LEAVE_GLUDIN0);
                    WorldPartition.loop().schedule(this, 20000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 3: {
                    BoatManager.getInstance().dockShip(BoatManager.GLUDIN_HARBOR, false);
                    BoatManager.getInstance().broadcastPackets(GLUDIN_DOCK[0], RUNE_DOCK[0], LEAVING_GLUDIN);
                    boat.broadcastPacket(GLUDIN_SOUND);
                    boat.payForRide(7905, 1, -90015, 150422, -3610);
                    boat.executePath(GLUDIN_TO_RUNE);
                    WorldPartition.loop().schedule(this, 250000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 4: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_RUNE15);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 5: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_RUNE10);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 6: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_RUNE5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 7: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], ARRIVAL_RUNE1);
                    break;
                }
                case 8: {
                    if (BoatManager.getInstance().dockBusy(BoatManager.RUNE_HARBOR)) {
                        if (shoutCount == 0) {
                            BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], BUSY_RUNE);
                        }

                        shoutCount++;
                        if (shoutCount > 35) {
                            shoutCount = 0;
                        }

                        WorldPartition.loop().schedule(this, 5000, TimeUnit.MILLISECONDS);
                        return;
                    }

                    boat.executePath(RUNE_DOCK);
                    break;
                }
                case 9: {
                    BoatManager.getInstance().dockShip(BoatManager.RUNE_HARBOR, true);
                    BoatManager.getInstance()
                            .broadcastPackets(RUNE_DOCK[0], GLUDIN_DOCK[0], ARRIVED_AT_RUNE, ARRIVED_AT_RUNE_2);
                    boat.broadcastPacket(RUNE_SOUND);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 10: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], LEAVE_RUNE5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 11: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], LEAVE_RUNE1);
                    WorldPartition.loop().schedule(this, 40000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 12: {
                    BoatManager.getInstance().broadcastPacket(RUNE_DOCK[0], GLUDIN_DOCK[0], LEAVE_RUNE0);
                    WorldPartition.loop().schedule(this, 20000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 13: {
                    BoatManager.getInstance().dockShip(BoatManager.RUNE_HARBOR, false);
                    BoatManager.getInstance().broadcastPackets(RUNE_DOCK[0], GLUDIN_DOCK[0], LEAVING_RUNE);
                    boat.broadcastPacket(RUNE_SOUND);
                    boat.payForRide(7904, 1, 34513, -38009, -3640);
                    boat.executePath(RUNE_TO_GLUDIN);
                    WorldPartition.loop().schedule(this, 60000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 14: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], ARRIVAL_GLUDIN15);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 15: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], ARRIVAL_GLUDIN10);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 16: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], ARRIVAL_GLUDIN5);
                    WorldPartition.loop().schedule(this, 240000, TimeUnit.MILLISECONDS);
                    break;
                }
                case 17: {
                    BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], ARRIVAL_GLUDIN1);
                    break;
                }
                case 18: {
                    if (BoatManager.getInstance().dockBusy(BoatManager.GLUDIN_HARBOR)) {
                        if (shoutCount == 0) {
                            BoatManager.getInstance().broadcastPacket(GLUDIN_DOCK[0], RUNE_DOCK[0], BUSY_GLUDIN);
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
                case 19: {
                    BoatManager.getInstance().dockShip(BoatManager.GLUDIN_HARBOR, true);
                    BoatManager.getInstance()
                            .broadcastPackets(GLUDIN_DOCK[0], RUNE_DOCK[0], ARRIVED_AT_GLUDIN, ARRIVED_AT_GLUDIN_2);
                    boat.broadcastPacket(GLUDIN_SOUND);
                    WorldPartition.loop().schedule(this, 300000, TimeUnit.MILLISECONDS);
                    break;
                }
            }

            shoutCount = 0;
            cycle++;
            if (cycle > 19) {
                cycle = 0;
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    public static void main(String[] args) {
        final Boat boat = BoatManager.getInstance().getNewBoat(3, -95686, 150514, -3610, 16723);
        if (boat != null) {
            boat.registerEngine(new BoatGludinRune(boat));
            boat.runEngine(180000);
            BoatManager.getInstance().dockShip(BoatManager.GLUDIN_HARBOR, true);
        }
    }
}
