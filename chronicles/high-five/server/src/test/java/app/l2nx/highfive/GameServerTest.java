/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.network.gslink.LoginServerLink;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.network.bootstrap.NetworkServer;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.network.transport.TransportResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.InOrder;

// The port comes from the static ServerConfig, which a concurrent world load would overwrite.
@Isolated
class GameServerTest {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);
    private static final int CONNECT_TIMEOUT_MS = 2_000;

    private final LoginServerLink link = mock(LoginServerLink.class);
    private final LoginService loginService = mock(LoginService.class);
    private final SessionFactory sessionFactory = mock(SessionFactory.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NetworkProperties properties = NetworkProperties.defaults().toBuilder()
            .shutdownTimeout(SHUTDOWN_TIMEOUT)
            .build();

    private GameServer server(NetworkEventLoops eventLoops) {
        return new GameServer(
                link,
                loginService,
                eventLoops,
                properties,
                new NetworkMetrics(registry),
                sessionFactory,
                mock(GameProtocolRegistry.class));
    }

    @Nested
    class Phase {
        @Test
        void getPhase_shouldStopAfterThePartitionRuntime() {
            final int actual = server(mock(NetworkEventLoops.class)).getPhase();

            assertThat(actual).isLessThan(PartitionRuntime.PHASE);
        }
    }

    // A freed port number belongs to the whole machine: another process may listen on it at once, so the stopped and
    // released checks read the server's own listening state instead of probing the number.
    @Nested
    class Port {
        private NetworkEventLoops eventLoops;
        private int previousPort;

        @BeforeEach
        void bindTheLoops() {
            eventLoops = new NetworkEventLoops(properties, TransportResolver.nativeAvailability());
            previousPort = ServerConfig.PORT_GAME;
            ServerConfig.PORT_GAME = 0;
        }

        @AfterEach
        void closeTheLoops() {
            ServerConfig.PORT_GAME = previousPort;
            eventLoops.close();
        }

        @Test
        void openPort_shouldListenAndRegisterTheBoundPort() throws IOException {
            final GameServer server = server(eventLoops);

            server.openPort();

            final int bound = server.clientPort().localAddress().getPort();
            try (Socket socket = connect(bound)) {
                assertThat(socket.isConnected()).isTrue();
            }
            verify(link).start(bound);
            server.stop();
        }

        @Test
        void openPort_shouldReleaseThePort_whenTheLinkFailsToStart() {
            final GameServer server = server(eventLoops);
            final AtomicReference<NetworkServer> opened = new AtomicReference<>();
            doAnswer(invocation -> {
                        opened.set(server.clientPort());
                        throw new IllegalStateException("thrown by the test");
                    })
                    .when(link)
                    .start(anyInt());

            assertThatIllegalStateException().isThrownBy(server::openPort).withMessage("thrown by the test");

            assertThat(opened.get().isListening()).isFalse();
            assertThat(server.clientPort()).isNull();
        }

        @Test
        void stopPort_shouldRefuseNewConnections_whenOpen() {
            final GameServer server = server(eventLoops);
            server.openPort();
            final NetworkServer port = server.clientPort();

            server.stopPort();

            assertThat(port.isListening()).isFalse();
            server.stop();
        }
    }

    @Nested
    class Stop {
        private final GameServer server = server(mock(NetworkEventLoops.class));

        @Test
        void stop_shouldLeaveTheLoginServer_whenThePortNeverOpened() {
            when(sessionFactory.awaitClosed(SHUTDOWN_TIMEOUT)).thenReturn(true);
            server.start();

            server.stop();

            final InOrder order = inOrder(sessionFactory, loginService, link);
            order.verify(sessionFactory).awaitClosed(SHUTDOWN_TIMEOUT);
            order.verify(loginService).logoutAll();
            order.verify(link).shutdown();
            assertThat(server.isRunning()).isFalse();
        }
    }

    @Nested
    class LeaveLoginServer {
        private final GameServer server = server(mock(NetworkEventLoops.class));

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

    private static Socket connect(int port) throws IOException {
        final Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        return socket;
    }
}
