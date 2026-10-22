/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import app.l2nx.load.bot.intent.EnterWorld;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.Login;
import app.l2nx.load.bot.intent.SelectServer;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.protocol.ClientSession;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class P267ProtocolTest {
    private static final LoadBotIdentity IDENTITY = LoadBotIdentity.builder()
            .index(0)
            .account("load0")
            .password("secret")
            .build();
    private static final Duration WAIT = Duration.ofSeconds(10);

    private FakeLoginServer server;
    private EventLoopGroup group;

    @AfterEach
    void tearDown() {
        if (group != null) {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
        if (server != null) {
            server.close();
        }
    }

    @Nested
    class Describe {
        @Test
        void capabilities_shouldCoverTheLoginExchange() {
            final P267Protocol protocol = protocol(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1));

            assertThat(protocol.id()).isEqualTo("p267");
            assertThat(protocol.capabilities())
                    .isUnmodifiable()
                    .containsExactlyInAnyOrder(IntentKind.LOGIN, IntentKind.SELECT_SERVER);
            assertThat(protocol.ioHandlerFactory()).isNotNull();
            assertThat(protocol.lastState()).isEqualTo(LoadBotState.GAME_CONNECT);
        }
    }

    @Nested
    class CheckIdentity {
        @Test
        void checkIdentity_shouldAccept_whenBothFieldsFillTheirOneBlockSize() {
            final P267Protocol protocol = protocol(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1));
            final LoadBotIdentity identity = IDENTITY.toBuilder()
                    .account("loadbotsxx9999")
                    .password("sixteen-bytes!!!")
                    .build();

            assertThatNoException().isThrownBy(() -> protocol.checkIdentity(identity));
        }

        @ParameterizedTest(name = "account [{0}]")
        @ValueSource(strings = {"loadbotsxx10000", "логин12345"})
        void checkIdentity_shouldReject_whenTheAccountTakesOverFourteenBytes(String account) {
            final P267Protocol protocol = protocol(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1));
            final LoadBotIdentity identity =
                    IDENTITY.toBuilder().account(account).build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> protocol.checkIdentity(identity))
                    .withMessageContaining(account)
                    .withMessageContaining("one-block");
        }

        @ParameterizedTest(name = "password [{0}]")
        @ValueSource(strings = {"seventeen-bytes!!", "пароль12345"})
        void checkIdentity_shouldRejectWithoutPrintingIt_whenThePasswordTakesOverSixteenBytes(String password) {
            final P267Protocol protocol = protocol(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1));
            final LoadBotIdentity identity =
                    IDENTITY.toBuilder().password(password).build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> protocol.checkIdentity(identity))
                    .withMessageContaining("its password does not fit")
                    .withMessageNotContaining(password);
        }

        @Test
        void checkIdentity_shouldReject_whenThePasswordIsBlank() {
            final P267Protocol protocol = protocol(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1));
            final LoadBotIdentity identity = IDENTITY.toBuilder().password(" ").build();

            assertThatIllegalArgumentException().isThrownBy(() -> protocol.checkIdentity(identity));
        }
    }

    @Nested
    class Open {
        @Test
        void open_shouldWalkTheWholeExchange_upToGameConnect() {
            server = new FakeLoginServer("load0", "secret", false);

            final Run run = run(server.address(), true);

            assertThat(run.events())
                    .containsExactly(
                            "state LOGIN_CONNECT",
                            "state LOGIN_AUTH",
                            "ack LOGIN",
                            "state SERVER_LIST",
                            "ack SELECT_SERVER",
                            "state GAME_CONNECT");
        }

        @Test
        void open_shouldRejectTheLoginAndStop_whenThePasswordIsWrong() {
            server = new FakeLoginServer("load0", "another", false);

            final Run run = run(server.address(), true);

            assertThat(run.events())
                    .containsExactly("state LOGIN_CONNECT", "state LOGIN_AUTH", "reject LOGIN", "state STOPPED");
        }

        @Test
        void open_shouldRejectTheSelectionAndStop_whenTheLoginServerAnswersPlayFail() {
            server = new FakeLoginServer("load0", "secret", true);

            final Run run = run(server.address(), true);

            assertThat(run.events())
                    .containsExactly(
                            "state LOGIN_CONNECT",
                            "state LOGIN_AUTH",
                            "ack LOGIN",
                            "state SERVER_LIST",
                            "reject SELECT_SERVER",
                            "state STOPPED");
        }

        @Test
        void open_shouldFail_whenTheLoadBotGoesOnPastGameConnect() {
            server = new FakeLoginServer("load0", "secret", false);

            final Run run = run(server.address(), false);

            assertThat(run.events()).endsWith("ack SELECT_SERVER", "state GAME_CONNECT", "failed");
            assertThat(run.listener().failures())
                    .singleElement(InstanceOfAssertFactories.THROWABLE)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("no game part");
        }

        @Test
        void open_shouldFail_whenTheLoginServerRefusesTheConnection() throws IOException {
            final InetSocketAddress closed;
            try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                closed = new InetSocketAddress(InetAddress.getLoopbackAddress(), socket.getLocalPort());
            }

            final Run run = run(closed, true);

            assertThat(run.events()).containsExactly("failed");
            assertThat(run.listener().failures())
                    .singleElement(InstanceOfAssertFactories.THROWABLE)
                    .isInstanceOf(IOException.class);
        }

        @Test
        void send_shouldFail_whenTheIntentIsBeyondTheLoginExchange() {
            server = new FakeLoginServer("load0", "secret", false);

            final Run run = run(server.address(), true, new EnterWorld());

            assertThat(run.events()).containsExactly("state LOGIN_CONNECT", "state LOGIN_AUTH", "failed");
            assertThat(run.listener().failures())
                    .singleElement(InstanceOfAssertFactories.THROWABLE)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot express ENTER_WORLD");
        }
    }

    private P267Protocol protocol(InetSocketAddress login) {
        return new P267Protocol(P267Settings.builder()
                .login(login)
                .serverId(LoginServerSide.SERVER_ID)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    private Run run(InetSocketAddress login, boolean closeAtGameConnect) {
        return run(login, closeAtGameConnect, null);
    }

    private Run run(InetSocketAddress login, boolean closeAtGameConnect, Intent atLoginAuth) {
        final P267Protocol protocol = protocol(login);
        group = new MultiThreadIoEventLoopGroup(1, protocol.ioHandlerFactory());
        final EventLoop loop = group.next();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final ClientSession[] session = new ClientSession[1];
        final boolean[] opening = new boolean[1];
        final RecordingListener listener = new RecordingListener();
        listener.reacting(event -> {
            if (opening[0]) {
                done.completeExceptionally(new AssertionError("The listener was called inside open: " + event));
            }
            switch (event) {
                case "state LOGIN_AUTH" ->
                    session[0].send(
                            atLoginAuth != null
                                    ? atLoginAuth
                                    : Login.builder()
                                            .account(IDENTITY.account())
                                            .password(IDENTITY.password())
                                            .build());
                case "state SERVER_LIST" -> session[0].send(new SelectServer());
                case "state GAME_CONNECT" -> {
                    if (closeAtGameConnect) {
                        session[0].close();
                        done.complete(null);
                    }
                }
                case "state STOPPED", "failed" -> {
                    session[0].close();
                    done.complete(null);
                }
                default -> {}
            }
        });
        loop.execute(() -> {
            opening[0] = true;
            session[0] = protocol.open(IDENTITY, loop, listener);
            opening[0] = false;
        });
        done.orTimeout(WAIT.toMillis(), TimeUnit.MILLISECONDS).join();
        // Callbacks after the terminal one would land here; one more loop turn lets them show.
        loop.submit(() -> {}).syncUninterruptibly();
        return new Run(listener, List.copyOf(listener.events()));
    }

    private record Run(RecordingListener listener, List<String> events) {}
}
