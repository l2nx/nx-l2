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
package app.l2nx.highfive.entity;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.actor.instance.Servitor;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.ActionClickHandler;
import app.l2nx.highfive.handler.ActionShiftHandler;
import app.l2nx.highfive.handler.IActionClickHandler;
import app.l2nx.highfive.handler.IActionShiftHandler;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.interfaces.IPositionable;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.DeleteObject;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExSendUIEvent;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.LocationUtil;
import java.util.HashMap;

/**
 * Base class for all interactive objects.
 */
@SingleWriter
public abstract class WorldObject extends ListenersContainer implements IPositionable {
    private String name;
    private int objectId;
    private WorldRegion worldRegion;
    private final Location location = new Location(0, 0, -10000);
    private InstanceType instanceType;

    private boolean spawned;

    private boolean invisible;

    private HashMap<String, Object> scripts;

    public WorldObject(int objectId) {
        setInstanceType(InstanceType.WorldObject);
        this.objectId = objectId;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void setName(String name) {
        WorldPartition.checkOwner();
        this.name = name;
    }

    public int getObjectId() {
        WorldPartition.checkOwner();
        return objectId;
    }

    // For toString only: logging calls it from any thread, where the checked getter would throw.
    protected final int uncheckedObjectId() {
        return objectId;
    }

    public WorldRegion getWorldRegion() {
        WorldPartition.checkOwner();
        return worldRegion;
    }

    @Override
    public Location getLocation() {
        WorldPartition.checkOwner();
        return new Location(
                location.getX(), location.getY(), location.getZ(), location.getHeading(), location.getInstanceId());
    }

    public InstanceType getInstanceType() {
        WorldPartition.checkOwner();
        return instanceType;
    }

    protected void setInstanceType(InstanceType instanceType) {
        this.instanceType = instanceType;
    }

    public boolean isSpawned() {
        WorldPartition.checkOwner();
        return spawned;
    }

    public void setSpawned(boolean spawned) {
        WorldPartition.checkOwner();
        this.spawned = spawned;
    }

    public boolean isInvisible() {
        WorldPartition.checkOwner();
        return invisible;
    }

    public boolean isInstanceTypes(InstanceType... instanceTypes) {
        WorldPartition.checkOwner();
        return instanceType.isTypes(instanceTypes);
    }

    public void onAction(Player player) {
        WorldPartition.checkOwner();
        onAction(player, true);
    }

    public void onAction(Player player, boolean interact) {
        WorldPartition.checkOwner();
        final IActionClickHandler handler = ActionClickHandler.getInstance().getHandler(getInstanceType());
        if (handler != null) {
            handler.onAction(player, this, interact);
        }

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    public void onActionShift(Player player) {
        WorldPartition.checkOwner();
        final IActionShiftHandler handler = ActionShiftHandler.getInstance().getHandler(getInstanceType());
        if (handler != null) {
            handler.onAction(player, this, true);
        }

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    public void onForcedAttack(Player player) {
        WorldPartition.checkOwner();
        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    public void onSpawn() {
        WorldPartition.checkOwner();
    }

    public boolean decayMe() {
        WorldPartition.checkOwner();
        spawned = false;
        World.removeVisibleObject(this, worldRegion);
        World.removeObject(this);
        return true;
    }

    // The new id changes hashCode: take the object out of every hash-based collection before calling this.
    public void refreshId() {
        WorldPartition.checkOwner();
        World.removeObject(this);
        IdManager.getInstance().releaseId(getObjectId());
        objectId = IdManager.getInstance().getNextId();
    }

    public boolean spawnMe() {
        WorldPartition.checkOwner();
        spawned = true;
        setWorldRegion(World.getRegion(this));

        World.addObject(this);

        worldRegion.addVisibleObject(this);

        World.addVisibleObject(this, getWorldRegion());

        onSpawn();

        return true;
    }

    public void spawnMe(int x, int y, int z) {
        WorldPartition.checkOwner();
        int spawnX = x;
        if (spawnX > World.WORLD_X_MAX) {
            spawnX = World.WORLD_X_MAX - 5000;
        }

        if (spawnX < World.WORLD_X_MIN) {
            spawnX = World.WORLD_X_MIN + 5000;
        }

        int spawnY = y;
        if (spawnY > World.WORLD_Y_MAX) {
            spawnY = World.WORLD_Y_MAX - 5000;
        }

        if (spawnY < World.WORLD_Y_MIN) {
            spawnY = World.WORLD_Y_MIN + 5000;
        }

        // Set before spawning: once spawned, setXYZ would update the world region itself.
        setXYZ(spawnX, spawnY, z);

        spawnMe();
    }

    public boolean canBeAttacked() {
        WorldPartition.checkOwner();
        return false;
    }

    public abstract boolean isAutoAttackable(Creature attacker);

    public int getId() {
        WorldPartition.checkOwner();
        return 0;
    }

    public abstract void sendInfo(Player player);

    public void sendPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
    }

    public void sendPacket(SystemMessageId id) {
        WorldPartition.checkOwner();
    }

    public Attackable asAttackable() {
        WorldPartition.checkOwner();
        return null;
    }

    public Creature asCreature() {
        WorldPartition.checkOwner();
        return null;
    }

    public Door asDoor() {
        WorldPartition.checkOwner();
        return null;
    }

    public Monster asMonster() {
        WorldPartition.checkOwner();
        return null;
    }

    public Npc asNpc() {
        WorldPartition.checkOwner();
        return null;
    }

    public Pet asPet() {
        WorldPartition.checkOwner();
        return null;
    }

    public Playable asPlayable() {
        WorldPartition.checkOwner();
        return null;
    }

    public Player asPlayer() {
        WorldPartition.checkOwner();
        return null;
    }

    public Servitor asServitor() {
        WorldPartition.checkOwner();
        return null;
    }

    public Summon asSummon() {
        WorldPartition.checkOwner();
        return null;
    }

    public boolean isArtefact() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isAttackable() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isCreature() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isCubic() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isDoor() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isFakePlayer() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isFence() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isItem() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isMonster() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isNpc() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isPet() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isPlayable() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isPlayer() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isServitor() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isSummon() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isTrap() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isVehicle() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isWalker() {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isTargetable() {
        WorldPartition.checkOwner();
        return true;
    }

    public boolean isInsideZone(ZoneId zone) {
        WorldPartition.checkOwner();
        return false;
    }

    public boolean isChargedShot(ShotType type) {
        WorldPartition.checkOwner();
        return false;
    }

    public void setChargedShot(ShotType type, boolean charged) {
        WorldPartition.checkOwner();
    }

    public void rechargeShots(boolean physical, boolean magical) {
        WorldPartition.checkOwner();
    }

    public <T> T addScript(T script) {
        WorldPartition.checkOwner();
        if (scripts == null) {
            scripts = new HashMap<>();
        }

        scripts.put(script.getClass().getName(), script);
        return script;
    }

    @SuppressWarnings("unchecked")
    public <T> T removeScript(Class<T> script) {
        WorldPartition.checkOwner();
        if (scripts == null) {
            return null;
        }

        return (T) scripts.remove(script.getName());
    }

    @SuppressWarnings("unchecked")
    public <T> T getScript(Class<T> script) {
        WorldPartition.checkOwner();
        if (scripts == null) {
            return null;
        }

        return (T) scripts.get(script.getName());
    }

    public void removeStatusListener(Creature object) {
        WorldPartition.checkOwner();
    }

    public void setXYZInvisible(int x, int y, int z) {
        WorldPartition.checkOwner();
        int correctX = x;
        if (correctX > World.WORLD_X_MAX) {
            correctX = World.WORLD_X_MAX - 5000;
        }

        if (correctX < World.WORLD_X_MIN) {
            correctX = World.WORLD_X_MIN + 5000;
        }

        int correctY = y;
        if (correctY > World.WORLD_Y_MAX) {
            correctY = World.WORLD_Y_MAX - 5000;
        }

        if (correctY < World.WORLD_Y_MIN) {
            correctY = World.WORLD_Y_MIN + 5000;
        }

        setXYZ(correctX, correctY, z);
        setSpawned(false);
    }

    public void setLocationInvisible(ILocational loc) {
        WorldPartition.checkOwner();
        setXYZInvisible(loc.getX(), loc.getY(), loc.getZ());
    }

    public void setWorldRegion(WorldRegion region) {
        WorldPartition.checkOwner();
        if ((region == null) && (worldRegion != null)) {
            worldRegion.removeVisibleObject(this);
        }

        worldRegion = region;
    }

    @Override
    public int getX() {
        WorldPartition.checkOwner();
        return location.getX();
    }

    @Override
    public int getY() {
        WorldPartition.checkOwner();
        return location.getY();
    }

    @Override
    public int getZ() {
        WorldPartition.checkOwner();
        return location.getZ();
    }

    @Override
    public int getHeading() {
        WorldPartition.checkOwner();
        return location.getHeading();
    }

    @Override
    public int getInstanceId() {
        WorldPartition.checkOwner();
        return location.getInstanceId();
    }

    public boolean isInInstance() {
        WorldPartition.checkOwner();
        return location.getInstanceId() > 0;
    }

    @Override
    public void setXYZ(int newX, int newY, int newZ) {
        WorldPartition.checkOwner();
        location.setXYZ(newX, newY, newZ);

        if (spawned) {
            final WorldRegion newRegion = World.getRegion(this);
            if ((newRegion != null) && (newRegion != worldRegion)) {
                if (worldRegion != null) {
                    worldRegion.removeVisibleObject(this);
                }

                newRegion.addVisibleObject(this);
                World.switchRegion(this, newRegion);
                setWorldRegion(newRegion);
            }
        }
    }

    @Override
    public void setXYZ(ILocational loc) {
        WorldPartition.checkOwner();
        setXYZ(loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public void setHeading(int newHeading) {
        WorldPartition.checkOwner();
        location.setHeading(newHeading);
    }

    /** 0 is the global instance. */
    @Override
    public void setInstanceId(int instanceId) {
        WorldPartition.checkOwner();
        final int oldInstanceId = getInstanceId();
        if ((instanceId < 0) || (oldInstanceId == instanceId)) {
            return;
        }

        final Instance newI = InstanceManager.getInstance().getInstance(instanceId);
        if (newI == null) {
            return;
        }

        final Instance oldI = InstanceManager.getInstance().getInstance(oldInstanceId);

        if (isPlayer()) {
            final Player player = asPlayer();
            if ((oldInstanceId > 0) && (oldI != null)) {
                oldI.removePlayer(objectId);
                if (oldI.isShowTimer()) {
                    sendInstanceUpdate(oldI, true);
                }
            }

            if (instanceId > 0) {
                newI.addPlayer(objectId);
                if (newI.isShowTimer()) {
                    sendInstanceUpdate(newI, false);
                }
            }

            if (player.hasSummon()) {
                player.getSummon().setInstanceId(instanceId);
            }
        } else if (isNpc()) {
            final Npc npc = asNpc();
            if ((oldInstanceId > 0) && (oldI != null)) {
                oldI.removeNpc(npc);
            }

            if (instanceId > 0) {
                newI.addNpc(npc);
            }
        }

        location.setInstanceId(instanceId);
    }

    private void sendInstanceUpdate(Instance instance, boolean hide) {
        final int startTime = (int) ((ClockBridge.gameMillis() - instance.getInstanceStartTime()) / 1000);
        final int endTime = (int) ((instance.getInstanceEndTime() - instance.getInstanceStartTime()) / 1000);
        if (instance.isTimerIncrease()) {
            sendPacket(new ExSendUIEvent(asPlayer(), hide, true, startTime, endTime, instance.getTimerText()));
        } else {
            sendPacket(new ExSendUIEvent(asPlayer(), hide, false, endTime - startTime, 0, instance.getTimerText()));
        }
    }

    @Override
    public void setLocation(Location loc) {
        WorldPartition.checkOwner();
        location.setXYZ(loc.getX(), loc.getY(), loc.getZ());
        location.setHeading(loc.getHeading());
        location.setInstanceId(loc.getInstanceId());
    }

    public double calculateDistance2D(int x, int y, int z) {
        WorldPartition.checkOwner();
        return distance2D(x, y);
    }

    public double calculateDistance2D(ILocational loc) {
        WorldPartition.checkOwner();
        if (loc == null) {
            return Double.MAX_VALUE;
        }

        return distance2D(loc.getX(), loc.getY());
    }

    public double calculateDistance3D(int x, int y, int z) {
        WorldPartition.checkOwner();
        return distance3D(x, y, z);
    }

    public double calculateDistance3D(ILocational loc) {
        WorldPartition.checkOwner();
        if (loc == null) {
            return Double.MAX_VALUE;
        }

        return distance3D(loc.getX(), loc.getY(), loc.getZ());
    }

    public double calculateDirectionTo(ILocational target) {
        WorldPartition.checkOwner();
        return LocationUtil.calculateAngleFrom(this, target);
    }

    public void setInvisible(boolean invisible) {
        WorldPartition.checkOwner();
        this.invisible = invisible;

        if (invisible) {
            final DeleteObject deletePacket = new DeleteObject(this);
            World.forEachVisibleObject(this, Player.class, player -> {
                if (!isVisibleFor(player)) {
                    player.sendPacket(deletePacket);
                }
            });
        }

        broadcastInfo();
    }

    public boolean isVisibleFor(Player player) {
        WorldPartition.checkOwner();
        return !invisible || player.isGM();
    }

    public void broadcastInfo() {
        WorldPartition.checkOwner();
        World.forEachVisibleObject(this, Player.class, player -> {
            if (isVisibleFor(player)) {
                sendInfo(player);
            }
        });
    }

    public boolean isInSurroundingRegion(WorldObject worldObject) {
        WorldPartition.checkOwner();
        if (worldObject == null) {
            return false;
        }

        final WorldRegion worldRegion = worldObject.getWorldRegion();
        if (worldRegion == null) {
            return false;
        }

        if (this.worldRegion == null) {
            return false;
        }

        return worldRegion.isSurroundingRegion(this.worldRegion);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof WorldObject other) && (other.objectId == objectId);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(objectId);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(':');
        sb.append(name);
        sb.append('[');
        sb.append(objectId);
        sb.append(']');
        return sb.toString();
    }

    // Owner checked once by callers; position read directly because these are the hottest geometry paths.
    private double distance2D(int x, int y) {
        final double dx = x - location.getX();
        final double dy = y - location.getY();
        return Math.sqrt((dx * dx) + (dy * dy));
    }

    private double distance3D(int x, int y, int z) {
        final double dx = x - location.getX();
        final double dy = y - location.getY();
        final double dz = z - location.getZ();
        return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }
}
