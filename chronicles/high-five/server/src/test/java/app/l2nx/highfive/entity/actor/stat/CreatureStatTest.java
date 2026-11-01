/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.stat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.TraitType;
import app.l2nx.highfive.mechanics.stats.functions.FuncAdd;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
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
class CreatureStatTest {
    private static final int NPC_ID = 20_545;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<CreatureStat> call) {
            final CreatureStat stat =
                    WorldLoopExtension.callInPass(() -> creature().getStat());

            assertThatThrownBy(() -> call.accept(stat))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<CreatureStat>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getPAtk", stat -> stat.getPAtk(null)),
                    Named.of("getMaxHp", CreatureStat::getMaxHp),
                    Named.of("getMoveSpeed", CreatureStat::getMoveSpeed),
                    Named.of("getActiveChar (accessor)", CreatureStat::getActiveChar),
                    Named.of("getExp (accessor)", CreatureStat::getExp),
                    Named.of("setExp (accessor)", stat -> stat.setExp(1)),
                    Named.of("setMaxBuffCount (accessor)", stat -> stat.setMaxBuffCount(1)),
                    Named.of("addMaxBuffCount", stat -> stat.addMaxBuffCount(1)),
                    Named.of("getLevel (NpcStat override)", CreatureStat::getLevel),
                    Named.of("addAttackTrait", stat -> stat.addAttackTrait(TraitType.SWORD, 2)),
                    Named.of("isTraitInvul", stat -> stat.isTraitInvul(TraitType.SWORD)));
        }

        @Test
        void calcStat_shouldApplyTheStatFunctions_whenCalledOnTheLoop() {
            final double actual = WorldLoopExtension.callInPass(() -> {
                final Creature creature = creature();
                creature.addStatFunc(new FuncAdd(Stat.BONUS_EXP, 0x40, new Object(), 10, null));
                return creature.calcStat(Stat.BONUS_EXP, 5);
            });

            assertThat(actual).isEqualTo(15);
        }
    }

    @Nested
    class AttackTraits {
        @Test
        void addAttackTrait_shouldMultiplyTheTraitAndCountIt() {
            final float[] actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.addAttackTrait(TraitType.SWORD, 1.5f);
                stat.addAttackTrait(TraitType.SWORD, 2f);
                return new float[] {stat.getAttackTrait(TraitType.SWORD), stat.hasAttackTrait(TraitType.SWORD) ? 1 : 0};
            });

            assertThat(actual).containsExactly(3f, 1f);
        }

        @Test
        void removeAttackTrait_shouldRestoreTheTrait_whenEveryAddIsRemoved() {
            final float[] actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.addAttackTrait(TraitType.SWORD, 2f);
                stat.removeAttackTrait(TraitType.SWORD, 2f);
                return new float[] {stat.getAttackTrait(TraitType.SWORD), stat.hasAttackTrait(TraitType.SWORD) ? 1 : 0};
            });

            assertThat(actual).containsExactly(1f, 0f);
        }

        @Test
        void removeAttackTrait_shouldKeepTheTrait_whenItWasNeverAdded() {
            final float actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.removeAttackTrait(TraitType.SWORD, 2f);
                return stat.getAttackTrait(TraitType.SWORD);
            });

            assertThat(actual).isEqualTo(1f);
        }
    }

    @Nested
    class DefenceTraits {
        @Test
        void removeDefenceTrait_shouldRestoreTheTrait_whenEveryAddIsRemoved() {
            final float[] actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.addDefenceTrait(TraitType.BLUNT, 1.25f);
                final float added = stat.getDefenceTrait(TraitType.BLUNT);
                stat.removeDefenceTrait(TraitType.BLUNT, 1.25f);
                stat.removeDefenceTrait(TraitType.BLUNT, 1.25f);
                return new float[] {added, stat.getDefenceTrait(TraitType.BLUNT)};
            });

            assertThat(actual).containsExactly(1.25f, 1f);
        }

        @Test
        void removeTraitInvul_shouldNotGoBelowZero_whenRemovedMoreThanAdded() {
            final boolean[] actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.removeTraitInvul(TraitType.BLUNT);
                stat.addTraitInvul(TraitType.BLUNT);
                final boolean invul = stat.isTraitInvul(TraitType.BLUNT);
                stat.removeTraitInvul(TraitType.BLUNT);
                return new boolean[] {invul, stat.isTraitInvul(TraitType.BLUNT)};
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class AddMaxBuffCount {
        @Test
        void addMaxBuffCount_shouldRestoreTheBaseCount_whenAddedAndRemoved() {
            final int[] actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.setMaxBuffCount(20);
                stat.addMaxBuffCount(4);
                final int added = stat.getMaxBuffCount();
                stat.addMaxBuffCount(-4);
                return new int[] {added, stat.getMaxBuffCount()};
            });

            assertThat(actual).containsExactly(24, 20);
        }

        @Test
        void addMaxBuffCount_shouldNotGoBelowZero_whenRemovedMoreThanTheBase() {
            final int actual = WorldLoopExtension.callInPass(() -> {
                final CreatureStat stat = creature().getStat();
                stat.setMaxBuffCount(2);
                stat.addMaxBuffCount(-4);
                return stat.getMaxBuffCount();
            });

            assertThat(actual).isZero();
        }
    }

    private static Creature creature() {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        return new Attackable(template);
    }
}
