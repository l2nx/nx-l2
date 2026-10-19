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
package app.l2nx.highfive.scripts.ai.areas.Gracia.vehicles;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.creature.VehiclePathPoint;
import app.l2nx.highfive.entity.actor.instance.AirShip;
import app.l2nx.highfive.entity.actor.instance.ControllableAirShip;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.entity.zone.type.ScriptZone;
import app.l2nx.highfive.managers.AirShipManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.enums.Movie;
import app.l2nx.highfive.network.serverpackets.NpcSay;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AirShipController extends Script {
    protected class DecayTask implements Runnable {
        @Override
        public void run() {
            if (dockedShip != null) {
                dockedShip.deleteMe();
            }
        }
    }

    protected class DepartTask implements Runnable {
        @Override
        public void run() {
            if ((dockedShip != null) && dockedShip.isInDock() && !dockedShip.isMoving()) {
                if (departPath != null) {
                    dockedShip.executePath(departPath);
                } else {
                    dockedShip.deleteMe();
                }
            }
        }
    }

    protected int dockZone = 0;
    protected int shipSpawnX = 0;
    protected int shipSpawnY = 0;

    protected int shipSpawnZ = 0;

    protected int shipHeading = 0;
    protected Location oustLoc = null;
    protected int locationId = 0;

    protected VehiclePathPoint[] arrivalPath = null;
    protected VehiclePathPoint[] departPath = null;

    protected VehiclePathPoint[][] teleportsTable = null;

    protected int[] fuelTable = null;

    protected Movie movie = null;

    protected boolean busy = false;
    protected ControllableAirShip dockedShip = null;
    private final Runnable decayTask = new DecayTask();

    private final Runnable departTask = new DepartTask();

    private Future<?> departSchedule = null;

    private NpcSay arrivalMessage = null;
    private static final int DEPART_INTERVAL = 300000; // 5 min
    private static final int LICENSE = 13559;

    private static final int STARSTONE = 13277;
    private static final int SUMMON_COST = 5;

    private static final SystemMessage SM_NEED_MORE = new SystemMessage(
                    SystemMessageId.AN_AIRSHIP_CANNOT_BE_SUMMONED_BECAUSE_YOU_DON_T_HAVE_ENOUGH_S1)
            .addItemName(STARSTONE);

    public AirShipController() {}

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        if ("summon".equalsIgnoreCase(event)) {
            if (dockedShip != null) {
                if (dockedShip.isOwner(player)) {
                    player.sendPacket(SystemMessageId.THE_CLAN_OWNED_AIRSHIP_ALREADY_EXISTS);
                }

                return null;
            }

            if (busy) {
                player.sendPacket(SystemMessageId.ANOTHER_AIRSHIP_HAS_ALREADY_BEEN_SUMMONED_PLEASE_TRY_AGAIN_LATER);
                return null;
            }

            if (!player.hasAccess(ClanAccess.ACCESS_AIRSHIP)) {
                player.sendPacket(
                        SystemMessageId.AIRSHIP_SUMMON_LICENSE_REGISTRATION_CAN_ONLY_BE_DONE_BY_THE_CLAN_LEADER);
                return null;
            }

            final int ownerId = player.getClanId();
            if (!AirShipManager.getInstance().hasAirShipLicense(ownerId)) {
                player.sendPacket(
                        SystemMessageId
                                .AN_AIRSHIP_CANNOT_BE_SUMMONED_BECAUSE_EITHER_YOU_HAVE_NOT_REGISTERED_YOUR_AIRSHIP_LICENSE_OR_THE_AIRSHIP_HAS_NOT_YET_BEEN_SUMMONED);
                return null;
            }

            if (AirShipManager.getInstance().hasAirShip(ownerId)) {
                player.sendPacket(SystemMessageId.YOUR_CLAN_S_AIRSHIP_IS_ALREADY_BEING_USED_BY_ANOTHER_CLAN_MEMBER);
                return null;
            }

            if (!player.destroyItemByItemId(ItemProcessType.FEE, STARSTONE, SUMMON_COST, npc, true)) {
                player.sendPacket(SM_NEED_MORE);
                return null;
            }

            busy = true;
            final AirShip ship = AirShipManager.getInstance()
                    .getNewAirShip(shipSpawnX, shipSpawnY, shipSpawnZ, shipHeading, ownerId);
            if (ship != null) {
                if (arrivalPath != null) {
                    ship.executePath(arrivalPath);
                }

                if (arrivalMessage == null) {
                    arrivalMessage = new NpcSay(
                            npc.getObjectId(),
                            ChatType.NPC_SHOUT,
                            npc.getId(),
                            NpcStringId.THE_AIRSHIP_HAS_BEEN_SUMMONED_IT_WILL_AUTOMATICALLY_DEPART_IN_5_MINUTES);
                }

                npc.broadcastPacket(arrivalMessage);
            } else {
                busy = false;
            }

            return null;
        } else if ("board".equalsIgnoreCase(event)) {
            if (player.isTransformed()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_TRANSFORMED);
                return null;
            } else if (player.isParalyzed()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_PETRIFIED);
                return null;
            } else if (player.isDead() || player.isFakeDeath()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_DEAD);
                return null;
            } else if (player.isFishing()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_FISHING);
                return null;
            } else if (player.isInCombat()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_IN_BATTLE);
                return null;
            } else if (player.isInDuel()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_IN_A_DUEL);
                return null;
            } else if (player.isSitting()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_SITTING);
                return null;
            } else if (player.isCastingNow()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_CASTING);
                return null;
            } else if (player.isCursedWeaponEquipped()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHEN_A_CURSED_WEAPON_IS_EQUIPPED);
                return null;
            } else if (player.isCombatFlagEquipped()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_HOLDING_A_FLAG);
                return null;
            } else if (player.hasSummon() || player.isMounted()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_AN_AIRSHIP_WHILE_A_PET_OR_A_SERVITOR_IS_SUMMONED);
                return null;
            } else if (player.isFlyingMounted()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_BOARD_BECAUSE_YOU_DO_NOT_MEET_THE_REQUIREMENTS);
                return null;
            }

            if (dockedShip != null) {
                dockedShip.addPassenger(player);
            }

            return null;
        } else if ("register".equalsIgnoreCase(event)) {
            if ((player.getClan() == null) || (player.getClan().getLevel() < 5)) {
                player.sendPacket(
                        SystemMessageId.IN_ORDER_TO_ACQUIRE_AN_AIRSHIP_THE_CLAN_S_LEVEL_MUST_BE_LEVEL_5_OR_HIGHER);
                return null;
            }

            if (!player.isClanLeader()) {
                player.sendPacket(
                        SystemMessageId.AIRSHIP_SUMMON_LICENSE_REGISTRATION_CAN_ONLY_BE_DONE_BY_THE_CLAN_LEADER);
                return null;
            }

            final int ownerId = player.getClanId();
            if (AirShipManager.getInstance().hasAirShipLicense(ownerId)) {
                player.sendPacket(SystemMessageId.THE_AIRSHIP_SUMMON_LICENSE_HAS_ALREADY_BEEN_ACQUIRED);
                return null;
            }

            if (!player.destroyItemByItemId(ItemProcessType.FEE, LICENSE, 1, npc, true)) {
                player.sendPacket(SM_NEED_MORE);
                return null;
            }

            AirShipManager.getInstance().registerLicense(ownerId);
            player.sendPacket(
                    SystemMessageId.THE_AIRSHIP_SUMMON_LICENSE_HAS_BEEN_ENTERED_YOUR_CLAN_CAN_NOW_SUMMON_THE_AIRSHIP);
            return null;
        } else {
            return event;
        }
    }

    @Override
    public void onEnterZone(Creature creature, ZoneType zone) {
        if ((creature instanceof ControllableAirShip) && (dockedShip == null)) {
            dockedShip = (ControllableAirShip) creature;
            dockedShip.setInDock(dockZone);
            dockedShip.setOustLoc(oustLoc);

            // Ship is not empty - display movie to passengers and dock
            if (!dockedShip.isEmpty()) {
                if (movie != null) {
                    playMovie(dockedShip.getPassengers(), movie);
                }

                ThreadPool.schedule(decayTask, 1000);
            } else {
                departSchedule = ThreadPool.schedule(departTask, DEPART_INTERVAL);
            }
        }
    }

    @Override
    public void onExitZone(Creature creature, ZoneType zone) {
        if ((creature instanceof ControllableAirShip) && creature.equals(dockedShip)) {
            if (departSchedule != null) {
                departSchedule.cancel(false);
                departSchedule = null;
            }

            dockedShip.setInDock(0);
            dockedShip = null;
            busy = false;
        }
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        return npc.getId() + ".htm";
    }

    protected void validityCheck() {
        final ScriptZone zone = ZoneManager.getInstance().getZoneById(dockZone, ScriptZone.class);
        if (zone == null) {
            log.warn("{}: Invalid zone {}, controller disabled", getName(), dockZone);
            busy = true;
            return;
        }

        VehiclePathPoint p;
        if (arrivalPath != null) {
            if (arrivalPath.length == 0) {
                log.warn("{}: Zero arrival path length.", getName());
                arrivalPath = null;
            } else {
                p = arrivalPath[arrivalPath.length - 1];
                if (!zone.isInsideZone(p.getLocation())) {
                    log.warn(
                            "{}: Arrival path finish point ({},{},{}) not in zone {}",
                            getName(),
                            p.getX(),
                            p.getY(),
                            p.getZ(),
                            dockZone);
                    arrivalPath = null;
                }
            }
        }

        if ((arrivalPath == null)
                && !ZoneManager.getInstance()
                        .getZoneById(dockZone, ScriptZone.class)
                        .isInsideZone(shipSpawnX, shipSpawnY, shipSpawnZ)) {
            log.warn(
                    "{}: Arrival path is null and spawn point not in zone {}, controller disabled",
                    getName(),
                    dockZone);
            busy = true;
            return;
        }

        if (departPath != null) {
            if (departPath.length == 0) {
                log.warn("{}: Zero depart path length.", getName());
                departPath = null;
            } else {
                p = departPath[departPath.length - 1];
                if (zone.isInsideZone(p.getLocation())) {
                    log.warn(
                            "{}: Departure path finish point ({},{},{}) in zone {}",
                            getName(),
                            p.getX(),
                            p.getY(),
                            p.getZ(),
                            dockZone);
                    departPath = null;
                }
            }
        }

        if (teleportsTable != null) {
            if (fuelTable == null) {
                log.warn("{}: Fuel consumption not defined.", getName());
            } else {
                if (teleportsTable.length != fuelTable.length) {
                    log.warn("{}: Fuel consumption not match teleport list.", getName());
                } else {
                    AirShipManager.getInstance()
                            .registerAirShipTeleportList(dockZone, locationId, teleportsTable, fuelTable);
                }
            }
        }
    }
}
