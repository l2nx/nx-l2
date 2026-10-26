/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.ai.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.partition.WorldLoopExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class AttackableThinkSystemTest {
    private static final Duration CADENCE = Duration.ofSeconds(1);

    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    private final AttackableThinkSystem system = new AttackableThinkSystem();

    @BeforeEach
    void installTheSystem(Loop world) {
        WorldLoopExtension.runInPass(() -> system.install(world, CADENCE));
    }

    @Nested
    class Run {
        @Test
        void run_shouldThinkEveryAttackableOnceInInsertionOrder() {
            final CreatureAI first = mock(CreatureAI.class);
            final CreatureAI second = mock(CreatureAI.class);
            add(attackable(first), attackable(second));

            WorldLoopExtension.passAfter(CADENCE);

            final InOrder order = inOrder(first, second);
            order.verify(first).notifyActionThink();
            order.verify(second).notifyActionThink();
        }

        @Test
        void run_shouldWaitForTheCadence() {
            final CreatureAI ai = mock(CreatureAI.class);
            add(attackable(ai));

            WorldLoopExtension.passAfter(CADENCE.minusMillis(1));
            verify(ai, never()).notifyActionThink();

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            verify(ai).notifyActionThink();
        }

        @Test
        void run_shouldSkipAnAttackable_whenAnEarlierThinkRemovedIt() {
            final CreatureAI removed = mock(CreatureAI.class);
            final Attackable victim = attackable(removed);
            final CreatureAI killer = mock(CreatureAI.class);
            doAnswer(invocation -> {
                        system.remove(victim);
                        return null;
                    })
                    .when(killer)
                    .notifyActionThink();
            add(attackable(killer), victim);

            WorldLoopExtension.passAfter(CADENCE);

            verify(removed, never()).notifyActionThink();
        }

        @Test
        void run_shouldThinkTheOthers_whenOneThinkThrows() {
            final CreatureAI failing = mock(CreatureAI.class);
            doThrow(new IllegalStateException("think failed")).when(failing).notifyActionThink();
            final CreatureAI next = mock(CreatureAI.class);
            add(attackable(failing), attackable(next));

            WorldLoopExtension.passAfter(CADENCE);
            WorldLoopExtension.passAfter(CADENCE);

            verify(failing, times(2)).notifyActionThink();
            verify(next, times(2)).notifyActionThink();
        }

        @Test
        void run_shouldDropAnAttackable_whenItHasNoAI() {
            final CreatureAI ai = mock(CreatureAI.class);
            final Attackable attackable = attackable(ai);
            add(attackable);
            when(attackable.hasAI()).thenReturn(false);
            WorldLoopExtension.passAfter(CADENCE);

            when(attackable.hasAI()).thenReturn(true);
            WorldLoopExtension.passAfter(CADENCE);

            verify(ai, never()).notifyActionThink();
        }
    }

    @Nested
    class Add {
        @Test
        void add_shouldThinkAnAttackableOnce_whenAddedTwice() {
            final CreatureAI ai = mock(CreatureAI.class);
            final Attackable attackable = attackable(ai);
            add(attackable, attackable);

            WorldLoopExtension.passAfter(CADENCE);

            verify(ai).notifyActionThink();
        }
    }

    @Nested
    class Install {
        @Test
        void install_shouldForgetTheAttackablesOfThePreviousWorld() {
            final CreatureAI stale = mock(CreatureAI.class);
            final CreatureAI fresh = mock(CreatureAI.class);
            add(attackable(stale));
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final Loop restarted = new Loop(
                    "restarted",
                    new ManualGameClock(WorldLoopExtension.WALL_START, ZoneOffset.UTC),
                    WorldLoopExtension.SEED,
                    EngineProperties.defaults(),
                    registry);

            WorldLoopExtension.runInPass(() -> system.install(restarted, CADENCE));
            add(attackable(fresh));
            WorldLoopExtension.passAfter(CADENCE);

            assertThat(registry.find("l2nx.engine.system")
                            .tags("system", AttackableThinkSystem.SYSTEM)
                            .timer())
                    .isNotNull();
            verify(stale, never()).notifyActionThink();
            verify(fresh).notifyActionThink();
        }
    }

    private void add(Attackable... attackables) {
        WorldLoopExtension.runInPass(() -> {
            for (Attackable attackable : attackables) {
                system.add(attackable);
            }
        });
    }

    private static Attackable attackable(CreatureAI ai) {
        final Attackable attackable = mock(Attackable.class);
        when(attackable.hasAI()).thenReturn(true);
        when(attackable.getAI()).thenReturn(ai);
        return attackable;
    }
}
