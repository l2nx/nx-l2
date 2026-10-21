/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.boot.ServerExit;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.network.gslink.LoginServerLink;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;
import org.springframework.context.support.GenericApplicationContext;

// The engine keeps static state (config, thread pools, singletons), so nothing here may run concurrently.
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(ClockBridgeExtension.class)
@ResourceLock(ClockBridgeExtension.LOCK)
class GameServerTest {
    private static final long THREAD_EXIT_TIMEOUT_MS = 10_000;

    @Nested
    class Start {
        @Test
        void start_shouldStopEngineThreads_whenStartFails() throws Exception {
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final GameServer server = new GameServer(
                    new ServerExit(new GenericApplicationContext()),
                    registry,
                    mock(LoginServerLink.class),
                    mock(LoginService.class),
                    mock(NetworkEventLoops.class),
                    NetworkProperties.defaults(),
                    new NetworkMetrics(registry),
                    mock(SessionFactory.class),
                    mock(GameProtocolRegistry.class));

            assertThatThrownBy(server::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("before the Spring context created it");

            assertThat(server.isRunning()).isFalse();
            assertThat(registry.find("l2nx.legacy.task.all").timer())
                    .as("thread pool started")
                    .isNotNull();
            assertThat(registry.find("executor.pool.size")
                            .tag("name", "l2nx-scheduled")
                            .gauge())
                    .as("thread pool meters unbound on abort")
                    .isNull();
            assertThat(registry.find("l2nx.legacy.task.lag")
                            .tag("pool", "l2nx-scheduled")
                            .timer())
                    .as("legacy task lag")
                    .isNotNull();
            assertThat(registry.find("l2nx.legacy.task")
                            .tag("code.namespace", "app.l2nx.highfive.threads.ThreadPool")
                            .timer())
                    .as("purge task measured")
                    .isNotNull();
            final List<Thread> engineThreads = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().startsWith("l2nx ")
                            || thread.getName().equals("GameTimeTaskManager"))
                    .toList();
            for (Thread thread : engineThreads) {
                thread.join(THREAD_EXIT_TIMEOUT_MS);
            }
            assertThat(engineThreads).as("engine threads").noneMatch(Thread::isAlive);
        }
    }

    @Nested
    class LeaveLoginServer {
        private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

        private final LoginServerLink link = mock(LoginServerLink.class);
        private final LoginService loginService = mock(LoginService.class);
        private final SessionFactory sessionFactory = mock(SessionFactory.class);
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final GameServer server = new GameServer(
                new ServerExit(new GenericApplicationContext()),
                registry,
                link,
                loginService,
                mock(NetworkEventLoops.class),
                NetworkProperties.defaults().toBuilder()
                        .shutdownTimeout(SHUTDOWN_TIMEOUT)
                        .build(),
                new NetworkMetrics(registry),
                sessionFactory,
                mock(GameProtocolRegistry.class));

        @Test
        void leaveLoginServer_shouldLogOutAndShutTheLinkDown_afterEverySessionClosed() {
            when(sessionFactory.awaitClosed(SHUTDOWN_TIMEOUT)).thenReturn(true);

            server.leaveLoginServer();

            final InOrder order = inOrder(sessionFactory, loginService, link);
            order.verify(sessionFactory).awaitClosed(SHUTDOWN_TIMEOUT);
            order.verify(loginService).logoutAll();
            order.verify(link).shutdown();
        }

        @Test
        void leaveLoginServer_shouldStillLogOutAndShutTheLinkDown_whenSessionsRemainAfterTheTimeout() {
            when(sessionFactory.awaitClosed(SHUTDOWN_TIMEOUT)).thenReturn(false);
            when(sessionFactory.openSessions()).thenReturn(2);

            server.leaveLoginServer();

            final InOrder order = inOrder(loginService, link);
            order.verify(loginService).logoutAll();
            order.verify(link).shutdown();
        }

        @Test
        void leaveLoginServer_shouldShutTheLinkDown_whenTheLogoutFails() {
            when(sessionFactory.awaitClosed(SHUTDOWN_TIMEOUT)).thenReturn(true);
            doThrow(new IllegalStateException("thrown by the test"))
                    .when(loginService)
                    .logoutAll();

            server.leaveLoginServer();

            verify(link).shutdown();
        }
    }
}
