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
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.SkillOperateType;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.FuncAdd;
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

/** Lives in {@code partition} to bind the world loop through the package-private {@code WorldPartition.swap}. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class CreatureBenchmark {
    private static final int NPC_ID = 20_545;
    private static final int SKILL_COUNT = 20;
    private static final int SKILL_ID_BASE = 1_000;

    private Loop previous;
    private FixedClockBinding clockBinding;
    private Attackable creature;
    private Skill[] skills;
    private Skill reuseSkill;
    private FuncAdd func;
    private Object funcOwner;
    private int skillCursor;

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

        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        creature = new Attackable(template);

        skills = new Skill[SKILL_COUNT];
        for (int i = 0; i < SKILL_COUNT; i++) {
            skills[i] = skill(SKILL_ID_BASE + i, 1);
            creature.addSkill(skills[i]);
        }
        reuseSkill = skills[0];

        funcOwner = new Object();
        func = new FuncAdd(Stat.POWER_ATTACK, 0x40, funcOwner, 10, null);
    }

    @TearDown
    public void tearDown() {
        clockBinding.close();
        WorldPartition.swap(previous);
    }

    @Benchmark
    public boolean zoneEnterLeave() {
        creature.setInsideZone(ZoneId.PEACE, true);
        final boolean inside = creature.isInsideZone(ZoneId.PEACE);
        creature.setInsideZone(ZoneId.PEACE, false);
        return inside;
    }

    @Benchmark
    public void statFuncAddRemove() {
        creature.addStatFunc(func);
        creature.removeStatsOwner(funcOwner);
    }

    @Benchmark
    public double statCalc() {
        return creature.getStat().getPAtk(null);
    }

    @Benchmark
    public int skillLookup() {
        final int id = SKILL_ID_BASE + (skillCursor++ % SKILL_COUNT);
        return creature.getKnownSkill(id).getLevel() + creature.getSkillLevel(id);
    }

    @Benchmark
    public boolean skillReuse() {
        creature.addTimeStamp(reuseSkill, 10_000);
        final boolean disabled = creature.isSkillDisabled(reuseSkill);
        creature.removeTimeStamp(reuseSkill);
        return disabled;
    }

    @Benchmark
    public Object aiAccess() {
        return creature.getAI();
    }

    @Benchmark
    public int allSkills() {
        int levels = 0;
        for (Skill skill : creature.getAllSkills()) {
            levels += skill.getLevel();
        }

        return levels;
    }

    // A real skill: its constructor reads the skill trees, which load from the datapack in the working directory.
    private static Skill skill(int id, int level) {
        return new Skill(new StatSet(Map.of("skill_id", id, "level", level, "operateType", SkillOperateType.P)));
    }
}
