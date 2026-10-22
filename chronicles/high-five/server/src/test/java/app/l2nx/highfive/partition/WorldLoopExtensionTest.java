/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.Loop;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class WorldLoopExtensionTest {
    @Nested
    class RunInPass {
        @Test
        void runInPass_shouldRunTheBodyOnTheBoundWorldLoop(Loop world) {
            final List<Boolean> ranInLoop = new ArrayList<>();

            WorldLoopExtension.runInPass(
                    () -> ranInLoop.add(WorldPartition.loop().inLoop()));

            assertThat(WorldPartition.loop()).isSameAs(world);
            assertThat(ranInLoop).containsExactly(true);
        }
    }

    @Nested
    class PassAfter {
        @Test
        void passAfter_shouldFireTimers_whenTheirDelayHasPassed() {
            final List<String> ran = new ArrayList<>();
            WorldLoopExtension.runInPass(
                    () -> WorldPartition.loop().schedule(() -> ran.add("timer"), 100, TimeUnit.MILLISECONDS));

            WorldLoopExtension.passAfter(Duration.ofMillis(99));
            assertThat(ran).isEmpty();

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            assertThat(ran).containsExactly("timer");
        }
    }
}
