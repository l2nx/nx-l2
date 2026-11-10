/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.Message;
import app.l2nx.engine.core.session.command.CoalescableCommand;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandHandler;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
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
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SessionTest {
    record Step(int value) implements Command {}

    record Enter(int value) implements Command {}

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
    private final AtomicReference<String> hero = new AtomicReference<>();
    private final Queue<Runnable> drains = new ArrayDeque<>();
    private final AtomicLong nanos = new AtomicLong();
    private final FakeTransport transport = new FakeTransport();
    private final MockClock meterClock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, meterClock);
    private final SessionMetrics metrics = new SessionMetrics(registry, 256);
    private final DrainScheduler pool =
            DrainScheduler.pool((drain, abandoned) -> drains.add(drain), metrics.drainFailures());
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
                final DrainScheduler scheduler =
                        DrainScheduler.pool((drain, abandoned) -> pool.execute(drain), metrics.drainFailures());
                final Session session = new Session(
                        limits(4_096, 16, 1_000_000, 4_096),
                        scheduler,
                        scheduler,
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
        void offer_shouldRefuse_whenQueueIsFullAndTheBucketHasTokens() {
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
        void offer_shouldRefuse_whenTheBucketIsEmptyAndTheQueueHasRoom() {
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
        void offerInternal_shouldBypassAnEmptyBucketAndHopToTheTransport() {
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
        void offer_shouldQueuePastTheQueueLimit_whileHeld() {
            final Session session = session(limits(2, 16, 1_000, 1_000));
            session.hold();

            assertThat(session.offer(session.input(new Step(1)))).isTrue();
            assertThat(session.offer(session.input(new Step(2)))).isTrue();
            assertThat(session.offer(session.input(new Step(3)))).isTrue();
            session.offerInternal(new Step(4));
            transport.runTasks();

            assertThat(session.queued()).isEqualTo(4);
            assertThat(transport.closes).isEmpty();
        }

        @Test
        void offer_shouldStillRateLimit_whileHeld() {
            final Session session = session(limits(2, 16, 1, 3));
            session.hold();
            session.offer(session.input(new Step(1)));
            session.offer(session.input(new Step(2)));
            session.offer(session.input(new Step(3)));

            assertThat(session.offer(session.input(new Step(4)))).isFalse();
        }

        @Test
        void release_shouldTakeTheQueueLimitOnTopOfTheBacklog_untilTheBacklogDrains() {
            final Session session = session(limits(2, 16, 1_000, 1_000));
            session.hold();
            for (int i = 1; i <= 3; i++) {
                session.offer(session.input(new Step(i)));
            }

            session.release();
            assertThat(session.offer(session.input(new Step(4)))).isTrue();
            assertThat(session.offer(session.input(new Step(5)))).isTrue();
            assertThat(session.offer(session.input(new Step(6))))
                    .as("the backlog of 3 plus the limit of 2")
                    .isFalse();
            runDrains();

            assertThat(log).containsExactly("step 1", "step 2", "step 3", "step 4", "step 5");
            assertThat(session.offer(session.input(new Step(7)))).isTrue();
            assertThat(session.offer(session.input(new Step(8)))).isTrue();
            assertThat(session.offer(session.input(new Step(9))))
                    .as("drained: the queue limit again")
                    .isFalse();
        }

        @Test
        void offer_shouldRateLimit_whenSettingsAllowTheHighestPacketsPerSecond() {
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
        void drain_shouldJudgeAnItemsContext_whenTheItemQueuedBeforeItRan() {
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
        void drain_shouldRejectAndCountAnItem_whenItsContextIsMissing() {
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
        void drain_shouldResolveACommandsContext_whenTheItemQueuedBeforeItRan() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input((Runnable) () -> hero.set("hero")));
            session.offer(session.input(new Enter(1)));
            runDrains();

            assertThat(log).containsExactly("enter 1 as hero");
            assertThat(rejected()).isZero();
        }

        @Test
        void drain_shouldRejectAndCountACommand_whenItsContextIsMissing() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Enter(1)));
            session.offer(session.input(new Step(2)));
            runDrains();

            assertThat(log).containsExactly("step 2");
            assertThat(registry.get(SessionMetrics.REJECTED)
                            .tag("type", Enter.class.getName())
                            .tag("context", "CHARACTER")
                            .counter()
                            .count())
                    .isEqualTo(1);
            assertThat(registry.find(SessionMetrics.COMMAND)
                            .tag("type", Enter.class.getName())
                            .timer())
                    .as("a rejected command never ran")
                    .isNull();
        }

        @Test
        void execute_shouldDiscardWithoutCountingAnItem_whenItRacedTheClose() {
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
        void setState_shouldNotifyTransportAndRefuseIllegalMoves() {
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
        void setState_shouldBeASilentNoOp_whenClosing() {
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
        void offer_shouldAcceptAndDropInput_whenTheSessionIsClosed() {
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
    class AuthedDrain {
        private final ManualGameClock clock =
                new ManualGameClock(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
        private final Loop world =
                new Loop("world", clock, 42L, EngineProperties.defaults(), new SimpleMeterRegistry());
        private final Session session = session(limits(256, 16, 200, 256), DrainScheduler.loop(() -> world));

        @Test
        void setState_shouldSendTheNextSignalToTheLoop_whenAuthed() {
            final AtomicReference<Loop> ranIn = new AtomicReference<>();
            session.offer(session.input(new Step(1)));
            runDrains();

            session.setState(SessionState.AUTHED);
            session.offer(session.input((Runnable) () -> ranIn.set(Loop.current())));

            assertThat(drains).as("no drain on the pool").isEmpty();
            assertThat(ranIn.get()).isNull();
            runPass();
            assertThat(log).containsExactly("step 1");
            assertThat(ranIn.get()).isSameAs(world);
        }

        @Test
        void drain_shouldHandTheRestOfTheQueueToTheLoop_whenAnItemAuthenticates() {
            session.offer(session.input((Runnable) () -> session.setState(SessionState.AUTHED)));
            session.offer(session.input(new Step(2)));

            runDrains();

            assertThat(log).as("nothing after the switch runs on the pool").isEmpty();
            runPass();
            assertThat(log).containsExactly("step 2");
            assertThat(session.hasInput()).isFalse();
            assertThat(session.trySignal()).as("the pass cleared the signal").isTrue();
            session.clearSignal();
        }

        @Test
        void pass_shouldRunAtMostPacketsPerPass_whenTheSessionIsAuthed() {
            session.setState(SessionState.AUTHED);
            for (int i = 1; i <= 20; i++) {
                session.offer(session.input(new Step(i)));
            }

            runPass();
            assertThat(log).hasSize(EngineProperties.defaults().packetsPerPass());
            runPass();
            assertThat(log).hasSize(20);
        }

        @Test
        void close_shouldRunOnClosedInAPass_whenAuthed() {
            final AtomicReference<Loop> closedIn = new AtomicReference<>();
            session.onClosed(() -> closedIn.set(Loop.current()));
            session.setState(SessionState.AUTHED);
            session.offer(session.input(new Step(1)));

            session.close();

            assertThat(session.state()).isEqualTo(SessionState.CLOSING);
            runPass();
            assertThat(closedIn.get()).isSameAs(world);
            assertThat(log).as("queued input is discarded").isEmpty();
            assertThat(session.state()).isEqualTo(SessionState.DISCONNECTED);
            assertThat(session.hasInput()).isFalse();
        }

        @Test
        void hold_shouldKeepTheInputBehindItQueued_untilRelease() {
            session.setState(SessionState.AUTHED);
            session.offer(session.input((Runnable) () -> {
                log.add("select");
                session.hold();
            }));
            session.offer(session.input((Runnable) () -> log.add("enter")));

            runPass();
            runPass();
            assertThat(log).as("held across passes").containsExactly("select");

            world.execute(session::release);
            runPass();
            runPass();

            assertThat(log).containsExactly("select", "enter");
        }

        @Test
        void hold_shouldKeepArrivalOrder_ofInputQueuedWhileHeld() {
            session.setState(SessionState.AUTHED);
            session.offer(session.input((Runnable) session::hold));
            runPass();
            for (int i = 1; i <= 3; i++) {
                final int value = i;
                session.offer(session.input((Runnable) () -> log.add("packet " + value)));
            }
            runPass();

            world.execute(session::release);
            runPass();
            runPass();

            assertThat(log).containsExactly("packet 1", "packet 2", "packet 3");
        }

        @Test
        void close_shouldRunOnClosed_whileTheSessionIsHeld() {
            final AtomicReference<Loop> closedIn = new AtomicReference<>();
            session.onClosed(() -> closedIn.set(Loop.current()));
            session.setState(SessionState.AUTHED);
            session.offer(session.input((Runnable) session::hold));
            session.offer(session.input((Runnable) () -> log.add("enter")));
            runPass();

            session.close();
            runPass();

            assertThat(closedIn.get()).isSameAs(world);
            assertThat(log).as("held input is discarded with the queue").isEmpty();
            assertThat(session.state()).isEqualTo(SessionState.DISCONNECTED);
        }

        private void runPass() {
            world.runPass(world.getPassTime());
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
        void hasInput_shouldBeFalse_whileHeldWithInputQueued() {
            final Session session = session(limits(256, 16, 200, 256));
            session.offer(session.input(new Step(1)));
            drains.clear();

            session.hold();

            assertThat(session.hasInput()).isFalse();
            assertThat(session.poll()).isNull();
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
        void drainAbandoned_shouldClearTheSignalAndWarnNamingTheSession_whenThePoolClosesBeforeTheDrainStarts() {
            final Logger sessionLog = (Logger) LoggerFactory.getLogger(Session.class);
            final ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            sessionLog.addAppender(logs);
            try {
                final DrainScheduler scheduler =
                        DrainScheduler.pool((drain, abandoned) -> abandoned.run(), metrics.drainFailures());
                final Session session = new Session(
                        limits(256, 16, 200, 256),
                        scheduler,
                        scheduler,
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
        void drain_shouldReportAFailedItemAndRunTheRest() {
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
        void drain_shouldForgiveFailures_whenTheWindowPassed() {
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
        void offer_shouldRecordTheQueueDepth_whenEachItemIsEnqueued() {
            final Session session = session(limits(256, 16, 200, 256));

            for (int i = 1; i <= 3; i++) {
                session.offer(session.input(new Step(i)));
            }

            final DistributionSummary actual =
                    registry.get(SessionMetrics.QUEUE_DEPTH).summary();
            assertThat(actual.count()).isEqualTo(3);
            assertThat(actual.totalAmount()).isEqualTo(1 + 2 + 3);
            assertThat(actual.max()).isEqualTo(3);
            assertThat(actual.getId().getTags()).isEmpty();
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

    @Nested
    class InputTime {
        @Test
        void drain_shouldRecordTheInputTimeFromEnqueueToTheEndOfTheRun_whenALegacyPacketWaitsAndRuns() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Slow(meterClock, Duration.ofMillis(3))));
            meterClock.add(Duration.ofMillis(2));
            runDrains();

            assertThat(inputTimer(Slow.class, "SUCCESS", "none").totalTime(TimeUnit.MILLISECONDS))
                    .as("2 ms in the queue and 3 ms of run")
                    .isEqualTo(5.0);
            assertThat(registry.get(SessionMetrics.LEGACY_PACKET)
                            .tags("type", Slow.class.getName())
                            .timer()
                            .totalTime(TimeUnit.MILLISECONDS))
                    .as("the run time alone")
                    .isEqualTo(3.0);
        }

        @Test
        void drain_shouldRecordTheInputTimeOfACommand_whenItWaitedInTheQueue() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Step(1)));
            meterClock.add(Duration.ofMillis(4));
            runDrains();

            final Timer actual = inputTimer(Step.class, "SUCCESS", "none");
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(4.0);
        }

        @Test
        void drain_shouldRecordTheInputTimeWithTheError_whenTheRunFails() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Failing()));
            meterClock.add(Duration.ofMillis(1));
            runDrains();

            final Timer actual = inputTimer(Failing.class, "ERROR", "IllegalStateException");
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1.0);
        }

        @Test
        void drain_shouldTimeOnlyTheLatestOfACoalescedRunFromTheEnqueueOfTheOldestItReplaced() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Move(1)));
            meterClock.add(Duration.ofMillis(1));
            session.offer(session.input(new Move(2)));
            meterClock.add(Duration.ofMillis(1));
            session.offer(session.input(new Move(3)));
            meterClock.add(Duration.ofMillis(2));
            runDrains();

            final Timer actual = inputTimer(Move.class, "SUCCESS", "none");
            assertThat(actual.count()).as("the dropped moves are not recorded").isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS))
                    .as("the first move waited 4 ms before the latest ran")
                    .isEqualTo(4.0);
        }

        @Test
        void drain_shouldTimeAnInputFromItsOwnEnqueue_whenAnotherTypeSeparatesItFromAnEarlierCoalescedRun() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Move(1)));
            session.offer(session.input(new Move(2)));
            session.offer(session.input(new Step(1)));
            meterClock.add(Duration.ofMillis(3));
            session.offer(session.input(new Move(3)));
            meterClock.add(Duration.ofMillis(1));
            runDrains();

            assertThat(inputTimer(Move.class, "SUCCESS", "none").max(TimeUnit.MILLISECONDS))
                    .as("the first run waited 4 ms, the lone move after the step 1 ms")
                    .isEqualTo(4.0);
            assertThat(inputTimer(Move.class, "SUCCESS", "none").totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(5.0);
        }

        @Test
        void drain_shouldRecordNoInputTime_whenTheInputIsRejectedForAMissingContext() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Bound(new AtomicBoolean(false), "bound")));
            runDrains();

            assertThat(registry.find(SessionMetrics.INPUT).timers()).isEmpty();
        }

        @Test
        void close_shouldRecordNoInputTime_whenTheQueuedInputIsDiscarded() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offer(session.input(new Step(1)));
            session.close();
            runDrains();

            assertThat(registry.find(SessionMetrics.INPUT).timers()).isEmpty();
        }

        @Test
        void offerInternal_shouldCountFromTheEnqueueOnTheTransport_notFromTheCall() {
            final Session session = session(limits(256, 16, 200, 256));

            session.offerInternal(new Step(1));
            meterClock.add(Duration.ofMillis(5));
            transport.runTasks();
            meterClock.add(Duration.ofMillis(1));
            runDrains();

            assertThat(inputTimer(Step.class, "SUCCESS", "none").totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(1.0);
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
        return session(limits, pool);
    }

    private Session session(Session.Limits limits, DrainScheduler authedScheduler) {
        final SessionCommandHandler<Move> moves = new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Move command) {
                log.add("move " + command.value());
            }
        };
        return new Session(
                limits,
                pool,
                authedScheduler,
                dispatcher(List.of(steps(command -> log.add("step " + command.value())), moves, enters())),
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

    private ContextCommandHandler<String, Enter> enters() {
        final ContextResolver<String> selected = new ContextResolver<>() {
            @Override
            public String name() {
                return "CHARACTER";
            }

            @Override
            public String resolve(Session session) {
                return hero.get();
            }
        };
        return new ContextCommandHandler<>() {
            @Override
            public ContextResolver<String> context() {
                return selected;
            }

            @Override
            public void handle(String context, Enter command) {
                log.add("enter " + command.value() + " as " + context);
            }
        };
    }

    private Timer inputTimer(Class<?> type, String outcome, String exception) {
        return registry.get(SessionMetrics.INPUT)
                .tags("type", type.getName(), "outcome", outcome, "exception", exception)
                .timer();
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

    private static final class Slow implements Runnable {
        private final MockClock clock;
        private final Duration work;

        Slow(MockClock clock, Duration work) {
            this.clock = clock;
            this.work = work;
        }

        @Override
        public void run() {
            clock.add(work);
        }
    }
}
