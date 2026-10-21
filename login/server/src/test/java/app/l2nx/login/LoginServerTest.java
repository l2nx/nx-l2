/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login;

import static org.assertj.core.api.Assertions.assertThat;
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
import app.l2nx.login.controller.ScrambledKeyPair;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.config.Transport;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.network.transport.TransportResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.grpc.server.GrpcServerFactory;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;
import org.springframework.scheduling.TaskScheduler;

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class LoginServerTest {
    private static final Duration RESTART_DELAY = Duration.ofHours(6);
    private static final ScrambledKeyPair RSA_KEYS = new ScrambledKeyPair(keyPair("RSA"));

    private final LoginController loginController = mock(LoginController.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ServerExit serverExit = mock(ServerExit.class);
    private final NetworkEventLoops eventLoops = new NetworkEventLoops(
            NetworkProperties.defaults().toBuilder()
                    .transport(Transport.NIO)
                    .workerThreads(1)
                    .shutdownQuietPeriod(Duration.ZERO)
                    .build(),
            TransportResolver.nativeAvailability());

    @BeforeEach
    void stubKeys() {
        when(loginController.getScrambledRSAKeyPair()).thenReturn(RSA_KEYS);
    }

    @AfterEach
    void closeEventLoops() {
        eventLoops.close();
    }

    @Nested
    class Start {
        @Test
        void start_shouldShutDownStartedServices_whenClientPortIsTaken() throws Exception {
            try (ServerSocket blocker = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                final LoginServer server =
                        server(TestLoginProperties.create(blocker.getLocalPort(), TestLoginProperties.noRestart()));

                assertThatThrownBy(server::start)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(String.valueOf(blocker.getLocalPort()));

                assertThat(server.isRunning()).isFalse();
            }

            verify(loginController).shutdown();
        }

        @Test
        void start_shouldScheduleRestartOnTaskScheduler_whenScheduledRestartEnabled() throws Exception {
            final ScheduledFuture<?> restartTask = mock(ScheduledFuture.class);
            when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                    .thenAnswer(invocation -> restartTask);
            final LoginServer server = server(
                    TestLoginProperties.create(freePort(), new LoginProperties.ScheduledRestart(true, RESTART_DELAY)));

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
        void start_shouldFail_whenRsaCannotUseTheKey() throws Exception {
            final ScrambledKeyPair notRsa = mock(ScrambledKeyPair.class);
            when(notRsa.getPrivateKey()).thenReturn(keyPair("EC").getPrivate());
            when(loginController.getScrambledRSAKeyPair()).thenReturn(notRsa);
            final LoginServer server = server(TestLoginProperties.create(freePort(), TestLoginProperties.noRestart()));

            assertThatThrownBy(server::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("c621");

            assertThat(server.isRunning()).isFalse();
            verify(loginController).shutdown();
        }

        @Test
        void start_shouldNotScheduleRestart_whenScheduledRestartDisabled() throws Exception {
            final LoginServer server = server(TestLoginProperties.create(freePort(), TestLoginProperties.noRestart()));

            server.start();
            server.stop();

            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }
    }

    @Nested
    class Phase {
        @Test
        void getPhase_shouldStartBeforeAndStopAfterTheGrpcServer() throws Exception {
            final GrpcServerLifecycle grpcServer = new GrpcServerLifecycle(
                    mock(GrpcServerFactory.class), Duration.ZERO, mock(ApplicationEventPublisher.class));

            final LoginServer server = server(TestLoginProperties.create(freePort(), TestLoginProperties.noRestart()));

            assertThat(server.getPhase()).isLessThan(grpcServer.getPhase());
        }
    }

    private LoginServer server(LoginProperties properties) {
        return new LoginServer(
                properties,
                loginController,
                mock(GameServerTable.class),
                taskScheduler,
                serverExit,
                new SimpleMeterRegistry(),
                eventLoops,
                NetworkProperties.defaults(),
                NetworkMetrics.noop());
    }

    private static KeyPair keyPair(String algorithm) {
        try {
            return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
