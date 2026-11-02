/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopBinding;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.clock.FixedClockBinding;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.ZoneRegion;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.entity.zone.form.ZoneCuboid;
import app.l2nx.highfive.entity.zone.type.ArenaZone;
import app.l2nx.highfive.entity.zone.type.DerbyTrackZone;
import app.l2nx.highfive.entity.zone.type.FortZone;
import app.l2nx.highfive.entity.zone.type.HqZone;
import app.l2nx.highfive.entity.zone.type.LandingZone;
import app.l2nx.highfive.entity.zone.type.NoStoreZone;
import app.l2nx.highfive.entity.zone.type.NoSummonFriendZone;
import app.l2nx.highfive.entity.zone.type.PeaceZone;
import app.l2nx.highfive.entity.zone.type.ScriptZone;
import app.l2nx.highfive.entity.zone.type.TownZone;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.util.StatSet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Zone revalidation of one region holding ten zones, four of them around the point the creature walks into. Lives in
 * {@code partition} to bind the world loop through the package-private {@code WorldPartition.swap}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ZoneBenchmark {
    private static final int NPC_ID = 20_545;
    private static final int INSIDE_X = 1_000;
    private static final int OUTSIDE_X = 20_000;

    private Loop previous;
    private FixedClockBinding clockBinding;
    private ZoneRegion region;
    private WalkingMonster creature;

    // JMH runs a thread-scoped setup on the benchmark thread, which so owns the world loop.
    @Setup
    public void setUp() {
        final ManualGameClock clock = new ManualGameClock(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
        final Loop loop =
                new Loop(PartitionRuntime.WORLD, clock, 1L, EngineProperties.defaults(), new SimpleMeterRegistry());
        LoopBinding.bindToCurrentThread(loop);
        previous = WorldPartition.swap(loop);
        final SplittableRandom random = new SplittableRandom(42L);
        clockBinding = FixedClockBinding.bind(clock, () -> random);

        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
        new InstanceManager(null);

        region = new ZoneRegion(0, 0);
        final ZoneCuboid around = new ZoneCuboid(0, 2_000, 0, 2_000, -1_000, 1_000);
        final ZoneCuboid elsewhere = new ZoneCuboid(5_000, 8_000, 5_000, 8_000, -1_000, 1_000);
        addZone(region, new PeaceZone(80_001), around);
        addZone(region, new NoSummonFriendZone(80_002), elsewhere);
        addZone(region, new TownZone(80_003), around);
        addZone(region, new LandingZone(80_004), elsewhere);
        addZone(region, new NoStoreZone(80_005), elsewhere);
        addZone(region, new ScriptZone(80_006), around);
        addZone(region, new FortZone(80_007), elsewhere);
        addZone(region, new DerbyTrackZone(80_008), elsewhere);
        addZone(region, new ArenaZone(80_009), around);
        addZone(region, new HqZone(80_010), elsewhere);

        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        creature = new WalkingMonster(template);
        creature.walkTo(OUTSIDE_X);
    }

    @TearDown
    public void tearDown() {
        clockBinding.close();
        WorldPartition.swap(previous);
    }

    /** Walks in, entering four zones, and out again, leaving them: two revalidations. */
    @Benchmark
    public void revalidateEnterLeave() {
        creature.walkTo(INSIDE_X);
        region.revalidateZones(creature);
        creature.walkTo(OUTSIDE_X);
        region.revalidateZones(creature);
    }

    /** A move that stays inside the same four zones, the common case: one revalidation. */
    @Benchmark
    public void revalidateStay() {
        creature.walkTo(INSIDE_X);
        region.revalidateZones(creature);
    }

    private static void addZone(ZoneRegion region, ZoneType zone, ZoneCuboid shape) {
        zone.setZone(shape);
        region.addZone(zone);
    }

    // Creature.setXYZ asks the zone manager for the region, which loads every datapack zone; the benchmark moves the
    // monster by its coordinates instead and revalidates its one region itself.
    private static final class WalkingMonster extends Attackable {
        private int x;

        private WalkingMonster(NpcTemplate template) {
            super(template);
        }

        private void walkTo(int newX) {
            x = newX;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return 1_000;
        }

        @Override
        public int getZ() {
            return 0;
        }
    }
}
