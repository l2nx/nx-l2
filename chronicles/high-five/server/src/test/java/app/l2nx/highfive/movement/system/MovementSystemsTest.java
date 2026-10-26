/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.partition.WorldLoopExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class MovementSystemsTest {
    private static final Duration PLAYER_CADENCE = Duration.ofMillis(50);
    private static final Duration NPC_CADENCE = PLAYER_CADENCE.multipliedBy(2);

    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    private final MovementSystems systems = new MovementSystems();

    @BeforeEach
    void installTheSystems(Loop world) {
        WorldLoopExtension.runInPass(() -> systems.install(world, PLAYER_CADENCE, NPC_CADENCE));
    }

    @Nested
    class RegisterMovingObject {
        @Test
        void registerMovingObject_shouldMoveAPlayerOnlyInThePlayerSystem() {
            final Creature player = creature(true, false);
            register(player);

            WorldLoopExtension.passAfter(PLAYER_CADENCE);
            verify(player).updatePosition();

            WorldLoopExtension.passAfter(PLAYER_CADENCE);
            verify(player, times(2)).updatePosition();
        }

        @Test
        void registerMovingObject_shouldMoveAnNpcOnlyInTheNpcSystem() {
            final Creature npc = creature(false, false);
            register(npc);

            WorldLoopExtension.passAfter(PLAYER_CADENCE);
            verify(npc, never()).updatePosition();

            WorldLoopExtension.passAfter(PLAYER_CADENCE);
            verify(npc).updatePosition();
        }

        @Test
        void registerMovingObject_shouldMoveACreatureOnce_whenRegisteredTwice() {
            final Creature npc = creature(false, false);
            register(npc);
            register(npc);

            WorldLoopExtension.passAfter(NPC_CADENCE);

            verify(npc).updatePosition();
        }
    }

    @Nested
    class Move {
        @Test
        void move_shouldKeepUpdatingACreature_untilItArrives() {
            final Creature npc = creature(false, false);
            register(npc);

            WorldLoopExtension.passAfter(NPC_CADENCE);
            WorldLoopExtension.passAfter(NPC_CADENCE);

            verify(npc, times(2)).updatePosition();
            verify(npc.getAI(), never()).notifyActionArrived();
        }

        @Test
        void move_shouldNotifyTheArrivalOnceAndStop_whenTheCreatureArrives() {
            final Creature npc = creature(false, true);
            register(npc);

            WorldLoopExtension.passAfter(NPC_CADENCE);
            WorldLoopExtension.passAfter(NPC_CADENCE);

            verify(npc).updatePosition();
            verify(npc.getAI()).notifyActionArrived();
        }

        @Test
        void move_shouldMoveAgainInTheNextRunOnly_whenTheArrivalStartsANewMove() {
            final Creature npc = creature(false, true);
            final CreatureAI ai = npc.getAI();
            doAnswer(invocation -> {
                        systems.registerMovingObject(npc);
                        return null;
                    })
                    .when(ai)
                    .notifyActionArrived();
            register(npc);

            WorldLoopExtension.passAfter(NPC_CADENCE);
            verify(npc).updatePosition();

            WorldLoopExtension.passAfter(NPC_CADENCE);
            verify(npc, times(2)).updatePosition();
        }

        @Test
        void move_shouldDropACreatureAndMoveTheOthers_whenItsUpdateThrows() {
            final Creature failing = creature(false, false);
            when(failing.updatePosition()).thenThrow(new IllegalStateException("update failed"));
            final Creature next = creature(false, false);
            register(failing);
            register(next);

            WorldLoopExtension.passAfter(NPC_CADENCE);
            WorldLoopExtension.passAfter(NPC_CADENCE);

            verify(failing).updatePosition();
            verify(next, times(2)).updatePosition();
        }
    }

    @Nested
    class Install {
        @Test
        void install_shouldForgetTheCreaturesOfThePreviousWorld() {
            final Creature stalePlayer = creature(true, false);
            final Creature staleNpc = creature(false, false);
            final Creature fresh = creature(false, false);
            register(stalePlayer);
            register(staleNpc);
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final Loop restarted = new Loop(
                    "restarted",
                    new ManualGameClock(WorldLoopExtension.WALL_START, ZoneOffset.UTC),
                    WorldLoopExtension.SEED,
                    EngineProperties.defaults(),
                    registry);

            WorldLoopExtension.runInPass(() -> systems.install(restarted, PLAYER_CADENCE, NPC_CADENCE));
            register(fresh);
            WorldLoopExtension.passAfter(NPC_CADENCE);

            assertThat(Stream.of(MovementSystems.PLAYER_SYSTEM, MovementSystems.NPC_SYSTEM)
                            .map(system -> registry.find("l2nx.engine.system")
                                    .tags("system", system)
                                    .timer()))
                    .allSatisfy(timer -> assertThat(timer).isNotNull());
            verify(stalePlayer, never()).updatePosition();
            verify(staleNpc, never()).updatePosition();
            verify(fresh).updatePosition();
        }
    }

    private void register(Creature creature) {
        WorldLoopExtension.runInPass(() -> systems.registerMovingObject(creature));
    }

    private static Creature creature(boolean player, boolean arrives) {
        final Creature creature = mock(Creature.class);
        final CreatureAI ai = mock(CreatureAI.class);
        when(creature.isPlayer()).thenReturn(player);
        when(creature.updatePosition()).thenReturn(arrives);
        when(creature.getAI()).thenReturn(ai);
        return creature;
    }
}
