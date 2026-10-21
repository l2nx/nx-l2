/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.creature.VehiclePathPoint;
import app.l2nx.highfive.entity.actor.instance.AirShip;
import app.l2nx.highfive.entity.actor.instance.ControllableAirShip;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExAirShipTeleportList;
import app.l2nx.highfive.repository.world.AirshipRepository;
import app.l2nx.highfive.util.StatSet;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AirShipManager {
    private static volatile AirShipManager instance;

    private static class AirShipTeleportList {
        @Getter
        private final int location;

        @Getter
        private final int[] fuel;

        @Getter
        private final VehiclePathPoint[][] route;

        public AirShipTeleportList(int locationValue, int[] fuelValue, VehiclePathPoint[][] routesValue) {
            location = locationValue;
            fuel = fuelValue;
            route = routesValue;
        }
    }

    private CreatureTemplate airShipTemplate = null;
    private final Map<Integer, StatSet> airShipsInfo = new HashMap<>();
    private final Map<Integer, AirShip> airShips = new HashMap<>();
    private final Map<Integer, AirShipTeleportList> teleports = new HashMap<>();

    private final AirshipRepository airshipRepository;

    public AirShipManager(AirshipRepository airshipRepository) {
        this.airshipRepository = airshipRepository;
        instance = this;
    }

    public void load() {
        final StatSet npcDat = new StatSet();
        npcDat.set("npcId", 9);
        npcDat.set("level", 0);
        npcDat.set("jClass", "boat");
        npcDat.set("baseShldDef", 0);
        npcDat.set("baseShldRate", 0);
        npcDat.set("baseAccCombat", 38);
        npcDat.set("baseEvasRate", 38);
        npcDat.set("baseCritRate", 38);
        npcDat.set("collisionRadius", 0);
        npcDat.set("collisionHeight", 0);
        npcDat.set("sex", "male");
        npcDat.set("type", "");
        npcDat.set("baseAtkRange", 0);
        npcDat.set("baseMpMax", 0);
        npcDat.set("baseCpMax", 0);
        npcDat.set("rewardExp", 0);
        npcDat.set("rewardSp", 0);
        npcDat.set("basePAtk", 0);
        npcDat.set("baseMAtk", 0);
        npcDat.set("basePAtkSpd", 0);
        npcDat.set("aggroRange", 0);
        npcDat.set("baseMAtkSpd", 0);
        npcDat.set("rhand", 0);
        npcDat.set("lhand", 0);
        npcDat.set("armor", 0);
        npcDat.set("baseWalkSpd", 0);
        npcDat.set("baseRunSpd", 0);
        npcDat.set("name", "AirShip");
        npcDat.set("baseHpMax", 50000);
        npcDat.set("baseHpReg", 3.e-3f);
        npcDat.set("baseMpReg", 3.e-3f);
        npcDat.set("basePDef", 100);
        npcDat.set("baseMDef", 100);
        airShipTemplate = new CreatureTemplate(npcDat);
        loadLicenses();
    }

    public AirShip getNewAirShip(int x, int y, int z, int heading) {
        final AirShip airShip = new AirShip(airShipTemplate);
        airShip.setHeading(heading);
        airShip.setXYZInvisible(x, y, z);
        airShip.spawnMe();
        airShip.getStat().setMoveSpeed(280);
        airShip.getStat().setRotationSpeed(2000);
        return airShip;
    }

    public AirShip getNewAirShip(int x, int y, int z, int heading, int ownerId) {
        final StatSet info = airShipsInfo.get(ownerId);
        if (info == null) {
            return null;
        }

        AirShip airShip = airShips.get(ownerId);
        if (airShip != null) {
            airShip.refreshId();
        } else {
            airShip = new ControllableAirShip(airShipTemplate, ownerId);
            airShips.put(ownerId, airShip);

            airShip.setMaxFuel(600);
            airShip.setFuel(info.getInt("fuel"));
            airShip.getStat().setMoveSpeed(280);
            airShip.getStat().setRotationSpeed(2000);
        }

        airShip.setHeading(heading);
        airShip.setXYZInvisible(x, y, z);
        airShip.spawnMe();
        return airShip;
    }

    public void removeAirShip(AirShip ship) {
        if (ship.getOwnerId() == 0) {
            return;
        }

        final StatSet info = airShipsInfo.get(ship.getOwnerId());
        if (info != null) {
            info.set("fuel", ship.getFuel());
        }

        storeInDb(ship.getOwnerId());
    }

    public boolean hasAirShipLicense(int ownerId) {
        return airShipsInfo.containsKey(ownerId);
    }

    public void registerLicense(int ownerId) {
        if (airShipsInfo.containsKey(ownerId)) {
            return;
        }

        final StatSet info = new StatSet();
        info.set("fuel", 600);
        airShipsInfo.put(ownerId, info);
        try {
            airshipRepository.save(ownerId, info.getInt("fuel"));
        } catch (DataAccessException e) {
            log.warn("Could not add new airship license: {}", e.getMessage(), e);
        }
    }

    public boolean hasAirShip(int ownerId) {
        final AirShip ship = airShips.get(ownerId);
        return (ship != null) && (ship.isSpawned() || ship.isTeleporting());
    }

    public void registerAirShipTeleportList(
            int dockId, int locationId, VehiclePathPoint[][] tp, int[] fuelConsumption) {
        if (tp.length != fuelConsumption.length) {
            return;
        }

        teleports.put(dockId, new AirShipTeleportList(locationId, fuelConsumption, tp));
    }

    public void sendAirShipTeleportList(Player player) {
        if ((player == null) || !player.isInAirShip()) {
            return;
        }

        final AirShip ship = player.getAirShip();
        if (!ship.isCaptain(player) || !ship.isInDock() || ship.isMoving()) {
            return;
        }

        final int dockId = ship.getDockId();
        if (!teleports.containsKey(dockId)) {
            return;
        }

        final AirShipTeleportList all = teleports.get(dockId);
        player.sendPacket(new ExAirShipTeleportList(all.getLocation(), all.getRoute(), all.getFuel()));
    }

    public VehiclePathPoint[] getTeleportDestination(int dockId, int index) {
        final AirShipTeleportList all = teleports.get(dockId);
        return (all == null) || (index < -1) || (index >= all.getRoute().length) ? null : all.getRoute()[index + 1];
    }

    public int getFuelConsumption(int dockId, int index) {
        final AirShipTeleportList all = teleports.get(dockId);
        return (all == null) || (index < -1) || (index >= all.getFuel().length) ? 0 : all.getFuel()[index + 1];
    }

    private void loadLicenses() {
        try {
            airshipRepository.findAll().forEach((clanId, fuel) -> {
                final StatSet info = new StatSet();
                info.set("fuel", fuel);
                airShipsInfo.put(clanId, info);
            });
        } catch (DataAccessException e) {
            log.warn("Could not load airships table: {}", e.getMessage(), e);
        }

        log.info("Loaded {} private airships", airShipsInfo.size());
    }

    private void storeInDb(int ownerId) {
        final StatSet info = airShipsInfo.get(ownerId);
        if (info == null) {
            return;
        }

        try {
            airshipRepository.save(ownerId, info.getInt("fuel"));
        } catch (DataAccessException e) {
            log.warn("Could not update airships table: {}", e.getMessage(), e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static AirShipManager getInstance() {
        final AirShipManager current = instance;
        if (current == null) {
            throw new IllegalStateException("AirShipManager is used before the Spring context created it.");
        }
        return current;
    }
}
