/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.tasks.cubics;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class CubicTasksTest {
    static Stream<Named<Supplier<Runnable>>> tasks() {
        return Stream.of(
                Named.of("CubicAction", () -> new CubicAction(null, 100)),
                Named.of("CubicHeal", () -> new CubicHeal(null)),
                Named.of("CubicDisappear", () -> new CubicDisappear(null)));
    }

    @Nested
    class Run {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.entity.actor.tasks.cubics.CubicTasksTest#tasks")
        void run_shouldThrow_whenCalledOffTheLoop(Supplier<Runnable> task) {
            final Runnable actual = task.get();

            assertThatThrownBy(actual::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.entity.actor.tasks.cubics.CubicTasksTest#tasks")
        void run_shouldDoNothing_whenTheCubicIsGone(Supplier<Runnable> task) {
            final Runnable actual = task.get();

            assertThatCode(() -> WorldLoopExtension.runInPass(actual)).doesNotThrowAnyException();
        }
    }
}
