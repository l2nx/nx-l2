/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.ai.system;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class AiWorldSystemTest {
    private static final Duration THINK_CADENCE = Duration.ofMillis(700);

    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    @Nested
    class Install {
        @Test
        void install_shouldThinkOnTheConfiguredCadence(Loop world) {
            final CreatureAI ai = mock(CreatureAI.class);
            final Attackable attackable = mock(Attackable.class);
            when(attackable.hasAI()).thenReturn(true);
            when(attackable.getAI()).thenReturn(ai);
            WorldLoopExtension.runInPass(() -> {
                new AiWorldSystem(new AiSystemProperties(THINK_CADENCE)).install(world);
                AttackableThinkSystem.getInstance().add(attackable);
            });

            WorldLoopExtension.passAfter(THINK_CADENCE.minusMillis(1));
            verify(ai, never()).notifyActionThink();

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            verify(ai).notifyActionThink();
        }
    }
}
