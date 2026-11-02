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
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.WorldRegion;
import app.l2nx.highfive.entity.actor.holders.npc.AbsorberInfo;
import app.l2nx.highfive.entity.actor.holders.npc.AggroInfo;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
class AttackableTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Attackable> call) {
            final Attackable attackable = attackableOnTheLoop();

            assertThatThrownBy(() -> call.accept(attackable)).isInstanceOf(IllegalStateException.class);
        }

        static Stream<Named<Consumer<Attackable>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("addDamageHate", attackable -> attackable.addDamageHate(null, 10, 10)),
                    Named.of("reduceCurrentHp (override)", attackable -> attackable.reduceCurrentHp(1, null, null)),
                    Named.of("isRaid (accessor)", Attackable::isRaid),
                    Named.of("setOnKillDelay (accessor)", attackable -> attackable.setOnKillDelay(1)),
                    Named.of("getAggroList", Attackable::getAggroList),
                    Named.of("getAggroInfo", attackable -> attackable.getAggroInfo(null)),
                    Named.of("isAggroListEmpty", Attackable::isAggroListEmpty),
                    Named.of(
                            "collectAttackers",
                            attackable -> attackable.collectAttackers(attacker -> true, new ArrayList<>())),
                    Named.of("getMostHatedAttacker", attackable -> attackable.getMostHatedAttacker(attacker -> true)),
                    Named.of("addHateToAttackers", attackable -> attackable.addHateToAttackers(-1)),
                    Named.of("addAggroFrom", attackable -> attackable.addAggroFrom(attackable)),
                    Named.of("getAbsorberInfo", attackable -> attackable.getAbsorberInfo(1)),
                    Named.of("getMustRewardExpSP", Attackable::getMustRewardExpSP));
        }

        @Test
        void isAggroListEmpty_shouldAnswer_whenCalledOnTheLoop() {
            final Attackable attackable = attackableOnTheLoop();
            final AtomicReference<Boolean> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> actual.set(attackable.isAggroListEmpty()));

            assertThat(actual.get()).isTrue();
        }
    }

    @Nested
    class AggroLookups {
        @Test
        void getAggroInfo_shouldAnswerTheEntryOfTheAttacker() {
            final AtomicReference<AggroInfo> actual = new AtomicReference<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                expected.set(new TestAttackable());
                attackable.addDamageHate(expected.get(), 10, 10);
                actual.set(attackable.getAggroInfo(expected.get()));
            });

            assertThat(WorldLoopExtension.callInPass(actual.get()::getAttacker)).isSameAs(expected.get());
        }

        @Test
        void getAggroInfo_shouldAnswerNull_whenTheCreatureIsNoAttacker() {
            final AtomicReference<AggroInfo> actual = new AtomicReference<>(mock(AggroInfo.class));

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addDamageHate(new TestAttackable(), 10, 10);
                actual.set(attackable.getAggroInfo(new TestAttackable()));
            });

            assertThat(actual.get()).isNull();
        }

        @Test
        void isAggroListEmpty_shouldFollowTheList() {
            final List<Boolean> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                actual.add(attackable.isAggroListEmpty());
                attackable.addDamageHate(new TestAttackable(), 10, 10);
                actual.add(attackable.isAggroListEmpty());
                attackable.clearAggroList();
                actual.add(attackable.isAggroListEmpty());
            });

            assertThat(actual).containsExactly(true, false, true);
        }

        @Test
        void getAggroList_shouldKeepTheOrderTheAttackersCameIn() {
            final List<Creature> expected = new ArrayList<>();
            final List<Creature> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                for (int i = 0; i < 5; i++) {
                    expected.add(new TestAttackable());
                }
                expected.forEach(attacker -> attackable.addDamageHate(attacker, 10, 10));
                actual.addAll(attackable.getAggroList().keySet());
            });

            assertThat(actual).containsExactlyElementsOf(expected);
        }
    }

    @Nested
    class GetMostHated {
        @Test
        void getMostHated_shouldAnswerTheFirstAttacker_whenTwoHateEqually() {
            final List<Creature> actual = new ArrayList<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final WorldRegion region = region();
                final TestAttackable attackable = spawnedIn(region, new TestAttackable());
                expected.set(spawnedIn(region, new TestAttackable()));
                attackable.addDamageHate(expected.get(), 10, 50);
                attackable.addDamageHate(spawnedIn(region, new TestAttackable()), 10, 50);
                actual.add(attackable.getMostHated());
                actual.add(attackable.getMostHated());
            });

            assertThat(actual).containsExactly(expected.get(), expected.get());
        }
    }

    @Nested
    class CollectAttackers {
        @Test
        void collectAttackers_shouldAppendTheAcceptedAttackersInListOrder() {
            final List<Creature> expected = new ArrayList<>();
            final List<Creature> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                final Creature present = new TestAttackable();
                actual.add(present);
                expected.add(present);
                final List<Creature> attackers = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    attackers.add(new TestAttackable());
                }
                attackers.forEach(attacker -> attackable.addDamageHate(attacker, 10, 10));
                expected.add(attackers.get(1));
                expected.add(attackers.get(3));
                attackable.collectAttackers(attacker -> (attackers.indexOf(attacker) % 2) == 1, actual);
            });

            assertThat(actual).containsExactlyElementsOf(expected);
        }

        @Test
        void collectAttackers_shouldTestEveryAttacker_whenTheFilterClearsTheList() {
            final List<Creature> expected = new ArrayList<>();
            final List<Creature> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                for (int i = 0; i < 3; i++) {
                    expected.add(new TestAttackable());
                }
                expected.forEach(attacker -> attackable.addDamageHate(attacker, 10, 10));
                attackable.collectAttackers(
                        attacker -> {
                            attackable.clearAggroList();
                            return true;
                        },
                        actual);
            });

            assertThat(actual).containsExactlyElementsOf(expected);
        }
    }

    @Nested
    class GetMostHatedAttacker {
        @Test
        void getMostHatedAttacker_shouldAnswerTheAcceptedAttackerWithTheMostHate() {
            final AtomicReference<Creature> actual = new AtomicReference<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                final Creature rejected = new TestAttackable();
                expected.set(new TestAttackable());
                attackable.addDamageHate(new TestAttackable(), 10, 10);
                attackable.addDamageHate(rejected, 10, 90);
                attackable.addDamageHate(expected.get(), 10, 50);
                actual.set(attackable.getMostHatedAttacker(attacker -> attacker != rejected));
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }

        @Test
        void getMostHatedAttacker_shouldAnswerTheFirstAttacker_whenTwoHateEqually() {
            final AtomicReference<Creature> actual = new AtomicReference<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                expected.set(new TestAttackable());
                attackable.addDamageHate(expected.get(), 10, 50);
                attackable.addDamageHate(new TestAttackable(), 10, 50);
                actual.set(attackable.getMostHatedAttacker(attacker -> true));
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }

        @Test
        void getMostHatedAttacker_shouldAnswerTheAttacker_whenItsHateIsNegative() {
            final AtomicReference<Creature> actual = new AtomicReference<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                expected.set(new TestAttackable());
                attackable.addDamageHate(expected.get(), 10, -5);
                actual.set(attackable.getMostHatedAttacker(attacker -> true));
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }

        @Test
        void getMostHatedAttacker_shouldAnswerNull_whenTheFilterAcceptsNone() {
            final AtomicReference<Creature> actual = new AtomicReference<>(mock(Creature.class));

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addDamageHate(new TestAttackable(), 10, 10);
                actual.set(attackable.getMostHatedAttacker(attacker -> false));
            });

            assertThat(actual.get()).isNull();
        }
    }

    @Nested
    class AddHateToAttackers {
        @Test
        void addHateToAttackers_shouldAddTheHateToEveryAttackerAndNoDamage() {
            final List<Long> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                final Creature first = new TestAttackable();
                final Creature second = new TestAttackable();
                attackable.addDamageHate(first, 10, 10);
                attackable.addDamageHate(second, 10, 30);
                attackable.addHateToAttackers(-5);
                actual.add(attackable.getAggroInfo(first).getHate());
                actual.add(attackable.getAggroInfo(second).getHate());
                actual.add(attackable.getAggroInfo(first).getDamage());
            });

            assertThat(actual).containsExactly(5L, 25L, 10L);
        }
    }

    @Nested
    class AddAggroFrom {
        @Test
        void addAggroFrom_shouldAddEveryAttackerOfTheSource() {
            final AtomicReference<Set<Creature>> actual = new AtomicReference<>();
            final List<Creature> expected = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable source = new TestAttackable();
                final TestAttackable minion = new TestAttackable();
                expected.add(new TestAttackable());
                expected.add(new TestAttackable());
                expected.forEach(attacker -> source.addDamageHate(attacker, 10, 10));
                minion.addAggroFrom(source);
                actual.set(Set.copyOf(minion.getAggroList().keySet()));
            });

            assertThat(actual.get()).containsExactlyInAnyOrderElementsOf(expected);
        }

        @Test
        void addAggroFrom_shouldChangeNothing_whenTheSourceIsItself() {
            final List<Long> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                final TestAttackable attacker = new TestAttackable();
                attackable.addDamageHate(attacker, 10, 25);
                attackable.addAggroFrom(attackable);
                actual.add(attackable.getAggroInfo(attacker).getHate());
                actual.add(attackable.getAggroInfo(attacker).getDamage());
            });

            assertThat(actual).containsExactly(25L, 10L);
        }

        @Test
        void addAggroFrom_shouldGiveTheMinionItsOwnEntryWithTheHateOfTheSourceAndNoDamage() {
            final AtomicReference<AggroInfo> actual = new AtomicReference<>();
            final AtomicReference<AggroInfo> source = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable master = new TestAttackable();
                final TestAttackable minion = new TestAttackable();
                final TestAttackable attacker = new TestAttackable();
                master.addDamageHate(attacker, 10, 25);
                minion.addAggroFrom(master);
                source.set(master.getAggroInfo(attacker));
                actual.set(minion.getAggroInfo(attacker));
            });

            assertThat(actual.get()).isNotSameAs(source.get());
            assertThat(WorldLoopExtension.callInPass(actual.get()::getHate)).isEqualTo(25);
            assertThat(WorldLoopExtension.callInPass(actual.get()::getDamage)).isZero();
        }

        @Test
        void addAggroFrom_shouldKeepTheSourceEntry_whenTheMinionIsDamagedLater() {
            final AtomicReference<AggroInfo> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable master = new TestAttackable();
                final TestAttackable minion = new TestAttackable();
                final TestAttackable attacker = new TestAttackable();
                master.addDamageHate(attacker, 10, 25);
                minion.addAggroFrom(master);
                minion.addDamageHate(attacker, 500, 500);
                actual.set(master.getAggroInfo(attacker));
            });

            assertThat(WorldLoopExtension.callInPass(actual.get()::getDamage)).isEqualTo(10);
            assertThat(WorldLoopExtension.callInPass(actual.get()::getHate)).isEqualTo(25);
        }
    }

    @Nested
    class CollectionGetters {
        @ParameterizedTest(name = "{0}")
        @MethodSource("writesThroughGetters")
        void collectionGetter_shouldRefuseWrites(Consumer<Attackable> write) {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addDamageHate(new TestAttackable(), 10, 10);
                actual.set(catchThrowable(() -> write.accept(attackable)));
            });

            assertThat(actual.get()).isInstanceOf(UnsupportedOperationException.class);
        }

        static Stream<Named<Consumer<Attackable>>> writesThroughGetters() {
            return Stream.of(
                    Named.of(
                            "getAggroList",
                            attackable -> attackable.getAggroList().clear()),
                    Named.of(
                            "getAggroList values",
                            attackable -> attackable.getAggroList().values().clear()),
                    Named.of(
                            "getHateList",
                            attackable -> attackable.getHateList().clear()));
        }

        @Test
        void getAggroList_shouldVisitEveryAttacker_whenTheListIsClearedWhileIterating() {
            final List<Creature> expected = new ArrayList<>();
            final List<Creature> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                for (int i = 0; i < 3; i++) {
                    expected.add(new TestAttackable());
                }
                expected.forEach(attacker -> attackable.addDamageHate(attacker, 10, 10));
                for (Creature attacker : attackable.getAggroList().keySet()) {
                    actual.add(attacker);
                    attackable.clearAggroList();
                }
            });

            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Nested
    class Absorbers {
        @Test
        void getAbsorberInfo_shouldAnswerTheAbsorber_whenItAbsorbedTheSoul() {
            final Player absorber = player(0x7ffe_0201);
            final AtomicReference<AbsorberInfo> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addAbsorber(absorber);
                actual.set(attackable.getAbsorberInfo(absorber.getObjectId()));
            });

            assertThat(WorldLoopExtension.callInPass(actual.get()::getObjectId)).isEqualTo(absorber.getObjectId());
        }

        @Test
        void getAbsorberInfo_shouldAnswerNull_whenTheListWasReset() {
            final Player absorber = player(0x7ffe_0202);
            final AtomicReference<AbsorberInfo> actual = new AtomicReference<>(mock(AbsorberInfo.class));

            WorldLoopExtension.runInPass(() -> {
                final TestAttackable attackable = new TestAttackable();
                attackable.addAbsorber(absorber);
                attackable.resetAbsorbList();
                actual.set(attackable.getAbsorberInfo(absorber.getObjectId()));
            });

            assertThat(actual.get()).isNull();
        }
    }

    private static WorldRegion region() {
        final WorldRegion region = new WorldRegion(0, 0, 0);
        region.setSurroundingRegions(new WorldRegion[] {region});
        return region;
    }

    private static <T extends Creature> T spawnedIn(WorldRegion region, T creature) {
        creature.setSpawned(true);
        creature.setWorldRegion(region);
        return creature;
    }

    private static Player player(int objectId) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        return player;
    }

    private static Attackable attackableOnTheLoop() {
        final AtomicReference<Attackable> attackable = new AtomicReference<>();
        WorldLoopExtension.runInPass(() -> attackable.set(new TestAttackable()));
        assertThat(attackable.get()).as("attackable built on the loop").isNotNull();
        return attackable.get();
    }
}
