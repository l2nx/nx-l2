/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopBinding;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Lives in {@code partition} to bind the world loop through the package-private {@code WorldPartition.swap}. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class WorldBenchmark {
    private static final int PLAYERS = 1000;
    private static final int LOOKUP_IDS = 1024;
    private static final int OBJECT_ID_BASE = 0x1000_0000;
    private static final int PLAYER_ID_BASE = 0x2000_0000;
    private static final int REGION_SIZE_XY = 1 << World.SHIFT_BY;
    private static final int REGION_SPREAD_XY = REGION_SIZE_XY / 2 - 100;
    private static final int REGION_SPREAD_Z = World.Z_REGION_SIZE / 2 - 100;

    private static final int CENTRE_X = 40 * REGION_SIZE_XY + REGION_SIZE_XY / 2;
    private static final int CENTRE_Y = 72 * REGION_SIZE_XY + REGION_SIZE_XY / 2;
    private static final int CENTRE_Z = World.WORLD_Z_MIN + 6 * World.Z_REGION_SIZE + World.Z_REGION_SIZE / 2;

    @Param({"50", "500"})
    private int objectsPerRegion;

    private final List<WorldObject> objects = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final int[] lookupIds = new int[LOOKUP_IDS];
    private int nextObjectId = OBJECT_ID_BASE;
    private int lookupCursor;
    private Loop previous;
    private WorldObject centre;
    private WorldObject mover;

    // JMH runs a thread-scoped setup on the benchmark thread, which so owns the world loop.
    @Setup
    public void setUp() {
        final Loop loop = new Loop(
                PartitionRuntime.WORLD,
                new ManualGameClock(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC),
                1L,
                EngineProperties.defaults(),
                new SimpleMeterRegistry());
        LoopBinding.bindToCurrentThread(loop);
        previous = WorldPartition.swap(loop);

        World.init();

        final SplittableRandom random = new SplittableRandom(42L);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int i = 0; i < objectsPerRegion; i++) {
                        spawn(
                                CENTRE_X + dx * REGION_SIZE_XY + random.nextInt(-REGION_SPREAD_XY, REGION_SPREAD_XY),
                                CENTRE_Y + dy * REGION_SIZE_XY + random.nextInt(-REGION_SPREAD_XY, REGION_SPREAD_XY),
                                CENTRE_Z
                                        + dz * World.Z_REGION_SIZE
                                        + random.nextInt(-REGION_SPREAD_Z, REGION_SPREAD_Z));
                    }
                }
            }
        }
        centre = spawn(CENTRE_X, CENTRE_Y, CENTRE_Z);
        mover = spawn(CENTRE_X, CENTRE_Y, CENTRE_Z);

        for (int i = 0; i < PLAYERS; i++) {
            final Player player = player(PLAYER_ID_BASE + i);
            World.addObject(player);
            players.add(player);
        }

        for (int i = 0; i < LOOKUP_IDS; i++) {
            lookupIds[i] = random.nextBoolean()
                    ? objects.get(random.nextInt(objects.size())).getObjectId()
                    : players.get(random.nextInt(PLAYERS)).getObjectId();
        }
    }

    @TearDown
    public void tearDown() {
        // The next World.init replaces the regions; only the global maps outlive a trial.
        objects.forEach(World::removeObject);
        players.forEach(World::removeObject);
        WorldPartition.swap(previous);
    }

    @Benchmark
    public List<WorldObject> getVisibleObjects() {
        return World.getVisibleObjects(centre, WorldObject.class);
    }

    @Benchmark
    public void forEachVisibleObject(Blackhole bh) {
        World.forEachVisibleObject(centre, WorldObject.class, bh::consume);
    }

    @Benchmark
    public void getPlayers(Blackhole bh) {
        for (Player player : World.getPlayers()) {
            bh.consume(player);
        }
    }

    @Benchmark
    public WorldObject findObject() {
        return World.findObject(lookupIds[lookupCursor++ & (LOOKUP_IDS - 1)]);
    }

    @Benchmark
    public void switchRegion() {
        mover.setXYZ(CENTRE_X + REGION_SIZE_XY, CENTRE_Y, CENTRE_Z);
        mover.setXYZ(CENTRE_X, CENTRE_Y, CENTRE_Z);
    }

    private WorldObject spawn(int x, int y, int z) {
        final WorldObject object = new GridObject(nextObjectId++);
        object.spawnMe(x, y, z);
        objects.add(object);
        return object;
    }

    // The jmh resources select the subclass mock maker: it leaves WorldObject bytecode alone, where the inline one
    // would instrument the very methods the grid scans call on every object.
    private static Player player(int objectId) {
        final Player player = mock(Player.class, withSettings().stubOnly());
        when(player.getObjectId()).thenReturn(objectId);
        when(player.isPlayer()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
        return player;
    }

    private static final class GridObject extends WorldObject {
        GridObject(int objectId) {
            super(objectId);
        }

        @Override
        public boolean isAutoAttackable(Creature attacker) {
            return false;
        }

        @Override
        public void sendInfo(Player player) {}
    }
}
