/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
class EffectListTest {
    private static final int NPC_ID = 20_545;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<EffectList> call) {
            final EffectList effects =
                    WorldLoopExtension.callInPass(() -> creature().getEffectList());

            assertThatThrownBy(() -> call.accept(effects))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<EffectList>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("add", effects -> effects.add(null)),
                    Named.of("remove", effects -> effects.remove(SkillFinishType.NORMAL, null)),
                    Named.of("getBuffs", EffectList::getBuffs),
                    Named.of("getDebuffs", EffectList::getDebuffs),
                    Named.of("collectDebuffs", effects -> effects.collectDebuffs(new ArrayList<>())),
                    Named.of("getPassives", EffectList::getPassives),
                    Named.of("getEffects", EffectList::getEffects),
                    Named.of("getBlockedAbnormalTypes", EffectList::getBlockedAbnormalTypes),
                    Named.of("isBlockedAbnormalType", effects -> effects.isBlockedAbnormalType(AbnormalType.NONE)),
                    Named.of("isAffectedBySkill", effects -> effects.isAffectedBySkill(1)),
                    Named.of(
                            "getBuffInfoByAbnormalType",
                            effects -> effects.getBuffInfoByAbnormalType(AbnormalType.NONE)),
                    Named.of("getFirstEffect", effects -> effects.getFirstEffect(EffectType.BUFF)),
                    Named.of("hasDispellableDebuff", EffectList::hasDispellableDebuff),
                    Named.of("getBuffCount", EffectList::getBuffCount),
                    Named.of("getShortBuff (accessor)", EffectList::getShortBuff),
                    Named.of("isEmpty", EffectList::isEmpty),
                    Named.of("isAffected", effects -> effects.isAffected(EffectFlag.NONE)),
                    Named.of("stopAllEffects", EffectList::stopAllEffects),
                    Named.of("stopSkillEffects", effects -> effects.stopSkillEffects(SkillFinishType.NORMAL, 1)),
                    Named.of("stopEffectsOnDamage", effects -> effects.stopEffectsOnDamage(true)),
                    Named.of("forEach", effects -> effects.forEach(_ -> false, true)));
        }

        @Test
        void isEmpty_shouldAnswer_whenCalledOnTheLoop() {
            final EffectList effects =
                    WorldLoopExtension.callInPass(() -> creature().getEffectList());

            final boolean actual = WorldLoopExtension.callInPass(effects::isEmpty);

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class Snapshots {
        @Test
        void getDebuffs_shouldBeReadOnly() {
            final List<BuffInfo> actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                creature.getEffectList().add(new BuffInfo(creature, creature, debuff(1, AbnormalType.STUN, 1)));
                return creature.getEffectList().getDebuffs();
            });

            assertThatThrownBy(actual::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getEffects_shouldKeepItsEntries_whenTheEffectsStop() {
            final BuffInfo[] expected = new BuffInfo[1];
            final List<BuffInfo> actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                expected[0] = new BuffInfo(creature, creature, debuff(1, AbnormalType.STUN, 1));
                creature.getEffectList().add(expected[0]);
                final List<BuffInfo> snapshot = creature.getEffectList().getEffects();
                creature.getEffectList().stopAllEffects();
                return snapshot;
            });

            assertThat(actual).containsExactly(expected[0]);
        }
    }

    @Nested
    class Stop {
        @Test
        void stopAllEffects_shouldStopEachBuffOnce_whenAnExitStopsAnotherList() {
            final ExitEffect stopper = new ExitEffect();
            final ExitEffect stopped = new ExitEffect();
            final EffectList actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final Skill debuff = debuff(2, AbnormalType.STUN, 1);
                stopper.onExit = () -> creature.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, debuff);
                creature.getEffectList().add(withEffect(creature, toggle(1), stopper));
                creature.getEffectList().add(withEffect(creature, debuff, stopped));
                creature.getEffectList().stopAllEffects();
                return creature.getEffectList();
            });

            assertThat(stopper.exits).isEqualTo(1);
            assertThat(stopped.exits).isEqualTo(1);
            WorldLoopExtension.runInPass(() -> {
                assertThat(actual.getToggles()).isEmpty();
                assertThat(actual.getDebuffs()).isEmpty();
                assertThat(actual.isAffectedByAbnormalType(AbnormalType.STUN)).isFalse();
            });
        }

        @Test
        void stopAllDebuffs_shouldStopEachDebuffOnce_whenAnExitStopsALaterOne() {
            final ExitEffect stopper = new ExitEffect();
            final ExitEffect stopped = new ExitEffect();
            final EffectList actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final Skill later = debuff(2, AbnormalType.ROOT_MAGICALLY, 1);
                stopper.onExit = () -> creature.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, later);
                creature.getEffectList().add(withEffect(creature, debuff(1, AbnormalType.STUN, 1), stopper));
                creature.getEffectList().add(withEffect(creature, later, stopped));
                creature.getEffectList().stopAllDebuffs(true);
                return creature.getEffectList();
            });

            assertThat(stopper.exits).isEqualTo(1);
            assertThat(stopped.exits).isEqualTo(1);
            WorldLoopExtension.runInPass(() -> {
                assertThat(actual.getDebuffs()).isEmpty();
                assertThat(actual.isAffectedByAbnormalType(AbnormalType.STUN)).isFalse();
                assertThat(actual.isAffectedByAbnormalType(AbnormalType.ROOT_MAGICALLY))
                        .isFalse();
            });
        }

        @Test
        void stopAllDebuffs_shouldStopADebuffAnExitAdds() {
            final ExitEffect adder = new ExitEffect();
            final ExitEffect added = new ExitEffect();
            final EffectList actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                adder.onExit = () -> creature.getEffectList()
                        .add(withEffect(creature, debuff(2, AbnormalType.ROOT_MAGICALLY, 1), added));
                creature.getEffectList().add(withEffect(creature, debuff(1, AbnormalType.STUN, 1), adder));
                creature.getEffectList().stopAllDebuffs(true);
                return creature.getEffectList();
            });

            assertThat(adder.exits).isEqualTo(1);
            assertThat(added.exits).isEqualTo(1);
            WorldLoopExtension.runInPass(() -> {
                assertThat(actual.getDebuffs()).isEmpty();
                assertThat(actual.isAffectedByAbnormalType(AbnormalType.ROOT_MAGICALLY))
                        .isFalse();
            });
        }
    }

    @Nested
    class Stacking {
        @Test
        void add_shouldReplaceTheLowerAbnormalLevelAtTheEnd() {
            final ExitEffect replaced = new ExitEffect();
            final BuffInfo[] expected = new BuffInfo[2];
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final EffectList effects = creature.getEffectList();
                effects.add(withEffect(creature, debuff(1, AbnormalType.STUN, 1), replaced));
                expected[0] = new BuffInfo(creature, creature, debuff(2, AbnormalType.ROOT_MAGICALLY, 1));
                effects.add(expected[0]);
                expected[1] = new BuffInfo(creature, creature, debuff(3, AbnormalType.STUN, 2));
                effects.add(expected[1]);
                return List.of(effects.getDebuffs(), effects.getBuffInfoByAbnormalType(AbnormalType.STUN));
            });

            assertThat(actual.get(0)).isEqualTo(List.of(expected[0], expected[1]));
            assertThat(actual.get(1)).isSameAs(expected[1]);
            assertThat(replaced.exits).isEqualTo(1);
        }

        @Test
        void add_shouldIgnoreTheBuff_whenItsAbnormalLevelIsLower() {
            final BuffInfo[] expected = new BuffInfo[1];
            final List<BuffInfo> actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final EffectList effects = creature.getEffectList();
                expected[0] = new BuffInfo(creature, creature, debuff(1, AbnormalType.STUN, 2));
                effects.add(expected[0]);
                effects.add(new BuffInfo(creature, creature, debuff(2, AbnormalType.STUN, 1)));
                return effects.getDebuffs();
            });

            assertThat(actual).containsExactly(expected[0]);
        }
    }

    @Nested
    class Passives {
        @Test
        void add_shouldReplaceThePassiveOfTheSameSkill() {
            final BuffInfo[] expected = new BuffInfo[2];
            final List<BuffInfo> actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                final EffectList effects = creature.getEffectList();
                expected[0] = new BuffInfo(creature, creature, passive(7));
                effects.add(expected[0]);
                expected[1] = new BuffInfo(creature, creature, passive(7));
                effects.add(expected[1]);
                return effects.getPassives();
            });

            assertThat(actual).containsExactly(expected[1]);
            assertThat(WorldLoopExtension.callInPass(expected[0]::isInUse)).isFalse();
        }

        @Test
        void add_shouldNotExitTheReplacedPassive() {
            final ExitEffect replaced = new ExitEffect();
            WorldLoopExtension.runInPass(() -> {
                final Creature creature = creature();
                final EffectList effects = creature.getEffectList();
                effects.add(withEffect(creature, passive(7), replaced));
                effects.add(new BuffInfo(creature, creature, passive(7)));
            });

            assertThat(replaced.exits).isZero();
        }
    }

    @Nested
    class StopOnEvent {
        @ParameterizedTest(name = "{0}")
        @MethodSource("stopsOnEvent")
        void stop_shouldNotWalk_whenTheLastBuffEndingOnTheEventHasEnded(EventStop stop) {
            final Skill remaining = toggle(2);
            WorldLoopExtension.runInPass(() -> {
                final Creature creature = creature();
                final EffectList effects = creature.getEffectList();
                final Skill ending = toggle(1);
                stop.markEnding().accept(ending);
                effects.add(new BuffInfo(creature, creature, ending));
                effects.add(new BuffInfo(creature, creature, remaining));
                effects.stopSkillEffects(SkillFinishType.REMOVED, ending);
                clearInvocations(remaining);
                stop.call().accept(effects);
            });

            stop.verifyNotAsked().accept(verify(remaining, never()));
        }

        static Stream<Named<EventStop>> stopsOnEvent() {
            return Stream.of(
                    Named.of(
                            "stopEffectsOnAction",
                            new EventStop(
                                    skill -> when(skill.isRemovedOnAnyActionExceptMove())
                                            .thenReturn(true),
                                    EffectList::stopEffectsOnAction,
                                    Skill::isRemovedOnAnyActionExceptMove)),
                    Named.of(
                            "stopEffectsOnDamage",
                            new EventStop(
                                    skill -> when(skill.isRemovedOnDamage()).thenReturn(true),
                                    effects -> effects.stopEffectsOnDamage(true),
                                    Skill::isRemovedOnDamage)));
        }

        @Test
        @ResourceLock("PlayerConfig")
        void stopEffectsOnAction_shouldStopADanceEndingOnAction() {
            final byte dancesMax = PlayerConfig.DANCES_MAX_AMOUNT;
            PlayerConfig.DANCES_MAX_AMOUNT = 12;
            try {
                final boolean actual = WorldLoopExtension.callInPass(() -> {
                    final Creature creature = creature();
                    final EffectList effects = creature.getEffectList();
                    final Skill dance = skill(3, AbnormalType.NONE);
                    when(dance.isDance()).thenReturn(true);
                    when(dance.isRemovedOnAnyActionExceptMove()).thenReturn(true);
                    effects.add(new BuffInfo(creature, creature, dance));
                    effects.stopEffectsOnAction();
                    return effects.hasDances();
                });

                assertThat(actual).isFalse();
            } finally {
                PlayerConfig.DANCES_MAX_AMOUNT = dancesMax;
            }
        }
    }

    private record EventStop(Consumer<Skill> markEnding, Consumer<EffectList> call, Consumer<Skill> verifyNotAsked) {}

    private static Creature creature() {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        return new Attackable(template);
    }

    private static Skill debuff(int id, AbnormalType type, int level) {
        final Skill skill = skill(id, type);
        when(skill.isDebuff()).thenReturn(true);
        when(skill.getAbnormalLevel()).thenReturn(level);
        return skill;
    }

    private static Skill toggle(int id) {
        final Skill skill = skill(id, AbnormalType.NONE);
        when(skill.isToggle()).thenReturn(true);
        return skill;
    }

    private static Skill passive(int id) {
        final Skill skill = skill(id, AbnormalType.NONE);
        when(skill.isPassive()).thenReturn(true);
        when(skill.checkCondition(any(), any(), anyBoolean())).thenReturn(true);
        return skill;
    }

    private static Skill skill(int id, AbnormalType type) {
        final Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getAbnormalType()).thenReturn(type);
        return skill;
    }

    private static BuffInfo withEffect(Creature creature, Skill skill, AbstractEffect effect) {
        final BuffInfo info = new BuffInfo(creature, creature, skill);
        info.addEffect(effect);
        return info;
    }

    private static final class ExitEffect extends AbstractEffect {
        private Runnable onExit = () -> {};
        private int exits;

        ExitEffect() {
            super(null, null, new StatSet(Map.of("name", "exit")), null);
        }

        @Override
        public void onExit(Creature effector, Creature effected, Skill skill) {
            exits++;
            onExit.run();
        }
    }
}
