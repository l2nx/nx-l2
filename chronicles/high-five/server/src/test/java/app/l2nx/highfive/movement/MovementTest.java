/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.movement.system.MovementSystems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("app.l2nx.highfive.movement.Movement")
class MovementTest {
    private MovementSystems previous;

    @BeforeEach
    void unbind() {
        previous = Movement.swap(null);
    }

    @AfterEach
    void restore() {
        Movement.swap(previous);
    }

    @Nested
    class Register {
        @Test
        void register_shouldHandTheCreatureToTheBoundSystems() {
            final MovementSystems systems = mock(MovementSystems.class);
            final Creature creature = mock(Creature.class);
            Movement.bind(systems);

            Movement.register(creature);

            verify(systems).registerMovingObject(creature);
        }

        @Test
        void register_shouldThrow_whenUnbound() {
            final Creature creature = mock(Creature.class);

            assertThatIllegalStateException()
                    .isThrownBy(() -> Movement.register(creature))
                    .withMessageContaining("not installed");
        }
    }
}
