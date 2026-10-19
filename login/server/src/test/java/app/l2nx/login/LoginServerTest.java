/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.data.GameServerTable;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

@Execution(ExecutionMode.SAME_THREAD)
class LoginServerTest {
    private static final long THREAD_EXIT_TIMEOUT_MS = 10_000;
    private static final Duration RESTART_DELAY = Duration.ofHours(6);

    private final LoginController loginController = mock(LoginController.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ServerExit serverExit = mock(ServerExit.class);

    @Nested
    class Start {
        @Test
        void start_shouldShutDownStartedServices_whenClientPortIsTaken() throws Exception {
            final int gameServerPort = freePort();
            try (ServerSocket blocker = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                final LoginServer server = server(TestLoginProperties.create(
                        blocker.getLocalPort(), gameServerPort, TestLoginProperties.noRestart()));

                assertThatThrownBy(server::start)
                        .isInstanceOf(UncheckedIOException.class)
                        .hasMessageContaining(String.valueOf(blocker.getLocalPort()));

                assertThat(server.isRunning()).isFalse();
                awaitExit(server.getGameServerListener());
                assertThat(server.getGameServerListener().isAlive())
                        .as("game server listener thread")
                        .isFalse();
            }

            assertThatCode(() -> new ServerSocket(gameServerPort, 1, InetAddress.getLoopbackAddress()).close())
                    .as("game server port released")
                    .doesNotThrowAnyException();
            verify(loginController).shutdown();
        }

        @Test
        void start_shouldScheduleRestartOnTaskScheduler_whenScheduledRestartEnabled() throws Exception {
            final ScheduledFuture<?> restartTask = mock(ScheduledFuture.class);
            when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                    .thenAnswer(invocation -> restartTask);
            final LoginServer server = server(TestLoginProperties.create(
                    freePort(), freePort(), new LoginProperties.ScheduledRestart(true, RESTART_DELAY)));

            final Instant before = Instant.now();
            server.start();
            final Instant after = Instant.now();
            try {
                final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
                final ArgumentCaptor<Instant> startTime = ArgumentCaptor.forClass(Instant.class);
                verify(taskScheduler).schedule(task.capture(), startTime.capture());
                assertThat(startTime.getValue()).isBetween(before.plus(RESTART_DELAY), after.plus(RESTART_DELAY));

                task.getValue().run();
                verify(serverExit).exit(ServerExitCode.RESTART);
            } finally {
                server.stop();
            }

            verify(restartTask).cancel(false);
        }

        @Test
        void start_shouldNotScheduleRestart_whenScheduledRestartDisabled() throws Exception {
            final LoginServer server =
                    server(TestLoginProperties.create(freePort(), freePort(), TestLoginProperties.noRestart()));

            server.start();
            server.stop();

            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }
    }

    private LoginServer server(LoginProperties properties) {
        return new LoginServer(
                properties,
                loginController,
                mock(GameServerTable.class),
                taskScheduler,
                serverExit,
                new SimpleMeterRegistry());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void awaitExit(Thread thread) throws InterruptedException {
        thread.join(THREAD_EXIT_TIMEOUT_MS);
    }
}
