/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.cache.RelationCache;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class CreatureTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Creature> call) {
            final Creature creature = attackableOnTheLoop();

            assertThatThrownBy(() -> call.accept(creature)).isInstanceOf(IllegalStateException.class);
        }

        static Stream<Named<Consumer<Creature>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("isInsideZone", creature -> creature.isInsideZone(ZoneId.PEACE)),
                    Named.of("getTeam", Creature::getTeam),
                    Named.of(
                            "addAttackerToAttackByList",
                            creature -> creature.addAttackerToAttackByList(attackableOnTheLoop())),
                    Named.of("addAttackerToAttackByList(null)", creature -> creature.addAttackerToAttackByList(null)));
        }

        @Test
        void getTeam_shouldAnswer_whenCalledOnTheLoop() {
            final Creature creature = attackableOnTheLoop();
            final AtomicReference<Object> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> actual.set(creature.getTeam()));

            assertThat(actual.get()).isNotNull();
        }
    }

    @Nested
    class AttackByList {
        @Test
        void addAttackerToAttackByList_shouldKeepOneEntry_whenTheAttackerIsAddedTwice() {
            final AtomicReference<Set<Creature>> actual = new AtomicReference<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                expected.set(new TestAttackable());
                attackable.addAttackerToAttackByList(expected.get());
                attackable.addAttackerToAttackByList(expected.get());
                actual.set(Set.copyOf(attackable.getAttackByList()));
            });

            assertThat(actual.get()).containsExactly(expected.get());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ignoredAttackers")
        void addAttackerToAttackByList_shouldIgnoreTheAttacker_whenItIsNoOtherCreature(
                UnaryOperator<Creature> attackerOf) {
            final AtomicBoolean actual = new AtomicBoolean();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addAttackerToAttackByList(attackerOf.apply(attackable));
                actual.set(attackable.isAttackByListEmpty());
            });

            assertThat(actual.get()).isTrue();
        }

        static Stream<Named<UnaryOperator<Creature>>> ignoredAttackers() {
            return Stream.of(Named.of("null", attackable -> null), Named.of("itself", attackable -> attackable));
        }

        @Test
        void clearAttackByList_shouldEmptyTheList() {
            final AtomicReference<Set<Creature>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addAttackerToAttackByList(new TestAttackable());
                attackable.clearAttackByList();
                actual.set(Set.copyOf(attackable.getAttackByList()));
            });

            assertThat(actual.get()).isEmpty();
        }

        @Test
        void isAttackedBy_shouldAnswerTrue_whenTheCreatureIsInTheList() {
            final AtomicBoolean actual = new AtomicBoolean();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                final TestAttackable attacker = new TestAttackable();
                attackable.addAttackerToAttackByList(attacker);
                actual.set(attackable.isAttackedBy(attacker));
            });

            assertThat(actual.get()).isTrue();
        }

        @Test
        void isAttackedBy_shouldAnswerFalse_whenTheCreatureIsNotInTheList() {
            final AtomicBoolean actual = new AtomicBoolean(true);

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addAttackerToAttackByList(new TestAttackable());
                actual.set(attackable.isAttackedBy(new TestAttackable()));
            });

            assertThat(actual.get()).isFalse();
        }

        @Test
        void isAttackByListEmpty_shouldFollowTheList() {
            final List<Boolean> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                actual.add(attackable.isAttackByListEmpty());
                attackable.addAttackerToAttackByList(new TestAttackable());
                actual.add(attackable.isAttackByListEmpty());
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class FakePlayerDrops {
        @Test
        void getLastFakePlayerDrop_shouldAnswerTheLatestDrop() {
            final Item first = mock(Item.class);
            final Item expected = mock(Item.class);
            final AtomicReference<Item> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addFakePlayerDrop(first);
                attackable.addFakePlayerDrop(expected);
                actual.set(attackable.getLastFakePlayerDrop());
            });

            assertThat(actual.get()).isSameAs(expected);
        }

        @Test
        void getLastFakePlayerDrop_shouldAnswerNull_whenThereAreNoDrops() {
            final AtomicReference<Item> actual = new AtomicReference<>(mock(Item.class));

            WorldLoopExtension.runInPass(() -> actual.set(new TestAttackable().getLastFakePlayerDrop()));

            assertThat(actual.get()).isNull();
        }

        @Test
        void removeLastFakePlayerDrop_shouldRemoveTheLatestDrop() {
            final Item expected = mock(Item.class);
            final Item second = mock(Item.class);
            final AtomicReference<Item> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addFakePlayerDrop(expected);
                attackable.addFakePlayerDrop(second);
                attackable.removeLastFakePlayerDrop();
                actual.set(attackable.getLastFakePlayerDrop());
            });

            assertThat(actual.get()).isSameAs(expected);
        }

        @Test
        void clearFakePlayerDrops_shouldEmptyTheDrops() {
            final AtomicReference<Item> actual = new AtomicReference<>(mock(Item.class));

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addFakePlayerDrop(mock(Item.class));
                attackable.clearFakePlayerDrops();
                actual.set(attackable.getLastFakePlayerDrop());
            });

            assertThat(actual.get()).isNull();
        }
    }

    @Nested
    class KnownRelations {
        @Test
        void putKnownRelation_shouldReplaceTheRelationOfTheObject() {
            final RelationCache expected = new RelationCache(2, true);
            final AtomicReference<RelationCache> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.putKnownRelation(7, new RelationCache(1, false));
                attackable.putKnownRelation(7, expected);
                actual.set(attackable.getKnownRelation(7));
            });

            assertThat(actual.get()).isSameAs(expected);
        }

        @Test
        void removeKnownRelation_shouldForgetOnlyThatObject() {
            final RelationCache kept = new RelationCache(1, false);
            final List<RelationCache> expected = Arrays.asList(null, kept);
            final List<RelationCache> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.putKnownRelation(7, new RelationCache(1, false));
                attackable.putKnownRelation(8, kept);
                attackable.removeKnownRelation(7);
                actual.add(attackable.getKnownRelation(7));
                actual.add(attackable.getKnownRelation(8));
            });

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void getKnownRelation_shouldAnswerTheRelationOfTheObject() {
            final RelationCache expected = new RelationCache(2, true);
            final AtomicReference<RelationCache> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.putKnownRelation(7, expected);
                actual.set(attackable.getKnownRelation(7));
            });

            assertThat(actual.get()).isSameAs(expected);
        }

        @Test
        void getKnownRelation_shouldAnswerNull_whenTheObjectIsUnknown() {
            final AtomicReference<RelationCache> actual = new AtomicReference<>(new RelationCache(1, false));

            WorldLoopExtension.runInPass(() -> actual.set(new TestAttackable().getKnownRelation(7)));

            assertThat(actual.get()).isNull();
        }
    }

    @Nested
    class CollectionGetters {
        @ParameterizedTest(name = "{0}")
        @MethodSource("writesThroughGetters")
        void collectionGetter_shouldRefuseWrites(Consumer<Creature> write) {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> actual.set(catchThrowable(() -> write.accept(new TestAttackable()))));

            assertThat(actual.get()).isInstanceOf(UnsupportedOperationException.class);
        }

        static Stream<Named<Consumer<Creature>>> writesThroughGetters() {
            return Stream.of(
                    Named.of(
                            "getAttackByList",
                            creature -> creature.getAttackByList().clear()),
                    Named.of("getSkills", creature -> creature.getSkills().clear()),
                    Named.of("getAllSkills", creature -> creature.getAllSkills().clear()),
                    Named.of(
                            "getSkillReuseTimeStamps",
                            creature -> creature.getSkillReuseTimeStamps().clear()),
                    Named.of(
                            "getItemReuseTimeStamps",
                            creature -> creature.getItemReuseTimeStamps().clear()));
        }

        @Test
        void getSkills_shouldVisitEverySkill_whenSkillsAreRemovedWhileIterating() {
            final Skill first = skill(1);
            final Skill second = skill(2);
            final List<Skill> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addSkill(first);
                attackable.addSkill(second);
                for (Skill skill : attackable.getSkills().values()) {
                    actual.add(skill);
                    attackable.removeSkill(skill.getId());
                }
            });

            assertThat(actual).containsExactlyInAnyOrder(first, second);
        }

        private static Skill skill(int id) {
            final Skill skill = mock(Skill.class);
            when(skill.getId()).thenReturn(id);
            return skill;
        }
    }

    @Nested
    class Title {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void setTitle_shouldHoldNoTitle_whenTheValueIsBlank(String value) {
            final AtomicReference<String> actual = new AtomicReference<>("unset");

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.setTitle("Guard");
                attackable.setTitle(value);
                actual.set(attackable.getTitle());
            });

            assertThat(actual.get()).isNull();
        }

        @Test
        void setTitle_shouldKeepTheTitle_whenItHasText() {
            final AtomicReference<String> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.setTitle(" Guard ");
                actual.set(attackable.getTitle());
            });

            assertThat(actual.get()).isEqualTo(" Guard ");
        }
    }

    @Nested
    class DoAttack {
        @Test
        void doAttack_shouldIgnoreAReentrantCall() {
            final AtomicReference<ReentrantAttackable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final ReentrantAttackable attackable = new ReentrantAttackable();
                attackable.doAttack(new TestAttackable());
                actual.set(attackable);
            });

            assertThat(actual.get().attackChecks)
                    .as("only the outer attack reaches the hook")
                    .isEqualTo(1);
        }
    }

    private static final class ReentrantAttackable extends TestAttackable {
        private int attackChecks;

        @Override
        public boolean isAttackDisabled() {
            attackChecks++;
            if (attackChecks == 1) {
                doAttack(new TestAttackable());
            }

            return true;
        }
    }

    private static Creature attackableOnTheLoop() {
        final AtomicReference<Creature> creature = new AtomicReference<>();
        WorldLoopExtension.runInPass(() -> creature.set(new TestAttackable()));
        assertThat(creature.get()).as("attackable built on the loop").isNotNull();
        return creature.get();
    }
}
