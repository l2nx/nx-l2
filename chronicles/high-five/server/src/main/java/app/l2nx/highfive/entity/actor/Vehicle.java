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
package app.l2nx.highfive.entity.actor;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.holders.creature.VehiclePathPoint;
import app.l2nx.highfive.entity.actor.stat.VehicleStat;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.zone.ZoneRegion;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.movement.Movement;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.util.LocationUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.Collection;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author DS
 */
@Slf4j
@SingleWriter
public abstract class Vehicle extends Creature {
    protected int dockId = 0;

    // Dense and in boarding order: oust and broadcast walk a copy, which is one arraycopy.
    private final ObjectArraySet<Player> passengers = new ObjectArraySet<>();

    protected Location oustLoc = null;

    private Runnable engine = null;

    protected VehiclePathPoint[] currentPath = null;
    protected int runState = 0;
    private ScheduledFuture<?> monitorTask = null;
    private final Location monitorLocation = new Location(this);

    public Vehicle(CreatureTemplate template) {
        super(template);
        setInstanceType(InstanceType.Vehicle);
        setFlying(true);
    }

    public int getDockId() {
        WorldPartition.checkOwner();
        return dockId;
    }

    public Collection<Player> getPassengers() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(passengers));
    }

    public void setOustLoc(Location oustLoc) {
        WorldPartition.checkOwner();
        this.oustLoc = oustLoc;
    }

    public boolean isBoat() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isAirShip() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean canBeControlled() {
        WorldPartition.checkOwner();
        return engine == null;
    }

    public void registerEngine(Runnable r) {
        WorldPartition.checkOwner();
        engine = r;
    }

    public void runEngine(int delay) {
        WorldPartition.checkOwner();
        if (engine != null) {
            WorldPartition.loop().schedule(engine, delay, TimeUnit.MILLISECONDS);
        }
    }

    public void executePath(VehiclePathPoint[] path) {
        WorldPartition.checkOwner();
        runState = 0;
        currentPath = path;
        if ((currentPath != null) && (currentPath.length > 0)) {
            final VehiclePathPoint point = currentPath[0];
            if (point.getMoveSpeed() > 0) {
                getStat().setMoveSpeed(point.getMoveSpeed());
            }

            if (point.getRotationSpeed() > 0) {
                getStat().setRotationSpeed(point.getRotationSpeed());
            }

            getAI().setIntentionMoveTo(new Location(point.getX(), point.getY(), point.getZ()));
            return;
        }

        getAI().setIntentionActive();
    }

    @Override
    public boolean moveToNextRoutePoint() {
        WorldPartition.checkOwner();
        move = null;
        if (currentPath != null) {
            runState++;
            if (runState < currentPath.length) {
                final VehiclePathPoint point = currentPath[runState];
                if (!isMovementDisabled()) {
                    if (point.getMoveSpeed() == 0) {
                        point.setHeading(point.getRotationSpeed());
                        teleToLocation(point, false);
                        if (monitorTask != null) {
                            monitorTask.cancel(true);
                            monitorTask = null;
                        }

                        currentPath = null;
                    } else {
                        if (point.getMoveSpeed() > 0) {
                            getStat().setMoveSpeed(point.getMoveSpeed());
                        }

                        if (point.getRotationSpeed() > 0) {
                            getStat().setRotationSpeed(point.getRotationSpeed());
                        }

                        final MoveData m = new MoveData();
                        m.disregardingGeodata = false;
                        m.onGeodataPathIndex = -1;
                        m.xDestination = point.getX();
                        m.yDestination = point.getY();
                        m.zDestination = point.getZ();
                        m.heading = 0;

                        final double dx = point.getX() - getX();
                        final double dy = point.getY() - getY();
                        final double distance = Math.sqrt((dx * dx) + (dy * dy));
                        if (distance > 1) {
                            setHeading(LocationUtil.calculateHeadingFrom(getX(), getY(), point.getX(), point.getY()));
                        }

                        m.moveStartTime = GameTimeTaskManager.getInstance().getGameTicks();
                        move = m;
                        Movement.register(this);

                        if (monitorTask == null) {
                            monitorTask = WorldPartition.loop()
                                    .scheduleAtFixedRate(
                                            () -> {
                                                if (!isInDock() && (calculateDistance3D(monitorLocation) == 0)) {
                                                    if (currentPath != null) {
                                                        if (runState < currentPath.length) {
                                                            runState = Math.max(0, runState - 1);
                                                            moveToNextRoutePoint();
                                                        } else {
                                                            broadcastInfo();
                                                        }
                                                    }
                                                } else {
                                                    monitorLocation.setXYZ(this);
                                                }
                                            },
                                            1000,
                                            1000,
                                            TimeUnit.MILLISECONDS);
                        }

                        return true;
                    }
                }
            } else {
                if (monitorTask != null) {
                    monitorTask.cancel(true);
                    monitorTask = null;
                }

                currentPath = null;
            }
        }

        runEngine(10);
        return false;
    }

    @Override
    public VehicleStat getStat() {
        WorldPartition.checkOwner();
        return (VehicleStat) uncheckedStat();
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new VehicleStat(this, statAccess()));
    }

    public boolean isInDock() {
        WorldPartition.checkOwner();
        return dockId > 0;
    }

    public void setInDock(int d) {
        WorldPartition.checkOwner();
        dockId = d;
    }

    public Location getOustLoc() {
        WorldPartition.checkOwner();
        return oustLoc != null ? oustLoc : MapRegionData.getInstance().getTeleToLocation(this, TeleportWhereType.TOWN);
    }

    public void oustPlayers() {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> ousted = ScratchBuffers.snapshot(passengers)) {
            for (Player player : ousted) {
                oustPlayer(player);
            }
        }

        passengers.clear();
    }

    public void oustPlayer(Player player) {
        WorldPartition.checkOwner();
        player.setVehicle(null);
        player.setInVehiclePosition(null);
        removePassenger(player);
    }

    public boolean addPassenger(Player player) {
        WorldPartition.checkOwner();
        if ((player == null)
                || passengers.contains(player)
                || ((player.getVehicle() != null) && (player.getVehicle() != this))) {
            return false;
        }

        passengers.add(player);
        return true;
    }

    public void removePassenger(Player player) {
        WorldPartition.checkOwner();
        passengers.remove(player);
    }

    public boolean isEmpty() {
        WorldPartition.checkOwner();
        return passengers.isEmpty();
    }

    public void broadcastToPassengers(ServerPacket packet) {
        WorldPartition.checkOwner();
        try (ScratchBuffers.Snapshot<Player> receivers = ScratchBuffers.snapshot(passengers)) {
            for (Player player : receivers) {
                if (player != null) {
                    player.sendPacket(packet);
                }
            }
        }
    }

    public void payForRide(int itemId, int count, int oustX, int oustY, int oustZ) {
        WorldPartition.checkOwner();
        World.forEachVisibleObjectInRange(this, Player.class, 1000, player -> {
            if (player.isInBoat() && (player.getBoat() == this)) {
                if (itemId > 0) {
                    final Item ticket = player.getInventory().getItemByItemId(itemId);
                    if ((ticket == null)
                            || (player.getInventory().destroyItem(ItemProcessType.FEE, ticket, count, player, this)
                                    == null)) {
                        player.sendPacket(SystemMessageId.YOU_DO_NOT_POSSESS_THE_CORRECT_TICKET_TO_BOARD_THE_BOAT);
                        player.teleToLocation(new Location(oustX, oustY, oustZ), true);
                        return;
                    }

                    final InventoryUpdate iu = new InventoryUpdate();
                    iu.addModifiedItem(ticket);
                    player.sendInventoryUpdate(iu);
                }

                addPassenger(player);
            }
        });
    }

    @Override
    public boolean updatePosition() {
        WorldPartition.checkOwner();
        final boolean result = super.updatePosition();
        try (ScratchBuffers.Snapshot<Player> aboard = ScratchBuffers.snapshot(passengers)) {
            for (Player player : aboard) {
                if ((player != null) && (player.getVehicle() == this)) {
                    player.setXYZ(getX(), getY(), getZ());
                    player.revalidateZone(false);
                }
            }
        }

        return result;
    }

    @Override
    public void teleToLocation(ILocational loc, boolean allowRandomOffset) {
        WorldPartition.checkOwner();
        if (isMoving()) {
            stopMove(null);
        }

        setTeleporting(true);

        getAI().setIntentionActive();

        try (ScratchBuffers.Snapshot<Player> aboard = ScratchBuffers.snapshot(passengers)) {
            for (Player player : aboard) {
                if (player != null) {
                    player.teleToLocation(loc, false);
                }
            }
        }

        decayMe();
        setXYZ(loc);

        if (loc.getHeading() != 0) {
            setHeading(loc.getHeading());
        }

        onTeleported();
        revalidateZone(true);
    }

    @Override
    public void stopMove(Location loc) {
        WorldPartition.checkOwner();
        move = null;
        if (loc != null) {
            setXYZ(loc);
            setHeading(loc.getHeading());
            revalidateZone(true);
        }
    }

    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
        engine = null;

        try {
            if (isMoving()) {
                stopMove(null);
            }
        } catch (Exception e) {
            log.error("Failed stopMove().", e);
        }

        try {
            oustPlayers();
        } catch (Exception e) {
            log.error("Failed oustPlayers().", e);
        }

        final ZoneRegion oldZoneRegion = ZoneManager.getInstance().getRegion(this);

        try {
            decayMe();
        } catch (Exception e) {
            log.error("Failed decayMe().", e);
        }

        oldZoneRegion.removeFromZones(this);

        return super.deleteMe();
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
    }

    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return 0;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return false;
    }

    @Override
    public void detachAI() {
        WorldPartition.checkOwner();
    }

    @Override
    public boolean isVehicle() {
        WorldPartition.checkOwner();
        return true;
    }
}
