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
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.util.StatSet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

/** Lives in {@code partition} to bind the world loop through the package-private {@code WorldPartition.swap}. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class AttackableBenchmark {
    private static final int NPC_ID = 20_545;
    private static final int ATTACKER_COUNT = 20;

    private Loop previous;
    private FixedClockBinding clockBinding;
    private NpcTemplate template;
    private Attackable mob;
    private Attackable[] attackers;
    private int scriptCursor;

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

        template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        mob = newMob();

        attackers = new Attackable[ATTACKER_COUNT];
        for (int i = 0; i < ATTACKER_COUNT; i++) {
            attackers[i] = newMob();
        }

        fill();
    }

    @TearDown
    public void tearDown() {
        clockBinding.close();
        WorldPartition.swap(previous);
    }

    @Benchmark
    public Object aggroAddAndRead() {
        fill();
        return mob.getMostHated();
    }

    @Benchmark
    public void aggroClear() {
        fill();
        mob.clearAggroList();
    }

    @Benchmark
    public List<Creature> hateList() {
        return mob.getHateList();
    }

    @Benchmark
    public int scriptValue() {
        mob.setScriptValue(scriptCursor++ & 0xFF);
        return mob.getScriptValue();
    }

    private void fill() {
        for (Attackable attacker : attackers) {
            mob.addDamageHate(attacker, 10, 10);
        }
    }

    private Attackable newMob() {
        return new Attackable(template);
    }
}
