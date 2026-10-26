/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.system;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.movement.Movement;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("app.l2nx.highfive.movement.Movement")
class MovementWorldSystemTest {
    private static final Duration PLAYER_CADENCE = Duration.ofMillis(30);
    private static final Duration NPC_CADENCE = Duration.ofMillis(70);

    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    @AfterEach
    void unbind() {
        Movement.bind(null);
    }

    @Nested
    class Install {
        @Test
        void install_shouldBindTheBridgeToSystemsMovingPlayersAndNpcsOnTheirConfiguredCadences(Loop world) {
            final Creature player = creature(true);
            final Creature npc = creature(false);
            final MovementSystemProperties properties = MovementSystemProperties.builder()
                    .playerCadence(PLAYER_CADENCE)
                    .npcCadence(NPC_CADENCE)
                    .build();
            WorldLoopExtension.runInPass(() -> {
                new MovementWorldSystem(properties).install(world);
                Movement.register(player);
                Movement.register(npc);
            });

            WorldLoopExtension.passAfter(PLAYER_CADENCE);
            verify(player).updatePosition();
            verify(npc, never()).updatePosition();

            WorldLoopExtension.passAfter(NPC_CADENCE.minus(PLAYER_CADENCE));
            verify(npc).updatePosition();
        }
    }

    private static Creature creature(boolean player) {
        final Creature creature = mock(Creature.class);
        when(creature.isPlayer()).thenReturn(player);
        when(creature.getAI()).thenReturn(mock(CreatureAI.class));
        return creature;
    }
}
