/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.GameServer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GamePortLifecycleTest {
    private final GameServer gameServer = mock(GameServer.class);
    private final GamePortLifecycle lifecycle = new GamePortLifecycle(gameServer);

    @Nested
    class Phase {
        @Test
        void getPhase_shouldStartAfterTheWorld() {
            assertThat(lifecycle.getPhase()).isGreaterThan(WorldLifecycle.PHASE);
        }
    }

    @Nested
    class Start {
        @Test
        void start_shouldOpenThePort() {
            lifecycle.start();

            verify(gameServer).openPort();
            assertThat(lifecycle.isRunning()).isTrue();
        }

        @Test
        void start_shouldNotRun_whenThePortFailsToOpen() {
            doThrow(new IllegalStateException("thrown by the test"))
                    .when(gameServer)
                    .openPort();

            assertThatIllegalStateException().isThrownBy(lifecycle::start);

            assertThat(lifecycle.isRunning()).isFalse();
        }
    }

    @Nested
    class Stop {
        @Test
        void stop_shouldOnlyStopThePort() {
            lifecycle.start();

            lifecycle.stop();

            verify(gameServer).stopPort();
            assertThat(lifecycle.isRunning()).isFalse();
        }

        @Test
        void stop_shouldNotRun_whenStoppingThePortFails() {
            lifecycle.start();
            doThrow(new IllegalStateException("thrown by the test"))
                    .when(gameServer)
                    .stopPort();

            assertThatIllegalStateException().isThrownBy(lifecycle::stop);

            assertThat(lifecycle.isRunning()).isFalse();
        }
    }
}
