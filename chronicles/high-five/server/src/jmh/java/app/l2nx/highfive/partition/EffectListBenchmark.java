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
import app.l2nx.highfive.entity.actor.holders.creature.EffectList;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.SkillOperateType;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class EffectListBenchmark {
    private static final int NPC_ID = 20_545;
    private static final int FIRST_SKILL_ID = 1_000;
    private static final int TURNOVER = 10;
    private static final int RESIDENT = 20;
    private static final int MAX_BUFFS = 40;

    private Loop previous;
    private FixedClockBinding clockBinding;
    private Attackable mob;
    private EffectList effects;
    private Skill[] turnover;
    private Skill stackLow;
    private Skill stackHigh;

    // @Setup runs on the benchmark thread, which therefore owns the world loop.
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
        mob = new Attackable(template);
        // The player config is not loaded here; without a limit every add would evict the previous buff.
        mob.getStat().setMaxBuffCount(MAX_BUFFS);
        effects = mob.getEffectList();

        final AbnormalType[] types = AbnormalType.values();
        int next = 1;
        turnover = new Skill[TURNOVER];
        for (int i = 0; i < TURNOVER; i++) {
            turnover[i] = buff(FIRST_SKILL_ID + i, 1, types[next++]);
        }

        for (int i = 0; i < RESIDENT; i++) {
            effects.add(new BuffInfo(mob, mob, buff(FIRST_SKILL_ID + 100 + i, 1, types[next++])));
        }

        stackLow = buff(FIRST_SKILL_ID + 200, 1, types[next]);
        stackHigh = buff(FIRST_SKILL_ID + 200, 2, types[next]);
    }

    @TearDown
    public void tearDown() {
        clockBinding.close();
        WorldPartition.swap(previous);
    }

    @Benchmark
    public void addRemove() {
        for (Skill skill : turnover) {
            effects.add(new BuffInfo(mob, mob, skill));
        }

        for (Skill skill : turnover) {
            effects.stopSkillEffects(SkillFinishType.NORMAL, skill);
        }
    }

    @Benchmark
    public void stackReplace() {
        effects.add(new BuffInfo(mob, mob, stackLow));
        effects.add(new BuffInfo(mob, mob, stackHigh));
        effects.stopSkillEffects(SkillFinishType.NORMAL, stackHigh);
    }

    @Benchmark
    public List<BuffInfo> effects() {
        return effects.getEffects();
    }

    @Benchmark
    public boolean isAffected() {
        return effects.isAffectedBySkill(FIRST_SKILL_ID + 119);
    }

    // A real skill: its constructor reads the skill trees, which load from the datapack in the working directory.
    private static Skill buff(int id, int level, AbnormalType type) {
        return new Skill(new StatSet(Map.of(
                "skill_id",
                id,
                "level",
                level,
                "operateType",
                SkillOperateType.A2,
                "abnormalType",
                type,
                "abnormalLevel",
                level,
                "abnormalTime",
                600)));
    }
}
