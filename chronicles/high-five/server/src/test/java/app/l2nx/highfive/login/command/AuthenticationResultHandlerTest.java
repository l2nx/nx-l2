/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopGroup;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.engine.core.session.DrainScheduler;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.engine.core.session.SessionMetrics;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.SessionTransport;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionAddress;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LoginFail;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class AuthenticationResultHandlerTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService loginService = new LoginService(port, accounts, new ServerListing());
    private final AuthenticationResultHandler handler = new AuthenticationResultHandler(port, loginService, accounts);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);

    AuthenticationResultHandlerTest() {
        when(session.attachment(GameClient.class)).thenReturn(client);
    }

    @Test
    void address_shouldBeTheRequest() {
        final UUID requestId = UUID.randomUUID();

        assertThat(AuthenticationResult.builder().requestId(requestId).build().address())
                .isEqualTo(new SessionAddress.Request(requestId));
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldEnterTheAccount_andListTheCharactersAfterAuthenticating_whenAccepted() {
            final UUID accountId = UUID.randomUUID();
            accounts.reserve("alice", client);
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(requestId)
                            .accountId(accountId)
                            .accepted(true)
                            .build());

            verify(client).setAccountId(accountId);
            verify(client).setSessionId(KEY);
            final InOrder order = inOrder(client, session);
            order.verify(client).authenticated();
            order.verify(session).offerInternal(new ListCharacters());
            assertThat(accounts.find(accountId)).isSameAs(client);
            assertThat(accounts.pendingAuthentications()).isEmpty();
            assertThat(port.getCalls()).containsExactly(Call.of("accountsEntered", List.of(accountId)));
        }

        @Test
        void handle_shouldCloseTheClient_andFreeItsLogin_whenRefused() {
            accounts.reserve("alice", client);
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(requestId)
                            .accepted(false)
                            .build());

            verify(client).close(any(LoginFail.class));
            verify(client).disconnect();
            assertThat(accounts.reserve("alice", client)).isTrue();
            assertThat(port.getCalls()).isEmpty();
        }

        @Test
        void handle_shouldIgnoreTheAnswer_whenTheRequestIsAnotherClients() {
            final GameClient other = mock(GameClient.class);
            final UUID requestId = accounts.awaitAuthentication("bob", other, KEY);

            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(requestId)
                            .accountId(UUID.randomUUID())
                            .accepted(true)
                            .build());

            verify(client, never()).setAccountId(any());
            assertThat(accounts.pendingAuthentications()).containsExactly(requestId);
            assertThat(port.getCalls()).isEmpty();
        }

        @Test
        void handle_shouldIgnoreTheAnswer_whenTheRequestIsGone() {
            handler.handle(
                    session,
                    AuthenticationResult.builder()
                            .requestId(UUID.randomUUID())
                            .accountId(UUID.randomUUID())
                            .accepted(true)
                            .build());

            verify(client, never()).setAccountId(any());
            assertThat(port.getCalls()).isEmpty();
        }
    }

    @Nested
    class OnTheWorldLoop {
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final CompletableFuture<String> listedOn = new CompletableFuture<>();
        private final LoopGroup group = new LoopGroup(
                "l2nx-loop",
                registry,
                EngineProperties.defaults(),
                (thread, failure) -> listedOn.completeExceptionally(failure));
        private final Loop world = new Loop(
                PartitionRuntime.WORLD,
                new SystemGameClock(Clock.systemUTC()),
                42L,
                EngineProperties.defaults(),
                registry);

        @AfterEach
        void stopTheLoop() {
            group.shutdown(Duration.ofSeconds(10));
        }

        @Test
        void handle_shouldListTheCharactersOnTheWorldLoop_whenAccepted() throws Exception {
            group.pin(world);
            final Session live = sessions().create(new InlineTransport());
            live.attach(client);
            doAnswer(invocation -> {
                        live.setState(SessionState.AUTHED);
                        return null;
                    })
                    .when(client)
                    .authenticated();
            accounts.reserve("alice", client);
            final UUID requestId = accounts.awaitAuthentication("alice", client, KEY);

            live.offerInternal(AuthenticationResult.builder()
                    .requestId(requestId)
                    .accountId(UUID.randomUUID())
                    .accepted(true)
                    .build());

            assertThat(listedOn.get(5, TimeUnit.SECONDS)).isEqualTo("l2nx-loop-world");
        }

        private SessionFactory sessions() {
            final SessionMetrics metrics = SessionMetrics.noop();
            final SessionCommandHandler<ListCharacters> listing = new SessionCommandHandler<>() {
                @Override
                public void handle(Session session, ListCharacters command) {
                    listedOn.complete(Thread.currentThread().getName());
                }
            };
            return new SessionFactory(
                    Session.Limits.of(NetworkProperties.defaults()),
                    DrainScheduler.pool((drain, abandoned) -> drain.run(), metrics.drainFailures()),
                    DrainScheduler.loop(() -> world),
                    new CommandDispatcher(
                            new CommandRegistry(List.of(handler, listing)),
                            address -> Optional.empty(),
                            NetworkMetrics.noop()),
                    metrics);
        }
    }

    private static final class InlineTransport implements SessionTransport {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public void stateChanged(SessionState from, SessionState to) {}

        @Override
        public void close(CloseReason reason) {}
    }
}
