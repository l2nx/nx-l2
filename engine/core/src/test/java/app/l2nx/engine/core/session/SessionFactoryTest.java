/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.network.metrics.NetworkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionFactoryTest {
    private final ExecutorService drains = Executors.newVirtualThreadPerTaskExecutor();
    private final SessionMetrics metrics = SessionMetrics.noop();
    private final DrainScheduler scheduler =
            DrainScheduler.pool((drain, abandoned) -> drains.execute(drain), metrics.drainFailures());
    private final SessionFactory factory = new SessionFactory(
            Session.Limits.builder()
                    .queueLimit(16)
                    .perDrain(16)
                    .perSecond(200)
                    .burst(16)
                    .failureLimit(10)
                    .failureWindow(Duration.ofSeconds(60))
                    .build(),
            scheduler,
            scheduler,
            new CommandDispatcher(
                    new CommandRegistry(List.of()),
                    address -> Optional.empty(),
                    new NetworkMetrics(new SimpleMeterRegistry())),
            metrics);

    @AfterEach
    void stopDrains() {
        drains.close();
    }

    @Nested
    class AwaitClosed {
        @Test
        void awaitClosed_shouldReturnTrue_whenNoSessionWasCreated() {
            assertThat(factory.awaitClosed(Duration.ZERO)).isTrue();
        }

        @Test
        void awaitClosed_shouldReturnTrue_whenSessionsCloseOnOtherThreads() {
            final AtomicInteger callbacks = new AtomicInteger();
            final List<Session> sessions = IntStream.range(0, 8)
                    .mapToObj(i -> factory.create(new FakeTransport()))
                    .toList();
            sessions.forEach(session -> session.onClosed(callbacks::incrementAndGet));
            sessions.forEach(session -> Thread.ofVirtual().start(session::close));

            assertThat(factory.awaitClosed(Duration.ofSeconds(5))).isTrue();
            assertThat(callbacks.get()).isEqualTo(8);
            assertThat(sessions)
                    .allSatisfy(session -> assertThat(session.state()).isEqualTo(SessionState.DISCONNECTED));
        }

        @Test
        void awaitClosed_shouldReturnFalse_whenASessionIsStillOpen() {
            final Session closing = factory.create(new FakeTransport());
            factory.create(new FakeTransport());
            closing.close();

            assertThat(factory.awaitClosed(Duration.ofMillis(100))).isFalse();
        }

        @Test
        void awaitClosed_shouldWaitForTheClosedCallback_whenItIsStillRunning() throws Exception {
            final Session session = factory.create(new FakeTransport());
            final CountDownLatch entered = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            session.onClosed(() -> {
                entered.countDown();
                await(release);
            });
            session.close();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(factory.awaitClosed(Duration.ofMillis(100))).isFalse();

            release.countDown();
            assertThat(factory.awaitClosed(Duration.ofSeconds(5))).isTrue();
        }

        @Test
        void awaitClosed_shouldCountTheSessionClosed_whenItsClosedCallbackFails() {
            final Session session = factory.create(new FakeTransport());
            session.onClosed(() -> {
                throw new IllegalStateException("thrown by the test");
            });

            session.close();

            assertThat(factory.awaitClosed(Duration.ofSeconds(5))).isTrue();
        }

        @Test
        void awaitClosed_shouldReturnFalseAndKeepTheInterrupt_whenTheWaitingThreadIsInterrupted() throws Exception {
            factory.create(new FakeTransport());
            final AtomicBoolean result = new AtomicBoolean(true);
            final AtomicBoolean interrupted = new AtomicBoolean();
            final Thread waiting = Thread.ofVirtual().start(() -> {
                result.set(factory.awaitClosed(Duration.ofSeconds(30)));
                interrupted.set(Thread.currentThread().isInterrupted());
            });

            waiting.interrupt();
            waiting.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(waiting.isAlive()).isFalse();
            assertThat(result.get()).isFalse();
            assertThat(interrupted.get()).isTrue();
        }
    }

    @Nested
    class OpenSessions {
        @Test
        void openSessions_shouldCountEachSession_untilItsCloseIsHandled() {
            final List<Session> sessions =
                    List.of(factory.create(new FakeTransport()), factory.create(new FakeTransport()));
            assertThat(factory.openSessions()).isEqualTo(2);

            sessions.forEach(Session::close);

            assertThat(factory.awaitClosed(Duration.ofSeconds(5))).isTrue();
            assertThat(factory.openSessions()).isZero();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
