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
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.Fence;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.RandomAnimationTaskManager;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Collections handed out are read-only: the object sets as snapshots, as in {@link World}; the surrounding regions,
 * fixed when the world is set up, as one immutable list.
 */
@SingleWriter
public class WorldRegion {
    private final ObjectArraySet<WorldObject> visibleObjects = new ObjectArraySet<>();
    private final ObjectArraySet<Door> doors = new ObjectArraySet<>();
    private final ObjectArraySet<Fence> fences = new ObjectArraySet<>();
    private WorldRegion[] surroundingRegions;

    private List<WorldRegion> surroundingRegionList;

    private final int regionX;

    private final int regionY;

    private final int regionZ;

    private boolean active = GeneralConfig.GRIDS_ALWAYS_ON;

    private ScheduledFuture<?> neighborsTask = null;
    private int activeNeighbors;

    public WorldRegion(int regionX, int regionY, int regionZ) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionZ = regionZ;
    }

    private void switchAI(boolean isOn) {
        if (visibleObjects.isEmpty()) {
            return;
        }

        // A snapshot: mobs too far from their spawn teleport back, which moves them out of this region. An object an
        // earlier iteration took out of the world is skipped.
        try (ScratchBuffers.Snapshot<WorldObject> objects = ScratchBuffers.snapshot(visibleObjects)) {
            for (int i = 0; i < objects.size(); i++) {
                final WorldObject wo = objects.get(i);
                if (!wo.isSpawned()) {
                    continue;
                }

                if (isOn) {
                    startAI(wo);
                } else {
                    stopAI(wo);
                }
            }
        }
    }

    private static void stopAI(WorldObject wo) {
        if (wo.isAttackable()) {
            final Attackable mob = wo.asAttackable();

            mob.setTarget(null);

            mob.stopMove(null);

            mob.stopAllEffects();

            mob.clearAggroList();
            mob.clearAttackByList();

            final Spawn spawn = mob.getSpawn();
            if ((spawn != null) && (mob.calculateDistance2D(spawn) > NpcConfig.MAX_DRIFT_RANGE)) {
                mob.teleToLocation(spawn);
            }

            if (mob.hasAI()) {
                mob.getAI().setIntentionIdle();
                mob.getAI().stopAITask();
            }

            mob.abortAttack();

            RandomAnimationTaskManager.getInstance().remove(mob);
        } else if (wo.isNpc()) {
            RandomAnimationTaskManager.getInstance().remove(wo.asNpc());
        }
    }

    private static void startAI(WorldObject wo) {
        if (wo.isAttackable()) {
            wo.asAttackable().getStatus().startHpMpRegeneration();
            RandomAnimationTaskManager.getInstance().add(wo.asNpc());
        } else if (wo.isNpc()) {
            RandomAnimationTaskManager.getInstance().add(wo.asNpc());
        }
    }

    public void incrementActiveNeighbors() {
        WorldPartition.checkOwner();
        activeNeighbors++;
    }

    public void decrementActiveNeighbors() {
        WorldPartition.checkOwner();
        activeNeighbors--;
    }

    public boolean areNeighborsActive() {
        WorldPartition.checkOwner();
        return GeneralConfig.GRIDS_ALWAYS_ON || (activeNeighbors > 0);
    }

    public boolean areNeighborsEmpty() {
        WorldPartition.checkOwner();
        for (int i = 0; i < surroundingRegions.length; i++) {
            final WorldRegion worldRegion = surroundingRegions[i];
            if (worldRegion.isActive()) {
                final Collection<WorldObject> regionObjects = worldRegion.visibleObjectsView();
                if (regionObjects.isEmpty()) {
                    continue;
                }

                for (WorldObject wo : regionObjects) {
                    if ((wo != null) && wo.isPlayable()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public void setActive(boolean value) {
        WorldPartition.checkOwner();
        if (active == value) {
            return;
        }

        active = value;

        if (value) {
            for (int i = 0; i < surroundingRegions.length; i++) {
                surroundingRegions[i].incrementActiveNeighbors();
            }
        } else {
            for (int i = 0; i < surroundingRegions.length; i++) {
                surroundingRegions[i].decrementActiveNeighbors();
            }
        }

        switchAI(value);
    }

    /** Activates self at once but neighbors after a delay, so a player teleporting in and straight out wakes none. */
    private void startActivation() {
        setActive(true);

        if (neighborsTask != null) {
            neighborsTask.cancel(true);
            neighborsTask = null;
        }

        neighborsTask = WorldPartition.loop()
                .schedule(
                        () -> {
                            for (int i = 0; i < surroundingRegions.length; i++) {
                                surroundingRegions[i].setActive(true);
                            }
                        },
                        1000 * GeneralConfig.GRID_NEIGHBOR_TURNON_TIME,
                        TimeUnit.MILLISECONDS);
    }

    /** Deactivation is delayed, since a player who just left may come straight back. */
    private void startDeactivation() {
        if (neighborsTask != null) {
            neighborsTask.cancel(true);
            neighborsTask = null;
        }

        neighborsTask = WorldPartition.loop()
                .schedule(
                        () -> {
                            for (int i = 0; i < surroundingRegions.length; i++) {
                                final WorldRegion worldRegion = surroundingRegions[i];
                                if (worldRegion.areNeighborsEmpty()) {
                                    worldRegion.setActive(false);
                                }
                            }
                        },
                        1000 * GeneralConfig.GRID_NEIGHBOR_TURNOFF_TIME,
                        TimeUnit.MILLISECONDS);
    }

    public void addVisibleObject(WorldObject object) {
        WorldPartition.checkOwner();
        if (object == null) {
            return;
        }

        visibleObjects.add(object);

        if (object.isDoor()) {
            for (int i = 0; i < surroundingRegions.length; i++) {
                surroundingRegions[i].addDoor(object.asDoor());
            }
        } else if (object.isFence()) {
            for (int i = 0; i < surroundingRegions.length; i++) {
                surroundingRegions[i].addFence((Fence) object);
            }
        }

        if (object.isPlayable() && !active && !GeneralConfig.GRIDS_ALWAYS_ON) {
            startActivation();
        }
    }

    public void removeVisibleObject(WorldObject object) {
        WorldPartition.checkOwner();
        if (object == null) {
            return;
        }

        if (visibleObjects.isEmpty()) {
            return;
        }

        visibleObjects.remove(object);

        if (object.isDoor()) {
            for (int i = 0; i < surroundingRegions.length; i++) {
                surroundingRegions[i].removeDoor(object.asDoor());
            }
        } else if (object.isFence()) {
            for (int i = 0; i < surroundingRegions.length; i++) {
                surroundingRegions[i].removeFence((Fence) object);
            }
        }

        if (object.isPlayable() && areNeighborsEmpty() && !GeneralConfig.GRIDS_ALWAYS_ON) {
            startDeactivation();
        }
    }

    public Collection<WorldObject> getVisibleObjects() {
        WorldPartition.checkOwner();
        return List.copyOf(visibleObjects);
    }

    /** Live: only for loops that filter into a local result and call nothing that can move an object. */
    Collection<WorldObject> visibleObjectsView() {
        return visibleObjects;
    }

    public void addDoor(Door door) {
        WorldPartition.checkOwner();
        doors.add(door);
    }

    private void removeDoor(Door door) {
        doors.remove(door);
    }

    public boolean hasDoors() {
        WorldPartition.checkOwner();
        return !doors.isEmpty();
    }

    /** The predicate runs on a copy, never during a live walk, so it may change game state. */
    public boolean anyDoor(Predicate<Door> test) {
        WorldPartition.checkOwner();
        return anyOf(doors, test);
    }

    public boolean hasFences() {
        WorldPartition.checkOwner();
        return !fences.isEmpty();
    }

    /** The predicate runs on a copy, never during a live walk, so it may change game state. */
    public boolean anyFence(Predicate<Fence> test) {
        WorldPartition.checkOwner();
        return anyOf(fences, test);
    }

    private static <T> boolean anyOf(Collection<T> source, Predicate<T> test) {
        try (ScratchBuffers.Snapshot<T> snapshot = ScratchBuffers.snapshot(source)) {
            for (int i = 0; i < snapshot.size(); i++) {
                if (test.test(snapshot.get(i))) {
                    return true;
                }
            }

            return false;
        }
    }

    public void addFence(Fence fence) {
        WorldPartition.checkOwner();
        fences.add(fence);
    }

    private void removeFence(Fence fence) {
        fences.remove(fence);
    }

    public void setSurroundingRegions(WorldRegion[] regions) {
        WorldPartition.checkOwner();
        surroundingRegions = regions.clone();

        // Self first, so bulk operations update this region first.
        for (int i = 0; i < surroundingRegions.length; i++) {
            if (surroundingRegions[i] == this) {
                final WorldRegion first = surroundingRegions[0];
                surroundingRegions[0] = this;
                surroundingRegions[i] = first;
            }
        }

        surroundingRegionList = List.of(surroundingRegions);
    }

    public boolean isSurroundingRegion(WorldRegion region) {
        WorldPartition.checkOwner();
        if (region == null) {
            return false;
        }

        return (Math.abs(regionX - region.regionX) <= 1)
                && (Math.abs(regionY - region.regionY) <= 1)
                && (Math.abs(regionZ - region.regionZ) <= 1);
    }

    public List<WorldRegion> getSurroundingRegions() {
        WorldPartition.checkOwner();
        return surroundingRegionList;
    }

    /** The internal array, for the hot loops of {@link World}: never handed out further and never written. */
    WorldRegion[] surroundingRegionsArray() {
        return surroundingRegions;
    }

    public int getRegionX() {
        WorldPartition.checkOwner();
        return regionX;
    }

    public int getRegionY() {
        WorldPartition.checkOwner();
        return regionY;
    }

    public int getRegionZ() {
        WorldPartition.checkOwner();
        return regionZ;
    }

    public boolean isActive() {
        WorldPartition.checkOwner();
        return active;
    }

    @Override
    public String toString() {
        return "(" + regionX + ", " + regionY + ", " + regionZ + ")";
    }
}
