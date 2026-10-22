/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.Loop;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class WorldPartitionTest {
    private final ManualGameClock clock = new ManualGameClock(WorldLoopExtension.WALL_START, ZoneOffset.UTC);
    private final Loop loop = WorldLoopExtension.newLoop(clock);

    private Loop previous;

    @BeforeEach
    void unbind() {
        previous = WorldPartition.swap(null);
    }

    @AfterEach
    void restore() {
        WorldPartition.swap(previous);
    }

    private void runPass() {
        loop.runPass(clock.gameMillis());
    }

    @Nested
    class Unbound {
        static Stream<Arguments> entries() {
            return Stream.of(
                    entry("loop", WorldPartition::loop),
                    entry("checkOwner", WorldPartition::checkOwner),
                    entry("offer", () -> WorldPartition.offer(() -> {})));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("entries")
        void entry_shouldThrow_whenUnbound(String name, ThrowingCallable entry) {
            assertThatIllegalStateException().isThrownBy(entry).withMessageContaining("not running");
        }

        private static Arguments entry(String name, ThrowingCallable entry) {
            return Arguments.of(name, entry);
        }
    }

    @Nested
    class LoopOf {
        @Test
        void loop_shouldReturnTheBoundLoop_whenBound() {
            WorldPartition.bind(loop);

            assertThat(WorldPartition.loop()).isSameAs(loop);
        }
    }

    @Nested
    class Offer {
        @Test
        void offer_shouldRunInTheNextPass_whenCalledOffLoop() {
            WorldPartition.bind(loop);
            final List<Boolean> ranInLoop = new ArrayList<>();

            WorldPartition.offer(() -> ranInLoop.add(loop.inLoop()));
            assertThat(ranInLoop).isEmpty();

            runPass();
            assertThat(ranInLoop).containsExactly(true);
        }

        @Test
        void offer_shouldRunInTheNextPass_whenCalledInPass() {
            WorldPartition.bind(loop);
            final List<String> ran = new ArrayList<>();
            loop.execute(() -> {
                WorldPartition.offer(() -> ran.add("offered"));
                ran.add("pass");
            });

            runPass();
            assertThat(ran).containsExactly("pass");

            runPass();
            assertThat(ran).containsExactly("pass", "offered");
        }
    }

    @Nested
    class CheckOwner {
        @Test
        void checkOwner_shouldThrow_whenCalledOffLoop() {
            WorldPartition.bind(loop);

            assertThatIllegalStateException()
                    .isThrownBy(WorldPartition::checkOwner)
                    .withMessageContaining("touched from thread");
        }

        @Test
        void checkOwner_shouldPass_whenCalledInPass() {
            WorldPartition.bind(loop);
            final AtomicReference<String> outcome = new AtomicReference<>();
            loop.execute(() -> {
                try {
                    WorldPartition.checkOwner();
                    outcome.set("passed");
                } catch (IllegalStateException e) {
                    outcome.set(e.getMessage());
                }
            });

            runPass();
            assertThat(outcome).hasValue("passed");
        }
    }
}
