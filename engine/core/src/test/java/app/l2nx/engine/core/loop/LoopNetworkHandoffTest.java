/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.channel.TestHandoffs;
import app.l2nx.network.test.RecordingExecutor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopNetworkHandoffTest {
    private final Loop loop = TestLoops.loop(TestLoops.clock());
    private final RecordingExecutor executor = new RecordingExecutor();

    @Nested
    class RunPass {
        @Test
        void runPass_shouldOpenTheHandoffOnlyForItsPhases() {
            final List<Boolean> actual = new ArrayList<>();
            loop.offer(l -> actual.add(l.networkHandoff().outbound().isOpen()));

            loop.runPass(T0);

            assertThat(actual).containsExactly(true);
            assertThat(loop.networkHandoff().outbound().isOpen()).isFalse();
        }

        @Test
        void runPass_shouldWakeTheDeferredEventLoopAfterTheFlushHooks() {
            final List<Integer> wakeUpsInFlush = new ArrayList<>();
            loop.addHook(PassPhase.FLUSH, (l, now) -> wakeUpsInFlush.add(executor.immediate()));
            loop.offer(l -> TestHandoffs.defer(l.networkHandoff().outbound(), executor));

            loop.runPass(T0);

            assertThat(wakeUpsInFlush).containsExactly(0);
            assertThat(executor.immediate()).isEqualTo(1);
        }

        @Test
        void runPass_shouldWakeAndRethrow_whenAPhaseThrowsAnError() {
            final PhaseFailure failure = new PhaseFailure();
            loop.offer(l -> {
                TestHandoffs.defer(l.networkHandoff().outbound(), executor);
                throw failure;
            });

            assertThatThrownBy(() -> loop.runPass(T0)).isSameAs(failure);
            assertThat(executor.immediate()).isEqualTo(1);
            assertThat(loop.networkHandoff().outbound().isOpen()).isFalse();
        }

        @Test
        void runPass_shouldKeepThePhaseErrorAndSuppressTheWakeUpFailure_whenBothFail() {
            final PhaseFailure failure = new PhaseFailure();
            final PhaseFailure wakeUpFailure = new PhaseFailure();
            executor.failExecuteWith(wakeUpFailure);
            loop.offer(l -> {
                TestHandoffs.defer(l.networkHandoff().outbound(), executor);
                throw failure;
            });

            assertThatThrownBy(() -> loop.runPass(T0)).isSameAs(failure).hasSuppressedException(wakeUpFailure);
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldKeepTheHandoffClosed_whenAShutdownHookRuns() {
            final List<Boolean> actual = new ArrayList<>();
            loop.addShutdownHook(l -> actual.add(l.networkHandoff().outbound().isOpen()));

            loop.shutdown();

            assertThat(actual).containsExactly(false);
        }

        @Test
        void shutdown_shouldKeepTheHandoffClosed_whenAStopDrainRuns() {
            final List<Boolean> actual = new ArrayList<>();
            loop.offer(l -> actual.add(l.networkHandoff().outbound().isOpen()));

            loop.shutdown();

            assertThat(actual).containsExactly(false);
        }
    }

    private static final class PhaseFailure extends Error {
        PhaseFailure() {
            super("phase failure");
        }
    }
}
