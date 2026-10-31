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
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.DeleteObject;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExShowScreenMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Collections handed out are read-only snapshots, since legacy callers mutate the world while iterating them.
 * A loop that calls into game objects or caller predicates walks a snapshot, since a call can move an object between
 * regions, and skips objects taken out meanwhile; only pure filters walk a live region view.
 */
@Slf4j
@SingleWriter
public class World {

    /** Gracia border Flying objects not allowed to the east of it. */
    public static final int GRACIA_MAX_X = -166168;

    public static final int GRACIA_MAX_Z = 6105;
    public static final int GRACIA_MIN_Z = -895;

    /** Regions are {@code 1 << SHIFT_BY} wide; 11 divides a 32768 tile into 16x16 regions. */
    public static final int SHIFT_BY = 11;

    public static final int TILE_SIZE = 32768;

    public static final int TILE_X_MIN = 11;

    public static final int TILE_Y_MIN = 10;
    public static final int TILE_X_MAX = 26;
    public static final int TILE_Y_MAX = 26;
    public static final int TILE_ZERO_COORD_X = 20;
    public static final int TILE_ZERO_COORD_Y = 18;
    public static final int WORLD_X_MIN = (TILE_X_MIN - TILE_ZERO_COORD_X) * TILE_SIZE;
    public static final int WORLD_Y_MIN = (TILE_Y_MIN - TILE_ZERO_COORD_Y) * TILE_SIZE;

    public static final int WORLD_X_MAX = ((TILE_X_MAX - TILE_ZERO_COORD_X) + 1) * TILE_SIZE;
    public static final int WORLD_Y_MAX = ((TILE_Y_MAX - TILE_ZERO_COORD_Y) + 1) * TILE_SIZE;

    public static final int WORLD_Z_MIN = -16000;

    public static final int WORLD_Z_MAX = 16000;
    public static final int Z_REGION_SIZE = 2000;

    public static final int OFFSET_X = Math.abs(WORLD_X_MIN >> SHIFT_BY);

    public static final int OFFSET_Y = Math.abs(WORLD_Y_MIN >> SHIFT_BY);

    private static final int REGIONS_X = (WORLD_X_MAX >> SHIFT_BY) + OFFSET_X;

    private static final int REGIONS_Y = (WORLD_Y_MAX >> SHIFT_BY) + OFFSET_Y;
    private static final int REGIONS_Z = (Math.abs(WORLD_Z_MIN) + Math.abs(WORLD_Z_MAX)) / Z_REGION_SIZE;

    private static final Int2ObjectOpenHashMap<Player> allPlayers = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Player> allGoodPlayers = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Player> allEvilPlayers = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<WorldObject> allObjects = new Int2ObjectOpenHashMap<>();
    /** Keyed by owner id. */
    private static final Int2ObjectOpenHashMap<Pet> petsInstance = new Int2ObjectOpenHashMap<>();

    private static final WorldRegion[][][] worldRegions = new WorldRegion[REGIONS_X + 1][REGIONS_Y + 1][REGIONS_Z];

    /** Range of a walk without a distance bound; no two points of the world are that far apart. */
    private static final int UNBOUNDED = Integer.MAX_VALUE;

    private World() {}

    public static void init() {
        WorldPartition.checkOwner();
        for (int x = 0; x <= REGIONS_X; x++) {
            for (int y = 0; y <= REGIONS_Y; y++) {
                for (int z = 0; z < REGIONS_Z; z++) {
                    worldRegions[x][y][z] = new WorldRegion(x, y, z);
                }
            }
        }

        for (int rx = 0; rx <= REGIONS_X; rx++) {
            for (int ry = 0; ry <= REGIONS_Y; ry++) {
                for (int rz = 0; rz < REGIONS_Z; rz++) {
                    final List<WorldRegion> surroundingRegions = new ArrayList<>();
                    for (int sx = rx - 1; sx <= (rx + 1); sx++) {
                        for (int sy = ry - 1; sy <= (ry + 1); sy++) {
                            for (int sz = rz - 1; sz <= (rz + 1); sz++) {
                                if (((sx >= 0)
                                        && (sx < REGIONS_X)
                                        && (sy >= 0)
                                        && (sy < REGIONS_Y)
                                        && (sz >= 0)
                                        && (sz < REGIONS_Z))) {
                                    surroundingRegions.add(worldRegions[sx][sy][sz]);
                                }
                            }
                        }
                    }

                    WorldRegion[] regionArray = new WorldRegion[surroundingRegions.size()];
                    regionArray = surroundingRegions.toArray(regionArray);
                    worldRegions[rx][ry][rz].setSurroundingRegions(regionArray);
                }
            }
        }

        log.info("({} by {}) World Region Grid set up.", REGIONS_X, REGIONS_Y);
    }

    public static void addObject(WorldObject object) {
        WorldPartition.checkOwner();
        allObjects.putIfAbsent(object.getObjectId(), object);

        if (object.isPlayer()) {
            final Player newPlayer = object.asPlayer();
            if (newPlayer.isTeleporting()) // TODO: Drop when we stop removing player from the world while teleporting.
            {
                return;
            }

            final Player existingPlayer = allPlayers.putIfAbsent(object.getObjectId(), newPlayer);
            if (existingPlayer != null) {
                Disconnection.of(existingPlayer).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                Disconnection.of(newPlayer).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                log.warn("Duplicate character!? Disconnected both characters ({})", newPlayer.getName());
            } else if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
                addFactionPlayerToWorld(newPlayer);
            }
        }
    }

    public static void removeObject(WorldObject object) {
        WorldPartition.checkOwner();
        allObjects.remove(object.getObjectId());
        if (object.isPlayer()) {
            final Player player = object.asPlayer();
            if (player.isTeleporting()) // TODO: Drop when we stop removing player from the world while teleporting.
            {
                return;
            }

            allPlayers.remove(object.getObjectId());

            if (FactionSystemConfig.FACTION_SYSTEM_ENABLED) {
                if (player.isGood()) {
                    allGoodPlayers.remove(player.getObjectId());
                } else if (player.isEvil()) {
                    allEvilPlayers.remove(player.getObjectId());
                }
            }
        }
    }

    public static WorldObject findObject(int objectId) {
        WorldPartition.checkOwner();
        return allObjects.get(objectId);
    }

    public static Collection<WorldObject> getVisibleObjects() {
        WorldPartition.checkOwner();
        return snapshotOf(allObjects);
    }

    public static int getVisibleObjectsCount() {
        WorldPartition.checkOwner();
        return allObjects.size();
    }

    public static int getPlayersCount() {
        WorldPartition.checkOwner();
        return allPlayers.size();
    }

    public static Collection<Player> getPlayers() {
        WorldPartition.checkOwner();
        return snapshotOf(allPlayers);
    }

    public static Collection<Player> getAllGoodPlayers() {
        WorldPartition.checkOwner();
        return snapshotOf(allGoodPlayers);
    }

    public static Collection<Player> getAllEvilPlayers() {
        WorldPartition.checkOwner();
        return snapshotOf(allEvilPlayers);
    }

    public static int getGoodPlayersCount() {
        WorldPartition.checkOwner();
        return allGoodPlayers.size();
    }

    public static int getEvilPlayersCount() {
        WorldPartition.checkOwner();
        return allEvilPlayers.size();
    }

    // One pass over the table into an exact-size array; List.copyOf goes through the iterator and copies twice.
    private static <V> List<V> snapshotOf(Int2ObjectOpenHashMap<V> map) {
        final ObjectArrayList<V> snapshot = new ObjectArrayList<>(map.size());
        map.values().forEach(snapshot::add);
        return ObjectLists.unmodifiable(snapshot);
    }

    public static Player getPlayer(String name) {
        WorldPartition.checkOwner();
        return getPlayer(CharInfoTable.getInstance().getIdByName(name));
    }

    public static Player getPlayer(int objectId) {
        WorldPartition.checkOwner();
        return allPlayers.get(objectId);
    }

    public static Pet getPet(int ownerId) {
        WorldPartition.checkOwner();
        return petsInstance.get(ownerId);
    }

    public static Pet addPet(int ownerId, Pet pet) {
        WorldPartition.checkOwner();
        return petsInstance.put(ownerId, pet);
    }

    public static void removePet(int ownerId) {
        WorldPartition.checkOwner();
        petsInstance.remove(ownerId);
    }

    /** Scans every visible object. */
    public static Npc getNpc(int npcId) {
        WorldPartition.checkOwner();
        for (WorldObject wo : allObjects.values()) {
            if (wo.isNpc() && (wo.getId() == npcId)) {
                return wo.asNpc();
            }
        }

        return null;
    }

    public static void addVisibleObject(WorldObject object, WorldRegion newRegion) {
        WorldPartition.checkOwner();
        if (!newRegion.isActive()) {
            return;
        }

        forEachVisibleObject(object, WorldObject.class, wo -> reveal(object, wo));
    }

    public static void addFactionPlayerToWorld(Player player) {
        WorldPartition.checkOwner();
        if (player.isGood()) {
            allGoodPlayers.put(player.getObjectId(), player);
        } else if (player.isEvil()) {
            allEvilPlayers.put(player.getObjectId(), player);
        }
    }

    public static void removeVisibleObject(WorldObject object, WorldRegion oldRegion) {
        WorldPartition.checkOwner();
        if ((object == null) || (oldRegion == null)) {
            return;
        }

        oldRegion.removeVisibleObject(object);

        final ObjectArrayList<WorldObject> snapshot = ScratchBuffers.acquire();
        try {
            final WorldRegion[] surroundingRegions = oldRegion.surroundingRegionsArray();
            for (int i = 0; i < surroundingRegions.length; i++) {
                final WorldRegion worldRegion = surroundingRegions[i];
                if (worldRegion.visibleObjectsView().isEmpty()) {
                    continue;
                }

                ScratchBuffers.copy(worldRegion.visibleObjectsView(), snapshot);
                for (int j = 0; j < snapshot.size(); j++) {
                    final WorldObject wo = snapshot.get(j);
                    if ((wo != object) && wo.isSpawned()) {
                        forget(object, wo);
                    }
                }
            }
        } finally {
            ScratchBuffers.release(snapshot);
        }
    }

    public static void switchRegion(WorldObject object, WorldRegion newRegion) {
        WorldPartition.checkOwner();
        final WorldRegion oldRegion = object.getWorldRegion();
        if ((oldRegion == null) || (oldRegion == newRegion)) {
            return;
        }

        final ObjectArrayList<WorldObject> snapshot = ScratchBuffers.acquire();
        try {
            final WorldRegion[] oldSurroundingRegions = oldRegion.surroundingRegionsArray();
            for (int i = 0; i < oldSurroundingRegions.length; i++) {
                final WorldRegion worldRegion = oldSurroundingRegions[i];
                if (newRegion.isSurroundingRegion(worldRegion)
                        || worldRegion.visibleObjectsView().isEmpty()) {
                    continue;
                }

                ScratchBuffers.copy(worldRegion.visibleObjectsView(), snapshot);
                for (int j = 0; j < snapshot.size(); j++) {
                    final WorldObject wo = snapshot.get(j);
                    if ((wo != object) && wo.isSpawned()) {
                        forget(object, wo);
                    }
                }
            }

            final WorldRegion[] newSurroundingRegions = newRegion.surroundingRegionsArray();
            for (int i = 0; i < newSurroundingRegions.length; i++) {
                final WorldRegion worldRegion = newSurroundingRegions[i];
                if (oldRegion.isSurroundingRegion(worldRegion)
                        || worldRegion.visibleObjectsView().isEmpty()) {
                    continue;
                }

                ScratchBuffers.copy(worldRegion.visibleObjectsView(), snapshot);
                for (int j = 0; j < snapshot.size(); j++) {
                    final WorldObject wo = snapshot.get(j);
                    if ((wo != object) && wo.isSpawned() && (wo.getInstanceId() == object.getInstanceId())) {
                        reveal(object, wo);
                    }
                }
            }
        } finally {
            ScratchBuffers.release(snapshot);
        }
    }

    private static void reveal(WorldObject object, WorldObject wo) {
        if (object.isPlayer() && wo.isVisibleFor(object.asPlayer())) {
            wo.sendInfo(object.asPlayer());
            if (wo.isCreature()) {
                final CreatureAI ai = wo.asCreature().getAI();
                if (ai != null) {
                    ai.describeStateToPlayer(object.asPlayer());
                    if (wo.isMonster() && (ai.getIntention() == Intention.IDLE)) {
                        ai.setIntentionActive();
                    }
                }
            }
        }

        if (wo.isPlayer() && object.isVisibleFor(wo.asPlayer())) {
            object.sendInfo(wo.asPlayer());
            if (object.isCreature()) {
                final CreatureAI ai = object.asCreature().getAI();
                if (ai != null) {
                    ai.describeStateToPlayer(wo.asPlayer());
                    if (object.isMonster() && (ai.getIntention() == Intention.IDLE)) {
                        ai.setIntentionActive();
                    }
                }
            }
        }
    }

    private static void forget(WorldObject object, WorldObject wo) {
        if (object.isCreature()) {
            final Creature objectCreature = object.asCreature();
            final CreatureAI ai = objectCreature.getAI();
            if (ai != null) {
                ai.notifyActionForgetObject(wo);
            }

            if (objectCreature.getTarget() == wo) {
                objectCreature.setTarget(null);
            }

            if (object.isPlayer()) {
                object.sendPacket(new DeleteObject(wo));
            }
        }

        if (wo.isCreature()) {
            final Creature woCreature = wo.asCreature();
            final CreatureAI ai = woCreature.getAI();
            if (ai != null) {
                ai.notifyActionForgetObject(object);
            }

            if (woCreature.getTarget() == object) {
                woCreature.setTarget(null);
            }

            if (wo.isPlayer()) {
                wo.sendPacket(new DeleteObject(object));
            }
        }
    }

    /**
     * Matches share {@code object}'s instance and exclude it. Returns a mutable list, empty when {@code object} is
     * null or has no region.
     */
    public static <T extends WorldObject> List<T> getVisibleObjects(WorldObject object, Class<T> clazz) {
        WorldPartition.checkOwner();
        return collectVisible(object, clazz, false, UNBOUNDED);
    }

    /**
     * Matches share {@code object}'s instance and exclude it. Returns a mutable list, empty when {@code object} is
     * null or has no region.
     */
    public static <T extends WorldObject> List<T> getVisibleObjectsInRange(
            WorldObject object, Class<T> clazz, int range) {
        WorldPartition.checkOwner();
        return collectVisible(object, clazz, false, range);
    }

    /**
     * Matches share {@code object}'s instance and exclude it. Returns a mutable list, empty when {@code object} is
     * null or has no region.
     */
    public static <T extends WorldObject> List<T> getVisibleObjects(
            WorldObject object, Class<T> clazz, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        final List<T> result = getVisibleObjects(object, clazz);
        result.removeIf(candidate -> !predicate.test(candidate));
        return result;
    }

    /**
     * Matches share {@code object}'s instance and exclude it. Returns a mutable list, empty when {@code object} is
     * null or has no region.
     */
    public static <T extends WorldObject> List<T> getVisibleObjectsInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        final List<T> result = getVisibleObjectsInRange(object, clazz, range);
        result.removeIf(candidate -> !predicate.test(candidate));
        return result;
    }

    private static <T extends WorldObject> List<T> collectVisible(
            WorldObject object, Class<T> clazz, boolean includeSelf, int range) {
        final List<T> matches = new ArrayList<>();
        walkVisible(object, clazz, includeSelf, range, matches, World::addMatch);
        return matches;
    }

    // Copies the regions whole and filters at each turn rather than filtering into the copy: every object is touched
    // once, as in a live walk.
    private static <T extends WorldObject> void forEachMatch(
            WorldObject object, Class<T> clazz, int range, Consumer<T> action) {
        final WorldRegion worldRegion = regionOf(object);
        if (worldRegion == null) {
            return;
        }

        final ObjectArrayList<WorldObject> candidates = ScratchBuffers.acquire();
        try {
            final WorldRegion[] surroundingRegions = worldRegion.surroundingRegionsArray();
            for (int i = 0; i < surroundingRegions.length; i++) {
                candidates.addAll(surroundingRegions[i].visibleObjectsView());
            }

            for (int i = 0; i < candidates.size(); i++) {
                final WorldObject wo = candidates.get(i);
                if (wo.isSpawned() && isMatch(wo, object, clazz, false, object.getInstanceId(), range)) {
                    action.accept(clazz.cast(wo));
                }
            }
        } finally {
            ScratchBuffers.release(candidates);
        }
    }

    private static <T extends WorldObject> T nearestMatching(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate) {
        final Scan scan = new Scan().start(object, UNBOUNDED);
        visitCandidates(
                object, clazz, range, scan, (s, candidate) -> predicate.test(candidate) && s.offerNearest(candidate));
        return scan.finish(clazz);
    }

    private static <T extends WorldObject> T randomMatching(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate) {
        final Scan scan = new Scan().start(object, UNBOUNDED);
        visitCandidates(
                object, clazz, range, scan, (s, candidate) -> predicate.test(candidate) && s.offerRandom(candidate));
        return scan.finish(clazz);
    }

    private static <T extends WorldObject, S> T walkVisible(
            WorldObject object, Class<T> clazz, boolean includeSelf, int range, S state, MatchVisitor<T, S> visitor) {
        final WorldRegion worldRegion = regionOf(object);
        if (worldRegion == null) {
            return null;
        }

        final WorldRegion[] surroundingRegions = worldRegion.surroundingRegionsArray();
        for (int i = 0; i < surroundingRegions.length; i++) {
            final T stoppedAt = walkRegion(surroundingRegions[i], object, clazz, includeSelf, range, state, visitor);
            if (stoppedAt != null) {
                return stoppedAt;
            }
        }

        return null;
    }

    // The visitor may call out, so it runs over one region's candidates at a time, collected before its first call.
    private static <T extends WorldObject, S> T visitCandidates(
            WorldObject object, Class<T> clazz, int range, S state, MatchVisitor<T, S> visitor) {
        final WorldRegion worldRegion = regionOf(object);
        if (worldRegion == null) {
            return null;
        }

        final ObjectArrayList<T> candidates = ScratchBuffers.acquire();
        try {
            final WorldRegion[] surroundingRegions = worldRegion.surroundingRegionsArray();
            for (int i = 0; i < surroundingRegions.length; i++) {
                candidates.clear();
                walkRegion(surroundingRegions[i], object, clazz, false, range, candidates, World::addMatch);
                for (int j = 0; j < candidates.size(); j++) {
                    final T candidate = candidates.get(j);
                    if (visitor.visit(state, candidate)) {
                        return candidate;
                    }
                }
            }

            return null;
        } finally {
            ScratchBuffers.release(candidates);
        }
    }

    // The one live walk over a region: a pure filter, so nothing called here can move an object between regions.
    private static <T extends WorldObject, S> T walkRegion(
            WorldRegion region,
            WorldObject object,
            Class<T> clazz,
            boolean includeSelf,
            int range,
            S state,
            MatchVisitor<T, S> visitor) {
        final Collection<WorldObject> visibleObjects = region.visibleObjectsView();
        if (visibleObjects.isEmpty()) {
            return null;
        }

        final int instanceId = object.getInstanceId();
        for (WorldObject wo : visibleObjects) {
            if (!isMatch(wo, object, clazz, includeSelf, instanceId, range)) {
                continue;
            }

            final T match = clazz.cast(wo);
            if (visitor.visit(state, match)) {
                return match;
            }
        }

        return null;
    }

    private static boolean isMatch(
            WorldObject wo, WorldObject object, Class<?> clazz, boolean includeSelf, int instanceId, int range) {
        return (includeSelf || (wo != object))
                && clazz.isInstance(wo)
                && (wo.getInstanceId() == instanceId)
                && ((range == UNBOUNDED) || (wo.calculateDistance3D(object) <= range));
    }

    private static WorldRegion regionOf(WorldObject object) {
        return (object == null) ? null : getRegion(object);
    }

    private static <T> boolean addMatch(List<T> matches, T match) {
        matches.add(match);
        return false;
    }

    private static boolean stopAtFirst(Object state, WorldObject match) {
        return true;
    }

    /**
     * Candidates are collected before the first call of {@code c}, so {@code c} may add, move or remove objects;
     * one taken out of the world or out of reach meanwhile is skipped.
     */
    public static <T extends WorldObject> void forEachVisibleObject(WorldObject object, Class<T> clazz, Consumer<T> c) {
        WorldPartition.checkOwner();
        forEachMatch(object, clazz, UNBOUNDED, c);
    }

    /**
     * Candidates are collected before the first call of {@code c}, so {@code c} may add, move or remove objects;
     * one taken out of the world or out of reach meanwhile is skipped.
     */
    public static <T extends WorldObject> void forEachVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Consumer<T> c) {
        WorldPartition.checkOwner();
        forEachMatch(object, clazz, range, c);
    }

    /**
     * First hit in region iteration order, not the closest; {@code null} when none, or when {@code object} is null or
     * has no region.
     */
    public static <T extends WorldObject> T getFirstVisibleObject(WorldObject object, Class<T> clazz) {
        WorldPartition.checkOwner();
        return walkVisible(object, clazz, false, UNBOUNDED, null, World::stopAtFirst);
    }

    /**
     * First hit in region iteration order, not the closest; {@code null} when none, or when {@code object} is null or
     * has no region.
     */
    public static <T extends WorldObject> T getFirstVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range) {
        WorldPartition.checkOwner();
        return walkVisible(object, clazz, false, range, null, World::stopAtFirst);
    }

    /**
     * First hit in region iteration order, not the closest; {@code null} when none, or when {@code object} is null or
     * has no region.
     */
    public static <T extends WorldObject> T getFirstVisibleObject(
            WorldObject object, Class<T> clazz, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        return visitCandidates(object, clazz, UNBOUNDED, predicate, Predicate::test);
    }

    /**
     * First hit in region iteration order, not the closest; {@code null} when none, or when {@code object} is null or
     * has no region.
     */
    public static <T extends WorldObject> T getFirstVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        return visitCandidates(object, clazz, range, predicate, Predicate::test);
    }

    /**
     * Acts at most once, on the first hit in region iteration order, not the closest.
     */
    public static <T extends WorldObject> void forFirstVisibleObject(
            WorldObject object, Class<T> clazz, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getFirstVisibleObject(object, clazz);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once, on the first hit in region iteration order, not the closest.
     */
    public static <T extends WorldObject> void forFirstVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getFirstVisibleObjectInRange(object, clazz, range);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once, on the first hit in region iteration order, not the closest.
     */
    public static <T extends WorldObject> void forFirstVisibleObject(
            WorldObject object, Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getFirstVisibleObject(object, clazz, predicate);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once, on the first hit in region iteration order, not the closest.
     */
    public static <T extends WorldObject> void forFirstVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getFirstVisibleObjectInRange(object, clazz, range, predicate);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Inspects every candidate; prefer the {@code getFirst*} variant when any match will do. {@code null} when none.
     */
    public static <T extends WorldObject> T getNearestVisibleObject(WorldObject object, Class<T> clazz) {
        WorldPartition.checkOwner();
        final Scan scan = new Scan().start(object, UNBOUNDED);
        walkVisible(object, clazz, false, UNBOUNDED, scan, Scan::offerNearest);
        return scan.finish(clazz);
    }

    /**
     * Inspects every candidate; prefer the {@code getFirst*} variant when any match will do. {@code null} when none.
     */
    public static <T extends WorldObject> T getNearestVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range) {
        WorldPartition.checkOwner();
        // The scan checks the range itself, so each distance is computed once.
        final Scan scan = new Scan().start(object, range);
        walkVisible(object, clazz, false, UNBOUNDED, scan, Scan::offerNearest);
        return scan.finish(clazz);
    }

    /**
     * Inspects every candidate; prefer the {@code getFirst*} variant when any match will do. {@code null} when none.
     */
    public static <T extends WorldObject> T getNearestVisibleObject(
            WorldObject object, Class<T> clazz, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        return nearestMatching(object, clazz, UNBOUNDED, predicate);
    }

    /**
     * Inspects every candidate; prefer the {@code getFirst*} variant when any match will do. {@code null} when none.
     */
    public static <T extends WorldObject> T getNearestVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        return nearestMatching(object, clazz, range, predicate);
    }

    /**
     * Acts at most once. Inspects every candidate; prefer the {@code forFirst*} variant when any match will do.
     */
    public static <T extends WorldObject> void forNearestVisibleObject(
            WorldObject object, Class<T> clazz, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getNearestVisibleObject(object, clazz);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once. Inspects every candidate; prefer the {@code forFirst*} variant when any match will do.
     */
    public static <T extends WorldObject> void forNearestVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getNearestVisibleObjectInRange(object, clazz, range);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once. Inspects every candidate; prefer the {@code forFirst*} variant when any match will do.
     */
    public static <T extends WorldObject> void forNearestVisibleObject(
            WorldObject object, Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getNearestVisibleObject(object, clazz, predicate);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once. Inspects every candidate; prefer the {@code forFirst*} variant when any match will do.
     */
    public static <T extends WorldObject> void forNearestVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getNearestVisibleObjectInRange(object, clazz, range, predicate);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Uniform over all matches via a full scan; prefer the {@code getFirst*} variant when order is irrelevant.
     * {@code null} when none.
     */
    public static <T extends WorldObject> T getRandomVisibleObject(WorldObject object, Class<T> clazz) {
        WorldPartition.checkOwner();
        final Scan scan = new Scan().start(object, UNBOUNDED);
        walkVisible(object, clazz, false, UNBOUNDED, scan, Scan::offerRandom);
        return scan.finish(clazz);
    }

    /**
     * Uniform over all matches via a full scan; prefer the {@code getFirst*} variant when order is irrelevant.
     * {@code null} when none.
     */
    public static <T extends WorldObject> T getRandomVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range) {
        WorldPartition.checkOwner();
        final Scan scan = new Scan().start(object, range);
        walkVisible(object, clazz, false, range, scan, Scan::offerRandom);
        return scan.finish(clazz);
    }

    /**
     * Uniform over all matches via a full scan; prefer the {@code getFirst*} variant when order is irrelevant.
     * {@code null} when none.
     */
    public static <T extends WorldObject> T getRandomVisibleObject(
            WorldObject object, Class<T> clazz, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        return randomMatching(object, clazz, UNBOUNDED, predicate);
    }

    /**
     * Uniform over all matches via a full scan; prefer the {@code getFirst*} variant when order is irrelevant.
     * {@code null} when none.
     */
    public static <T extends WorldObject> T getRandomVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate) {
        WorldPartition.checkOwner();
        return randomMatching(object, clazz, range, predicate);
    }

    /**
     * Acts at most once. Uniform over all matches via a full scan; prefer the {@code forFirst*} variant when order
     * is irrelevant.
     */
    public static <T extends WorldObject> void forRandomVisibleObject(
            WorldObject object, Class<T> clazz, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getRandomVisibleObject(object, clazz);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once. Uniform over all matches via a full scan; prefer the {@code forFirst*} variant when order
     * is irrelevant.
     */
    public static <T extends WorldObject> void forRandomVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getRandomVisibleObjectInRange(object, clazz, range);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once. Uniform over all matches via a full scan; prefer the {@code forFirst*} variant when order
     * is irrelevant.
     */
    public static <T extends WorldObject> void forRandomVisibleObject(
            WorldObject object, Class<T> clazz, Predicate<T> predicate, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getRandomVisibleObject(object, clazz, predicate);
        if (match != null) {
            action.accept(match);
        }
    }

    /**
     * Acts at most once. Uniform over all matches via a full scan; prefer the {@code forFirst*} variant when order
     * is irrelevant.
     */
    public static <T extends WorldObject> void forRandomVisibleObjectInRange(
            WorldObject object, Class<T> clazz, int range, Predicate<T> predicate, Consumer<T> action) {
        WorldPartition.checkOwner();
        final T match = getRandomVisibleObjectInRange(object, clazz, range, predicate);
        if (match != null) {
            action.accept(match);
        }
    }

    public static void broadcastToVisiblePlayers(WorldObject object, ServerPacket packet) {
        WorldPartition.checkOwner();
        sendToVisiblePlayers(object, false, UNBOUNDED, packet);
    }

    public static void broadcastToVisiblePlayersInRange(WorldObject object, ServerPacket packet, int range) {
        WorldPartition.checkOwner();
        sendToVisiblePlayers(object, false, range, packet);
    }

    public static void broadcastToSelfAndVisiblePlayers(WorldObject object, ServerPacket packet) {
        WorldPartition.checkOwner();
        sendToVisiblePlayers(object, true, UNBOUNDED, packet);
    }

    public static void broadcastToSelfAndVisiblePlayersInRange(WorldObject object, ServerPacket packet, int range) {
        WorldPartition.checkOwner();
        sendToVisiblePlayers(object, true, range, packet);
    }

    // A send can close the session and take the player out of its region, so the recipients are collected first.
    private static void sendToVisiblePlayers(WorldObject object, boolean includeSelf, int range, ServerPacket packet) {
        final ObjectArrayList<Player> players = ScratchBuffers.acquire();
        try {
            walkVisible(object, Player.class, includeSelf, range, players, World::addMatch);
            if (players.isEmpty()) {
                return;
            }

            packet.sendInBroadcast();
            try {
                for (int i = 0; i < players.size(); i++) {
                    players.get(i).sendPacket(packet);
                }
            } finally {
                packet.releaseBroadcast();
            }
        } finally {
            ScratchBuffers.release(players);
        }
    }

    public static void broadcastToAllOnlinePlayers(ServerPacket packet) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Player> players = ScratchBuffers.acquire();
        try {
            // A failed send can close the session, which takes the player out of the world.
            ScratchBuffers.copy(allPlayers.values(), players);
            packet.sendInBroadcast();
            try {
                for (int i = 0; i < players.size(); i++) {
                    final Player player = players.get(i);
                    if (player.isOnline()) {
                        player.sendPacket(packet);
                    }
                }
            } finally {
                packet.releaseBroadcast();
            }
        } finally {
            ScratchBuffers.release(players);
        }
    }

    public static void broadcastToAllOnlinePlayers(String text) {
        WorldPartition.checkOwner();
        broadcastToAllOnlinePlayers(text, false);
    }

    public static void broadcastToAllOnlinePlayers(String text, boolean isCritical) {
        WorldPartition.checkOwner();
        broadcastToAllOnlinePlayers(
                new CreatureSay(null, isCritical ? ChatType.CRITICAL_ANNOUNCE : ChatType.ANNOUNCEMENT, "", text));
    }

    public static void broadcastToAllOnlinePlayersOnScreen(String text) {
        WorldPartition.checkOwner();
        broadcastToAllOnlinePlayers(new ExShowScreenMessage(text, 10000));
    }

    public static WorldRegion getRegion(WorldObject object) {
        WorldPartition.checkOwner();
        try {
            final int z = object.getZ();
            final int regionZ = calculateRegionZ(z);
            return worldRegions[(object.getX() >> SHIFT_BY) + OFFSET_X][(object.getY() >> SHIFT_BY) + OFFSET_Y][
                    regionZ];
        } catch (ArrayIndexOutOfBoundsException e) {
            disposeOutOfBoundsObject(object);
            return null;
        }
    }

    public static WorldRegion getRegion(int x, int y, int z) {
        WorldPartition.checkOwner();
        try {
            final int regionZ = calculateRegionZ(z);
            return worldRegions[(x >> SHIFT_BY) + OFFSET_X][(y >> SHIFT_BY) + OFFSET_Y][regionZ];
        } catch (ArrayIndexOutOfBoundsException e) {
            log.warn(
                    "Incorrect world region X: {} Y: {} Z: {}",
                    ((x >> SHIFT_BY) + OFFSET_X),
                    ((y >> SHIFT_BY) + OFFSET_Y),
                    calculateRegionZ(z));
            return null;
        }
    }

    private static int calculateRegionZ(int z) {
        if (z < WORLD_Z_MIN) {
            return 0;
        }
        if (z > WORLD_Z_MAX) {
            return REGIONS_Z - 1;
        }

        return (z - WORLD_Z_MIN) / Z_REGION_SIZE;
    }

    public static void disposeOutOfBoundsObject(WorldObject object) {
        WorldPartition.checkOwner();
        if (object.isPlayer()) {
            object.asCreature().stopMove(object.asPlayer().getLastServerPosition());
        } else if (object.isSummon()) {
            final Summon summon = object.asSummon();
            summon.unSummon(summon.getOwner());
        } else if (allObjects.remove(object.getObjectId()) != null) {
            if (object.isNpc()) {
                final Npc npc = object.asNpc();
                log.warn(
                        "Deleting npc {} NPCID[{}] from invalid location X:{} Y:{} Z:{}",
                        object.getName(),
                        npc.getId(),
                        object.getX(),
                        object.getY(),
                        object.getZ());
                npc.deleteMe();

                final Spawn spawn = npc.getSpawn();
                if (spawn != null) {
                    log.warn(
                            "Spawn location X:{} Y:{} Z:{} Heading:{}",
                            spawn.getX(),
                            spawn.getY(),
                            spawn.getZ(),
                            spawn.getHeading());
                }
            } else if (object.isCreature()) {
                log.warn(
                        "Deleting object {} OID[{}] from invalid location X:{} Y:{} Z:{}",
                        object.getName(),
                        object.getObjectId(),
                        object.getX(),
                        object.getY(),
                        object.getZ());
                object.asCreature().deleteMe();
            }

            if (object.getWorldRegion() != null) {
                object.getWorldRegion().removeVisibleObject(object);
            }
        }
    }

    /**
     * Returns true to stop the walk at that match. A {@link #walkVisible} visitor runs during the live walk and only
     * updates state; a {@link #visitCandidates} visitor runs over collected candidates and may call out.
     */
    @FunctionalInterface
    private interface MatchVisitor<T, S> {
        boolean visit(S state, T match);
    }

    private static final class Scan {
        private WorldObject reference;
        private int range;
        private WorldObject best;
        private double bestDistance;
        private int count;

        private Scan start(WorldObject reference, int range) {
            this.reference = reference;
            this.range = range;
            best = null;
            bestDistance = Double.MAX_VALUE;
            count = 0;
            return this;
        }

        private boolean offerNearest(WorldObject candidate) {
            final double distance = candidate.calculateDistance3D(reference);
            if ((distance <= range) && (distance < bestDistance)) {
                bestDistance = distance;
                best = candidate;
            }

            return false;
        }

        // Reservoir sampling (Algorithm R, k = 1): uniform over every candidate offered, without collecting them.
        private boolean offerRandom(WorldObject candidate) {
            count++;
            if (ClockBridge.random().nextInt(count) == 0) {
                best = candidate;
            }

            return false;
        }

        private <T> T finish(Class<T> clazz) {
            return clazz.cast(best);
        }
    }
}
