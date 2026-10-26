/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.metrics.NetworkMetrics;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SessionStressTest {

    private static final int TOTAL = 200_000;

    record Step(int value) implements Command {}

    private final AtomicInteger drained = new AtomicInteger();
    private final AtomicInteger outOfOrder = new AtomicInteger();
    private final SessionMetrics metrics = SessionMetrics.noop();
    private final CommandDispatcher dispatcher = new CommandDispatcher(
            new CommandRegistry(List.of(new SessionCommandHandler<Step>() {
                @Override
                public void handle(Session session, Step command) {
                    if (command.value() != drained.get()) {
                        outOfOrder.incrementAndGet();
                    }
                    drained.incrementAndGet();
                }
            })),
            address -> Optional.empty(),
            NetworkMetrics.noop());

    @Nested
    class OnThePool {
        @Test
        @Timeout(60)
        void drain_shouldLoseNoInputToARacingProducer() {
            final Session session;
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                session = session(
                        DrainScheduler.pool((drain, abandoned) -> pool.execute(drain), metrics.drainFailures()));
                produce(session);
                while (drained.get() < TOTAL) {
                    Thread.onSpinWait();
                }
            }

            assertThat(drained.get()).isEqualTo(TOTAL);
            assertThat(outOfOrder.get()).isZero();
            assertThat(session.hasInput()).isFalse();
            assertThat(session.trySignal())
                    .as("the last drain cleared the signal")
                    .isTrue();
        }
    }

    @Nested
    class OnTheLoop {
        private final ManualGameClock clock =
                new ManualGameClock(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
        private final Loop loop = new Loop("test", clock, 42L, EngineProperties.defaults(), new SimpleMeterRegistry());

        @Test
        @Timeout(60)
        void loop_shouldLoseNoInputToARacingProducer() throws Exception {
            final long now = clock.gameMillis();
            final Session session = session(source -> {
                if (!loop.signal(source)) {
                    source.clearSignal();
                }
            });
            final Thread producer = Thread.ofPlatform().name("stress-producer").start(() -> produce(session));

            while (drained.get() < TOTAL) {
                loop.runPass(now);
                Thread.onSpinWait();
            }
            producer.join();
            loop.runPass(now);

            assertThat(drained.get()).isEqualTo(TOTAL);
            assertThat(outOfOrder.get()).isZero();
            assertThat(session.hasInput()).isFalse();
            assertThat(session.trySignal()).as("the loop cleared the signal").isTrue();
        }
    }

    @Nested
    class Transitions {
        private static final int ROUNDS = 10_000;

        @Test
        @Timeout(60)
        void setState_shouldNotifyInTransitionOrder_whileACloseRaces() throws Exception {
            final List<OrderingTransport.Move> broken = new ArrayList<>();
            try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
                for (int round = 0; (round < ROUNDS) && broken.isEmpty(); round++) {
                    final OrderingTransport transport = new OrderingTransport();
                    final Queue<Runnable> drains = new ConcurrentLinkedQueue<>();
                    final Session session = session(
                            DrainScheduler.pool((drain, abandoned) -> drains.add(drain), metrics.drainFailures()),
                            transport);
                    final CountDownLatch start = new CountDownLatch(1);
                    final Future<?> mover = threads.submit(() -> {
                        start.await();
                        session.setState(SessionState.AUTHED);
                        return null;
                    });
                    final Future<?> closer = threads.submit(() -> {
                        start.await();
                        session.close();
                        return null;
                    });

                    start.countDown();
                    mover.get();
                    closer.get();
                    Runnable drain;
                    while ((drain = drains.poll()) != null) {
                        drain.run();
                    }

                    if (!transport.inOrder()) {
                        broken.addAll(transport.moves);
                    }
                }
            }

            assertThat(broken)
                    .as("each notification starts where the previous one ended")
                    .isEmpty();
        }
    }

    // Yields before it records, so a notification that lags behind its transition is overtaken by the next one.
    private static final class OrderingTransport implements SessionTransport {
        private final List<Move> moves = new CopyOnWriteArrayList<>();

        record Move(SessionState from, SessionState to) {}

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public void stateChanged(SessionState from, SessionState to) {
            Thread.yield();
            moves.add(new Move(from, to));
        }

        @Override
        public void close(CloseReason reason) {}

        boolean inOrder() {
            SessionState expected = SessionState.CONNECTED;
            for (Move move : moves) {
                if (move.from() != expected) {
                    return false;
                }
                expected = move.to();
            }
            return expected == SessionState.DISCONNECTED;
        }
    }

    private Session session(DrainScheduler scheduler) {
        return session(scheduler, new FakeTransport());
    }

    private Session session(DrainScheduler scheduler, SessionTransport transport) {
        return new Session(
                Session.Limits.builder()
                        .queueLimit(1_024)
                        .perDrain(16)
                        .perSecond(1_000_000)
                        .burst(Integer.MAX_VALUE)
                        .failureLimit(10)
                        .failureWindow(Duration.ofSeconds(60))
                        .build(),
                scheduler,
                scheduler,
                dispatcher,
                metrics,
                transport,
                TimeMeter.SYSTEM_NANOTIME,
                () -> {});
    }

    // Single producer, as the IO thread; a full queue is retried, not dropped.
    private static void produce(Session session) {
        for (int i = 0; i < TOTAL; i++) {
            while (!session.offer(session.input(new Step(i)))) {
                Thread.onSpinWait();
            }
        }
    }
}
