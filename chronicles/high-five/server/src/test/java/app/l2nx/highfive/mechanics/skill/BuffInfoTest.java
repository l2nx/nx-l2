/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("PlayerConfig")
class BuffInfoTest {
    private static final long TICK_RATIO = 1_000;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<BuffInfo> call) {
            final BuffInfo info = WorldLoopExtension.callInPass(BuffInfoTest::buffInfo);

            assertThatThrownBy(() -> call.accept(info))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<BuffInfo>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getSkill (accessor)", BuffInfo::getSkill),
                    Named.of("isInUse (accessor)", BuffInfo::isInUse),
                    Named.of("setInUse (accessor)", info -> info.setInUse(false)),
                    Named.of("getEffects", BuffInfo::getEffects),
                    Named.of("hasEffectType", info -> info.hasEffectType(EffectType.BUFF)),
                    Named.of("getTime", BuffInfo::getTime),
                    Named.of("isFinished", BuffInfo::isFinished),
                    Named.of("finishEffects", info -> info.finishEffects(false)));
        }

        @Test
        void toString_shouldAnswer_whenCalledOffTheLoop() {
            final BuffInfo info = WorldLoopExtension.callInPass(BuffInfoTest::buffInfo);

            assertThat(info.toString()).contains("isInUse=true");
        }
    }

    @Nested
    class Effects {
        @Test
        void getEffects_shouldBeReadOnly() {
            final List<AbstractEffect> actual = WorldLoopExtension.callInPass(() -> {
                final BuffInfo info = buffInfo();
                info.addEffect(new TypedEffect());
                return info.getEffects();
            });

            assertThat(actual).hasSize(1);
            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void hasEffectType_shouldFindTheTypeOfAnyEffect() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final BuffInfo info = buffInfo();
                info.addEffect(new TypedEffect());
                return List.of(info.hasEffectType(EffectType.BUFF), info.hasEffectType(EffectType.DEBUFF));
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class Ticks {
        private long tickRatio;

        @BeforeEach
        void tickRatio() {
            tickRatio = PlayerConfig.EFFECT_TICK_RATIO;
            PlayerConfig.EFFECT_TICK_RATIO = TICK_RATIO;
        }

        @AfterEach
        void restoreConfig() {
            PlayerConfig.EFFECT_TICK_RATIO = tickRatio;
        }

        @Test
        void stopTicks_shouldCancelTheScheduledTicksAndClearTheTasks() {
            final TickEffect effect = new TickEffect();
            final BuffInfo info = WorldLoopExtension.callInPass(() -> {
                final BuffInfo created = buffInfo();
                created.addEffect(effect);
                created.initializeEffects();
                return created;
            });
            WorldLoopExtension.passAfter(Duration.ofMillis(TICK_RATIO));
            final int ticksBeforeStop = effect.ticks;

            WorldLoopExtension.runInPass(info::stopTicks);
            WorldLoopExtension.passAfter(Duration.ofMillis(3 * TICK_RATIO));

            assertThat(ticksBeforeStop).as("ticks before the stop").isEqualTo(1);
            assertThat(effect.ticks).as("ticks after the stop").isEqualTo(1);
            assertThat(info.toString()).contains("tasks={}");
        }

        @Test
        void initializeEffects_shouldScheduleNoTick_whenAnEarlierStartFinishedTheBuff() {
            final TickEffect later = new TickEffect();
            WorldLoopExtension.runInPass(() -> {
                final BuffInfo info = buffInfo();
                final Skill skill = info.getSkill();
                when(skill.isDebuff()).thenReturn(true);
                info.addEffect(new StartEffect(
                        () -> info.getEffected().getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill)));
                info.addEffect(later);
                info.getEffected().getEffectList().add(info);
            });

            WorldLoopExtension.passAfter(Duration.ofMillis(3 * TICK_RATIO));

            assertThat(later.ticks).isZero();
        }
    }

    private static BuffInfo buffInfo() {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", 20_545, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        final Creature creature = new Attackable(template);
        final Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(1);
        when(skill.getAbnormalType()).thenReturn(AbnormalType.NONE);
        return new BuffInfo(creature, creature, skill);
    }

    private static final class TypedEffect extends AbstractEffect {
        TypedEffect() {
            super(null, null, new StatSet(Map.of("name", "typed")), null);
        }

        @Override
        public EffectType getEffectType() {
            return EffectType.BUFF;
        }
    }

    private static final class TickEffect extends AbstractEffect {
        private int ticks;

        TickEffect() {
            super(null, null, new StatSet(Map.of("name", "tick", "ticks", 1)), null);
        }

        @Override
        public boolean onActionTime(Creature effector, Creature effected, Skill skill) {
            ticks++;
            return true;
        }
    }

    private static final class StartEffect extends AbstractEffect {
        private final Runnable onStart;

        StartEffect(Runnable onStart) {
            super(null, null, new StatSet(Map.of("name", "start")), null);
            this.onStart = onStart;
        }

        @Override
        public void onStart(Creature effector, Creature effected, Skill skill) {
            onStart.run();
        }
    }
}
