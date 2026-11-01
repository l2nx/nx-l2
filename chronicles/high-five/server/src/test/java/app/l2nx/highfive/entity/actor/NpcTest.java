/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.mechanics.script.QuestTimer;
import app.l2nx.highfive.mechanics.script.timers.TimerHolder;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
class NpcTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Npc> call) {
            final Npc npc = npcOnTheLoop();

            assertThatThrownBy(() -> call.accept(npc))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Npc>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("setScriptValue (accessor)", npc -> npc.setScriptValue(1)),
                    Named.of("getScriptValue (accessor)", Npc::getScriptValue),
                    Named.of("isBusy (accessor)", Npc::isBusy),
                    Named.of("getSpawn (accessor)", Npc::getSpawn),
                    Named.of("isAutoAttackable (override)", npc -> npc.isAutoAttackable(null)),
                    Named.of("onRandomAnimation", npc -> npc.onRandomAnimation(1)),
                    Named.of("addSummonedNpc", npc -> npc.addSummonedNpc(npc)),
                    Named.of("getSummonedNpcCount", Npc::getSummonedNpcCount),
                    Named.of("addCreatureSeeId (static)", npc -> Npc.addCreatureSeeId(TestAttackable.NPC_ID)));
        }

        @Test
        void getScriptValue_shouldAnswer_whenCalledOnTheLoop() {
            final Npc npc = npcOnTheLoop();

            final int actual = WorldLoopExtension.callInPass(npc::getScriptValue);

            assertThat(actual).isZero();
        }
    }

    @Nested
    class ScriptValue {
        @Test
        void setScriptValue_shouldBeReadBack() {
            final int expected = 7;

            final int actual = WorldLoopExtension.callInPass(() -> {
                final Npc npc = new Npc(TestAttackable.template());
                npc.setScriptValue(expected);
                return npc.getScriptValue();
            });

            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class SummonedNpcs {
        @Test
        void addSummonedNpc_shouldBeFoundByObjectId() {
            final AtomicReference<Npc> actual = new AtomicReference<>();
            final AtomicReference<Npc> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final Npc summoner = new Npc(TestAttackable.template());
                expected.set(new Npc(TestAttackable.template()));
                summoner.addSummonedNpc(expected.get());
                actual.set(summoner.getSummonedNpc(expected.get().getObjectId()));
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }

        @Test
        void getSummonedNpcCount_shouldFollowAddRemoveAndReset() {
            final List<Integer> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final Npc summoner = new Npc(TestAttackable.template());
                final Npc first = new Npc(TestAttackable.template());
                actual.add(summoner.getSummonedNpcCount());
                summoner.addSummonedNpc(first);
                summoner.addSummonedNpc(new Npc(TestAttackable.template()));
                actual.add(summoner.getSummonedNpcCount());
                summoner.removeSummonedNpc(first.getObjectId());
                actual.add(summoner.getSummonedNpcCount());
                summoner.resetSummonedNpcs();
                actual.add(summoner.getSummonedNpcCount());
            });

            assertThat(actual).containsExactly(0, 2, 1, 0);
        }
    }

    @Nested
    class StopTimers {
        @Test
        void stopQuestTimers_shouldCancelEveryTimer_whenACancelRemovesItsTimer() {
            final List<QuestTimer> expected = List.of(mock(QuestTimer.class), mock(QuestTimer.class));

            WorldLoopExtension.runInPass(() -> {
                final Npc npc = new Npc(TestAttackable.template());
                for (QuestTimer timer : expected) {
                    doAnswer(invocation -> {
                                npc.removeQuestTimer(timer);
                                return null;
                            })
                            .when(timer)
                            .cancelTask();
                    npc.addQuestTimer(timer);
                }
                npc.stopQuestTimers();
            });

            expected.forEach(timer -> verify(timer).cancelTask());
        }

        @Test
        void stopTimerHolders_shouldCancelEveryTimer_whenACancelRemovesItsTimer() {
            final List<TimerHolder<?>> expected = List.of(mock(TimerHolder.class), mock(TimerHolder.class));

            WorldLoopExtension.runInPass(() -> {
                final Npc npc = new Npc(TestAttackable.template());
                for (TimerHolder<?> timer : expected) {
                    doAnswer(invocation -> {
                                npc.removeTimerHolder(timer);
                                return null;
                            })
                            .when(timer)
                            .cancelTask();
                    npc.addTimerHolder(timer);
                }
                npc.stopTimerHolders();
            });

            expected.forEach(timer -> verify(timer).cancelTask());
        }
    }

    @Nested
    class BusyMessage {
        @Test
        void getBusyMessage_shouldAnswerNull_whenNoneWasSet() {

            final String actual =
                    WorldLoopExtension.callInPass(() -> new Npc(TestAttackable.template()).getBusyMessage());

            assertThat(actual).isNull();
        }
    }

    @Nested
    class ToString {
        @Test
        void toString_shouldNameTheNpc_whenCalledOffTheLoop() {
            final Npc npc = npcOnTheLoop();
            final int objectId = WorldLoopExtension.callInPass(npc::getObjectId);

            final String actual = npc.toString();

            assertThat(actual).startsWith("Npc:").contains("(" + TestAttackable.NPC_ID + ")[" + objectId + "]");
        }

        @Test
        void toString_shouldNameTheAttackable_whenCalledOffTheLoop() {
            final Npc attackable = WorldLoopExtension.callInPass(TestAttackable::new);

            final String actual = attackable.toString();

            assertThat(actual).startsWith("TestAttackable:").contains("(" + TestAttackable.NPC_ID + ")");
        }
    }

    private static Npc npcOnTheLoop() {
        final Npc npc = WorldLoopExtension.callInPass(() -> new Npc(TestAttackable.template()));
        assertThat(npc).as("npc built on the loop").isNotNull();
        return npc;
    }
}
