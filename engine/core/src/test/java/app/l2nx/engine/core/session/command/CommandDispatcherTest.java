/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.session.DrainScheduler;
import app.l2nx.engine.core.session.FakeTransport;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.engine.core.session.SessionMetrics;
import app.l2nx.network.metrics.NetworkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandDispatcherTest {
    record Ping(int value) implements Command {}

    record Kick(SessionAddress address) implements SessionAddressed {}

    record Report(int value) implements Command {}

    private static final SessionAddress ACCOUNT =
            new SessionAddress.Account(UUID.fromString("0190a6a2-7c00-7000-8000-000000000001"));
    private static final SessionAddress REQUEST =
            new SessionAddress.Request(UUID.fromString("0190a6a2-7c00-7000-8000-000000000002"));

    private final List<String> log = new CopyOnWriteArrayList<>();
    private final List<Session> handledOn = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> drains = new ArrayDeque<>();
    private final FakeTransport transport = new FakeTransport();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Map<SessionAddress, Session> online = new ConcurrentHashMap<>();
    private final CommandDispatcher dispatcher = new CommandDispatcher(
            new CommandRegistry(List.<CommandHandler<?>>of(pings(), kicks(), reports())),
            address -> Optional.ofNullable(online.get(address)),
            new NetworkMetrics(registry));

    @Nested
    class Dispatch {
        @Test
        void dispatch_shouldRunASessionCommandWithItsSession() {
            final Session session = session();

            dispatcher.dispatch(session, new Ping(1));

            assertThat(log).containsExactly("ping 1");
            assertThat(handledOn).containsExactly(session);
        }

        @Test
        void dispatch_shouldFail_whenAServiceCommandReachesASession() {
            final Session session = session();

            assertThatThrownBy(() -> dispatcher.dispatch(session, new Report(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Report.class.getName());
        }
    }

    @Nested
    class Submit {
        @Test
        void submit_shouldRunAServiceCommandOnTheCallersThread() {
            dispatcher.submit(new Report(2));

            assertThat(log)
                    .containsExactly("report 2 on " + Thread.currentThread().getName());
        }

        @Test
        void submit_shouldOfferAnAddressedCommandToTheSessionOfItsAccount_onItsEventLoop() {
            final Session session = session();
            online.put(ACCOUNT, session);

            dispatcher.submit(new Kick(ACCOUNT));
            assertThat(session.queued()).as("not offered before the hop").isZero();
            transport.runTasks();
            runDrains();

            assertThat(log).containsExactly("kick " + ACCOUNT);
            assertThat(handledOn).containsExactly(session);
        }

        @Test
        void submit_shouldOfferTheAnswerToARequest_toTheSessionThatSentIt() {
            final Session requester = session();
            online.put(ACCOUNT, session());
            online.put(REQUEST, requester);

            dispatcher.submit(new Kick(REQUEST));
            transport.runTasks();
            runDrains();

            assertThat(log).containsExactly("kick " + REQUEST);
            assertThat(handledOn).containsExactly(requester);
        }

        @Test
        void submit_shouldCountTheCommandAsDropped_whenTheAddressHasNoSession() {
            dispatcher.submit(new Kick(ACCOUNT));

            assertThat(handledOn).isEmpty();
            assertThat(registry.get("l2nx.network.dropped")
                            .tag("reason", "no-session")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }

        @Test
        void submit_shouldFail_whenASessionCommandIsNotAddressed() {
            assertThatThrownBy(() -> dispatcher.submit(new Ping(3)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Ping.class.getName());
        }

        @Test
        void submit_shouldFail_whenAnAddressedCommandCarriesNoAddress() {
            assertThatThrownBy(() -> dispatcher.submit(new Kick(null))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void submit_shouldHandTheCommandToTheHandlerWithoutASession_whenTheAddressHasNoSession() {
            dispatcher.submit(new Kick(ACCOUNT));

            assertThat(log).containsExactly("kick without session " + ACCOUNT);
        }

        @Test
        void submit_shouldNotRunTheNoSessionHook_whenTheAddressHasASession() {
            online.put(ACCOUNT, session());

            dispatcher.submit(new Kick(ACCOUNT));
            transport.runTasks();
            runDrains();

            assertThat(log).containsExactly("kick " + ACCOUNT);
        }
    }

    private Session session() {
        return new SessionFactory(
                        Session.Limits.builder()
                                .queueLimit(16)
                                .perDrain(16)
                                .perSecond(200)
                                .burst(16)
                                .failureLimit(10)
                                .failureWindow(Duration.ofSeconds(60))
                                .build(),
                        DrainScheduler.pool(
                                (drain, abandoned) -> drains.add(drain),
                                SessionMetrics.noop().drainFailures()),
                        dispatcher,
                        SessionMetrics.noop())
                .create(transport);
    }

    private void runDrains() {
        Runnable drain;
        while ((drain = drains.poll()) != null) {
            drain.run();
        }
    }

    private SessionCommandHandler<Ping> pings() {
        return new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Ping command) {
                handledOn.add(session);
                log.add("ping " + command.value());
            }
        };
    }

    private SessionCommandHandler<Kick> kicks() {
        return new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Kick command) {
                handledOn.add(session);
                log.add("kick " + command.address());
            }

            @Override
            public void handleWithoutSession(Kick command) {
                log.add("kick without session " + command.address());
            }
        };
    }

    private ServiceCommandHandler<Report> reports() {
        return new ServiceCommandHandler<>() {
            @Override
            public void handle(Report command) {
                log.add("report " + command.value() + " on "
                        + Thread.currentThread().getName());
            }
        };
    }
}
