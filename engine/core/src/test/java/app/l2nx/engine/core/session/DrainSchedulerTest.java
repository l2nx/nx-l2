/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopGroup;
import app.l2nx.engine.core.loop.ThreadFailureHandler;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.network.metrics.NetworkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DrainSchedulerTest {
    record Step(int value) implements Command {}

    private static final ThreadFailureHandler UNEXPECTED = (thread, failure) -> {
        throw new AssertionError("Unexpected death of " + thread.getName(), failure);
    };
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private final LoopGroup group = new LoopGroup("test-loop", registry, EngineProperties.defaults(), UNEXPECTED);
    private final Loop world =
            new Loop("world", new SystemGameClock(Clock.systemUTC()), 42L, EngineProperties.defaults(), registry);
    private final SessionMetrics metrics = SessionMetrics.noop();
    private final SessionFactory factory = new SessionFactory(
            Session.Limits.builder()
                    .queueLimit(16)
                    .perDrain(16)
                    .perSecond(200)
                    .burst(16)
                    .failureLimit(10)
                    .failureWindow(Duration.ofSeconds(60))
                    .build(),
            DrainScheduler.pool((drain, abandoned) -> drain.run(), metrics.drainFailures()),
            DrainScheduler.loop(() -> world),
            new CommandDispatcher(
                    new CommandRegistry(List.of(new SessionCommandHandler<Step>() {
                        @Override
                        public void handle(Session session, Step command) {
                            log.add("step " + command.value() + " on "
                                    + Thread.currentThread().getName());
                        }
                    })),
                    address -> Optional.empty(),
                    NetworkMetrics.noop()),
            metrics);
    private final Session session = factory.create(new FakeTransport());

    DrainSchedulerTest() {
        group.pin(world);
        session.setState(SessionState.AUTHED);
    }

    @AfterEach
    void stopTheLoop() {
        group.shutdown(STOP_TIMEOUT);
    }

    @Nested
    class Schedule {
        @Test
        void schedule_shouldRunTheItemOnTheLoopThread_whenAuthed() throws Exception {
            final CompletableFuture<Void> ran = new CompletableFuture<>();
            session.offer(session.input(new Step(1)));
            session.offer(session.input((Runnable) () -> ran.complete(null)));

            ran.get(5, TimeUnit.SECONDS);

            assertThat(log).containsExactly("step 1 on test-loop-world");
        }

        @Test
        void schedule_shouldClearTheSignal_whenTheLoopHasTerminated() {
            group.shutdown(STOP_TIMEOUT);

            assertThat(session.offer(session.input(new Step(1)))).isTrue();

            assertThat(session.trySignal()).as("the refused signal was cleared").isTrue();
            assertThat(session.queued()).isEqualTo(1);
        }
    }

    @Nested
    class ScheduleClose {
        @Test
        void close_shouldRunInADrainPass_whenTheLoopStopsItsInput() throws Exception {
            final AtomicReference<String> closedOn = new AtomicReference<>();
            session.onClosed(() -> closedOn.set(Thread.currentThread().getName()));
            final CompletableFuture<Void> hooked = new CompletableFuture<>();
            world.offer(loop -> {
                // The hook runs after the loop stopped taking input: a signalled session is no longer drained.
                loop.addShutdownHook(stopping -> {
                    session.offer(session.input(new Step(1)));
                    session.close();
                });
                hooked.complete(null);
            });
            hooked.get(5, TimeUnit.SECONDS);

            assertThat(group.shutdown(STOP_TIMEOUT)).isEmpty();

            assertThat(closedOn.get()).isEqualTo("test-loop-world");
            assertThat(log).isEmpty();
            assertThat(session.state()).isEqualTo(SessionState.DISCONNECTED);
            assertThat(factory.awaitClosed(Duration.ZERO)).isTrue();
        }

        @Test
        void close_shouldRunOnTheClosingThread_onceTheLoopHasTerminated() {
            final AtomicReference<Thread> closedOn = new AtomicReference<>();
            session.onClosed(() -> closedOn.set(Thread.currentThread()));
            group.shutdown(STOP_TIMEOUT);

            session.close();

            assertThat(closedOn.get()).isSameAs(Thread.currentThread());
            assertThat(session.state()).isEqualTo(SessionState.DISCONNECTED);
            assertThat(factory.awaitClosed(Duration.ofSeconds(1))).isTrue();
        }
    }
}
