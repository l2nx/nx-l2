/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.loop.Message;
import app.l2nx.engine.core.session.command.CoalescableCommand;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandHandler;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SessionTest {
    record Step(int value) implements Command {}

    private final class Bound implements Runnable, ContextBound {
        private final AtomicBoolean present;
        private final String name;

        Bound(AtomicBoolean present, String name) {
            this.present = present;
            this.name = name;
        }

        @Override
        public boolean hasContext() {
            return present.get();
        }

        @Override
        public String requiredContext() {
            return "CHARACTER";
        }

        @Override
        public void run() {
            log.add(name);
        }
    }

    record Move(int value) implements CoalescableCommand {}

    private final List<String> log = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> drains = new ArrayDeque<>();
    private final AtomicLong nanos = new AtomicLong();
    private final FakeTransport transport = new FakeTransport();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SessionMetrics metrics = new SessionMetrics(registry);
    private final TimeMeter time = new TimeMeter() {
        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    };

    @Nested
    class Order {
        @Test
        void drain_shouldRunCommandsAndLegacyPacketsInArrivalOrder() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Step(1)));
            session.offer(session.input((Runnable) () -> log.add("legacy")));
            session.offer(session.input(new Step(2)));
            runDrains();

            assertThat(log).containsExactly("step 1", "legacy", "step 2");
        }

        @Test
        void drain_shouldRunAtMostPacketsPerDrainAndReschedule() {
            final Session session = session(limits(256, 2, 200, 256));
            for (int i = 0; i < 5; i++) {
                session.offer(session.input(new Step(i)));
            }

            drains.poll().run();
            assertThat(log).hasSize(2);
            assertThat(drains).as("rescheduled while input remains").hasSize(1);

            runDrains();
            assertThat(log).containsExactly("step 0", "step 1", "step 2", "step 3", "step 4");
        }

        @Test
        void drain_shouldNeverRunTwoItemsOfOneSessionAtOnce() throws Exception {
            final AtomicInteger running = new AtomicInteger();
            final AtomicInteger overlaps = new AtomicInteger();
            final CountDownLatch done = new CountDownLatch(2_000);
            final SessionCommandHandler<Step> slow = steps(command -> {
                if (running.incrementAndGet() > 1) {
                    overlaps.incrementAndGet();
                }
                Thread.onSpinWait();
                running.decrementAndGet();
                done.countDown();
            });
            try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
                final Session session = new Session(
                        limits(4_096, 16, 1_000_000, 4_096),
                        DrainScheduler.pool((drain, abandoned) -> pool.execute(drain), metrics.drainFailures()),
                        dispatcher(List.of(slow)),
                        metrics,
                        transport,
                        TimeMeter.SYSTEM_NANOTIME,
                        () -> {});
                for (int i = 0; i < 2_000; i++) {
                    while (!session.offer(session.input(new Step(i)))) {
                        Thread.onSpinWait();
                    }
                }
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(overlaps.get()).isZero();
        }
    }

    @Nested
    class Flood {
        @Test
        void offer_shouldRefuse_whenQueueIsFull_whileTheBucketHasTokens() {
            final Session session = session(limits(2, 16, 1_000, 1_000));

            assertThat(session.offer(session.input(new Step(1)))).isTrue();
            assertThat(session.offer(session.input(new Step(2)))).isTrue();
            assertThat(session.offer(session.input(new Step(3)))).isFalse();

            runDrains();
            assertThat(session.offer(session.input(new Step(4))))
                    .as("room again, no refill needed")
                    .isTrue();
        }

        @Test
        void offer_shouldRefuse_whenTheBucketIsEmpty_whileTheQueueHasRoom() {
            final Session session = session(limits(64, 16, 2, 2));
            assertThat(session.offer(session.input(new Step(1)))).isTrue();
            assertThat(session.offer(session.input(new Step(2)))).isTrue();
            runDrains();

            assertThat(session.queued()).isZero();
            assertThat(session.offer(session.input(new Step(3)))).isFalse();
            nanos.addAndGet(Duration.ofSeconds(1).toNanos());
            assertThat(session.offer(session.input(new Step(4)))).isTrue();
        }

        @Test
        void offerInternal_shouldBypassAnEmptyBucket_andHopToTheTransport() {
            final Session session = session(limits(64, 16, 1, 1));
            session.offer(session.input(new Step(1)));
            runDrains();
            assertThat(session.offer(session.input(new Step(9))))
                    .as("bucket empty, queue empty")
                    .isFalse();

            session.offerInternal(new Step(2));
            assertThat(session.queued()).as("offered on the event loop only").isZero();
            transport.runTasks();
            runDrains();

            assertThat(log).containsExactly("step 1", "step 2");
        }

        @Test
        void offerInternal_shouldCloseWithFlood_whenTheQueueIsFull() {
            final Session session = session(limits(2, 16, 200, 2));
            session.offer(session.input(new Step(1)));
            session.offer(session.input(new Step(2)));

            session.offerInternal(new Step(3));
            transport.runTasks();

            assertThat(transport.closes).containsExactly(CloseReason.FLOOD);
        }

        @Test
        void offer_shouldRateLimit_atTheHighestPacketsPerSecondTheSettingsAllow() {
            final Session session = session(Session.Limits.of(NetworkProperties.defaults().toBuilder()
                    .packetsPerSecond(1_000_000_000)
                    .build()));

            assertThat(session.offer(session.input(new Step(1)))).isTrue();
        }

        @Test
        void input_shouldRefuseAnItemOfUnknownKind() {
            final Session session = session(limits(64, 16, 200, 64));

            assertThatThrownBy(() -> session.input("text")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void offer_shouldRefuseAnInputOfAnotherSession() {
            final Session session = session(limits(64, 16, 200, 64));
            final Session other = session(limits(64, 16, 200, 64));

            assertThatThrownBy(() -> session.offer(other.input(new Step(1))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(session.queued()).isZero();
        }
    }

    @Nested
    class Coalescing {
        @Test
        void drain_shouldRunOnlyTheLatestOfARunOfCoalescableCommands() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Move(1)));
            session.offer(session.input(new Move(2)));
            session.offer(session.input(new Step(3)));
            session.offer(session.input(new Move(4)));
            runDrains();

            assertThat(log).containsExactly("move 2", "step 3", "move 4");
        }
    }

    @Nested
    class Context {
        @Test
        void drain_shouldJudgeAnItemsContext_afterTheItemQueuedBeforeItRan() {
            final Session session = session(limits(256, 16, 200, 256));
            final AtomicBoolean selected = new AtomicBoolean();

            session.offer(session.input((Runnable) () -> {
                selected.set(true);
                log.add("select");
            }));
            session.offer(session.input(new Bound(selected, "enter")));
            runDrains();

            assertThat(log).containsExactly("select", "enter");
            assertThat(rejected()).isZero();
        }

        @Test
        void drain_shouldRejectAndCountAnItem_whoseContextIsMissing() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Bound(new AtomicBoolean(), "enter")));
            session.offer(session.input(new Step(1)));
            runDrains();

            assertThat(log).containsExactly("step 1");
            assertThat(registry.get(SessionMetrics.REJECTED)
                            .tag("type", Bound.class.getName())
                            .tag("context", "CHARACTER")
                            .counter()
                            .count())
                    .isEqualTo(1);
        }

        @Test
        void execute_shouldDiscardWithoutCounting_anItemThatRacedTheClose() {
            final Session session = session(limits(256, 16, 200, 256));
            session.offer(session.input(new Bound(new AtomicBoolean(), "enter")));
            final Message taken = session.poll();

            session.close();
            taken.execute(null);

            assertThat(log).isEmpty();
            assertThat(rejected()).isZero();
        }

        private double rejected() {
            return registry.find(SessionMetrics.REJECTED).counters().stream()
                    .mapToDouble(Counter::count)
                    .sum();
        }
    }

    @Nested
    class Lifecycle {
        @Test
        void setState_shouldNotifyTransport_andRefuseIllegalMoves() {
            final Session session = session(limits(256, 16, 200, 256));

            session.setState(SessionState.AUTHED);

            assertThat(transport.changes).containsExactly("CONNECTED->AUTHED");
            assertThatThrownBy(() -> session.setState(SessionState.CONNECTED))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> session.setState(SessionState.CLOSING))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void close_shouldNotifyClosingAndDisconnected() {
            final Session session = session(limits(256, 16, 200, 256));
            session.setState(SessionState.AUTHED);

            session.close();
            runDrains();

            assertThat(transport.changes)
                    .containsExactly("CONNECTED->AUTHED", "AUTHED->CLOSING", "CLOSING->DISCONNECTED");
        }

        @Test
        void setState_shouldBeASilentNoOp_onceClosing() {
            final Session session = session(limits(256, 16, 200, 256));
            session.setState(SessionState.AUTHED);
            session.close();

            session.setState(SessionState.AUTHED);

            assertThat(session.state()).isEqualTo(SessionState.CLOSING);
            assertThat(transport.changes).containsExactly("CONNECTED->AUTHED", "AUTHED->CLOSING");
        }

        @Test
        void close_shouldDiscardQueuedInputAndRunOnClosedOnce() {
            final Session session = session(limits(256, 16, 200, 256));
            final AtomicInteger closed = new AtomicInteger();
            session.onClosed(closed::incrementAndGet);
            session.offer(session.input(new Step(1)));

            session.close();
            session.close();
            runDrains();

            assertThat(log).isEmpty();
            assertThat(closed.get()).isEqualTo(1);
            assertThat(session.state()).isEqualTo(SessionState.DISCONNECTED);
        }

        @Test
        void offer_shouldAcceptAndDropInput_afterTheClose() {
            final Session session = session(limits(256, 16, 200, 256));
            session.close();
            runDrains();

            assertThat(session.offer(session.input(new Step(2)))).isTrue();
            assertThat(session.queued()).isZero();
            assertThat(session.hasInput()).isFalse();
            assertThat(drains).isEmpty();
        }
    }

    @Nested
    class AsInputSource {
        @Test
        void poll_shouldHandOutItemsInOrderWithoutRunningThem() {
            final Session session = session(limits(256, 16, 200, 256));
            session.offer(session.input(new Step(1)));
            session.offer(session.input(new Step(2)));
            drains.clear();

            final Message first = session.poll();
            final Message second = session.poll();

            assertThat(session.poll()).isNull();
            assertThat(log).isEmpty();
            first.execute(null);
            second.execute(null);
            assertThat(log).containsExactly("step 1", "step 2");
        }

        @Test
        void trySignal_shouldSucceedOnlyFromIdle() {
            final Session session = session(limits(256, 16, 200, 256));
            session.offer(session.input(new Step(1)));

            assertThat(session.trySignal()).as("the offer signalled already").isFalse();
            assertThat(session.hasInput()).isTrue();
            runDrains();
            assertThat(session.hasInput()).isFalse();
            assertThat(session.trySignal()).as("the drain cleared the signal").isTrue();
            session.clearSignal();
        }

        @Test
        void id_shouldBeStableAndDistinct() {
            final Session first = session(limits(256, 16, 200, 256));
            final Session second = session(limits(256, 16, 200, 256));

            assertThat(first.id()).isEqualTo(first.id()).isNotEqualTo(second.id());
        }
    }

    @Nested
    class DrainAbandoned {
        @Test
        void drainAbandoned_shouldClearTheSignal_andWarnNamingTheSession_whenThePoolClosesBeforeTheDrainStarts() {
            final Logger sessionLog = (Logger) LoggerFactory.getLogger(Session.class);
            final ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            sessionLog.addAppender(logs);
            try {
                final Session session = new Session(
                        limits(256, 16, 200, 256),
                        DrainScheduler.pool((drain, abandoned) -> abandoned.run(), metrics.drainFailures()),
                        dispatcher(List.of(steps(command -> log.add("step " + command.value())))),
                        metrics,
                        transport,
                        time,
                        () -> {});

                session.offer(session.input(new Step(1)));

                assertThat(session.trySignal()).as("the signal was cleared").isTrue();
                assertThat(log).isEmpty();
                assertThat(logs.list)
                        .filteredOn(event -> event.getLevel() == Level.WARN)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.startsWith("Session " + session.id() + " "));
            } finally {
                sessionLog.detachAppender(logs);
                logs.stop();
            }
        }
    }

    @Nested
    class Failures {
        @Test
        void drain_shouldReportAFailedItem_andRunTheRest() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Failing()));
            session.offer(session.input(new Step(2)));
            runDrains();

            assertThat(log).containsExactly("step 2");
            assertThat(registry.get("l2nx.engine.failures")
                            .tags("loop", "l2nx-game-packets", "phase", "inbox")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.legacy.packet")
                            .tags(
                                    "type",
                                    Failing.class.getName(),
                                    "outcome",
                                    "ERROR",
                                    "exception",
                                    "IllegalStateException")
                            .timer()
                            .count())
                    .isEqualTo(1);
            assertThat(transport.closes).isEmpty();
        }

        @Test
        void drain_shouldCloseTheConnection_whenFailuresExceedTheLimit() {
            final Session session = session(
                    limits(256, 16, 200, 256).toBuilder().failureLimit(2).build());

            session.offer(session.input(new Failing()));
            session.offer(session.input(new Failing()));
            runDrains();
            assertThat(transport.closes).isEmpty();
            session.offer(session.input(new Failing()));
            runDrains();

            assertThat(transport.closes).containsExactly(CloseReason.ERROR);
        }

        @Test
        void drain_shouldForgiveFailures_afterTheWindow() {
            final Session session = session(
                    limits(256, 16, 200, 256).toBuilder().failureLimit(2).build());
            session.offer(session.input(new Failing()));
            session.offer(session.input(new Failing()));
            runDrains();

            nanos.addAndGet(Duration.ofSeconds(60).toNanos());
            session.offer(session.input(new Failing()));
            session.offer(session.input(new Failing()));
            runDrains();

            assertThat(transport.closes).isEmpty();
        }
    }

    @Nested
    class Metrics {
        @Test
        void drain_shouldTimeCommandsAndLegacyPacketsByFullyQualifiedType() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Step(1)));
            session.offer(session.input(new LegacyProbe()));
            runDrains();

            assertThat(registry.get("l2nx.session.command")
                            .tags("type", Step.class.getName(), "outcome", "SUCCESS", "exception", "none")
                            .timer()
                            .count())
                    .isEqualTo(1);
            assertThat(registry.get("l2nx.legacy.packet")
                            .tags("type", LegacyProbe.class.getName(), "outcome", "SUCCESS", "exception", "none")
                            .timer()
                            .count())
                    .isEqualTo(1);
        }

        @Test
        void limits_shouldFollowTheNetworkProperties() {
            assertThat(Session.Limits.of(NetworkProperties.defaults()))
                    .isEqualTo(Session.Limits.builder()
                            .queueLimit(256)
                            .perDrain(16)
                            .perSecond(200)
                            .burst(256)
                            .failureLimit(10)
                            .failureWindow(Duration.ofSeconds(60))
                            .build());
        }
    }

    private static Session.Limits limits(int queueLimit, int perDrain, int perSecond, int burst) {
        return Session.Limits.builder()
                .queueLimit(queueLimit)
                .perDrain(perDrain)
                .perSecond(perSecond)
                .burst(burst)
                .failureLimit(10)
                .failureWindow(Duration.ofSeconds(60))
                .build();
    }

    private Session session(Session.Limits limits) {
        final SessionCommandHandler<Move> moves = new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Move command) {
                log.add("move " + command.value());
            }
        };
        return new Session(
                limits,
                DrainScheduler.pool((drain, abandoned) -> drains.add(drain), metrics.drainFailures()),
                dispatcher(List.of(steps(command -> log.add("step " + command.value())), moves)),
                metrics,
                transport,
                time,
                () -> {});
    }

    private static CommandDispatcher dispatcher(List<? extends CommandHandler<?>> handlers) {
        return new CommandDispatcher(new CommandRegistry(handlers), address -> Optional.empty(), NetworkMetrics.noop());
    }

    private void runDrains() {
        Runnable drain;
        while ((drain = drains.poll()) != null) {
            drain.run();
        }
    }

    private static SessionCommandHandler<Step> steps(Consumer<Step> body) {
        return new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Step command) {
                body.accept(command);
            }
        };
    }

    private static final class LegacyProbe implements Runnable {
        @Override
        public void run() {}
    }

    private static final class Failing implements Runnable {
        @Override
        public void run() {
            throw new IllegalStateException("boom");
        }
    }
}
