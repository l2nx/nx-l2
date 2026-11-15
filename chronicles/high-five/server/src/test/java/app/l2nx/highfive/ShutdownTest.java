/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerQueue;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.ListingStatus;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericApplicationContext;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("GeneralConfig")
@ResourceLock("LoginService")
@ResourceLock("Shutdown")
class ShutdownTest {
    private final RecordingExit exit = new RecordingExit();
    private final ServerListing listing = new ServerListing();
    private RecordingShutdown shutdown;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private ManualGameClock clock;

    @BeforeEach
    void setUp(Loop world) {
        Shutdown.bind(exit);
        new LoginService(new FakeLoginPort(), new OnlineAccounts(), listing);
        listing.setStatus(ListingStatus.AUTO);
        shutdown = new RecordingShutdown(world);
        clock = (ManualGameClock) world.getClock();
        appender.start();
        logger().addAppender(appender);
    }

    // The world loop is shared by the tests, so a countdown left running would fire into the next one.
    @AfterEach
    void tearDown() {
        WorldLoopExtension.runInPass(() -> shutdown.abort(null));
        logger().detachAppender(appender);
        appender.stop();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(Shutdown.class);
    }

    @Nested
    class StartShutdown {
        @Test
        void startShutdown_shouldAnnounceAtTheMarks_andExitOnce_whenTheCountdownEnds() {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 60, false));
            passSeconds(65);

            assertThat(shutdown.announcements())
                    .containsExactly(
                            new Announcement(0, 60),
                            new Announcement(30, 30),
                            new Announcement(50, 10),
                            new Announcement(55, 5),
                            new Announcement(56, 4),
                            new Announcement(57, 3),
                            new Announcement(58, 2),
                            new Announcement(59, 1));
            assertThat(exit.codes()).containsExactly(ServerExitCode.SHUTDOWN);
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.DOWN);
        }

        @Test
        void startShutdown_shouldCloseTheLogin_sixtySecondsBeforeTheEnd() {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 120, true));
            passSeconds(59);
            final ListingStatus before = listing.getStatus();
            passSeconds(1);

            assertThat(before).isEqualTo(ListingStatus.AUTO);
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.DOWN);
        }

        @Test
        void startShutdown_shouldReplaceTheRunningCountdown_whenStartedAgain() {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 120, false));
            passSeconds(10);
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 5, true));
            passSeconds(130);

            assertThat(exit.codes()).containsExactly(ServerExitCode.RESTART);
            assertThat(shutdown.announcements())
                    .extracting(Announcement::secondsLeft)
                    .containsExactly(120, 5, 4, 3, 2, 1);
        }

        @ParameterizedTest(name = "wall clock stepped by {0} s")
        @ValueSource(longs = {-3_600, 3_600})
        void startShutdown_shouldKeepTheCountdown_whenTheWallClockSteps(long wallStepSeconds) {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 60, false));
            passSeconds(20);
            clock.advanceWall(Duration.ofSeconds(wallStepSeconds));
            passSeconds(39);
            final List<ServerExitCode> beforeEnd = List.copyOf(exit.codes());
            passSeconds(1);

            assertThat(beforeEnd).isEmpty();
            assertThat(exit.codes()).containsExactly(ServerExitCode.SHUTDOWN);
            assertThat(shutdown.announcements())
                    .containsExactly(
                            new Announcement(0, 60),
                            new Announcement(30, 30),
                            new Announcement(50, 10),
                            new Announcement(55, 5),
                            new Announcement(56, 4),
                            new Announcement(57, 3),
                            new Announcement(58, 2),
                            new Announcement(59, 1));
        }

        @Test
        void startShutdown_shouldNotReplaceTheCountdown_whenItHasFinished() {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 1, false));
            passSeconds(1);
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 5, true));
            passSeconds(10);

            assertThat(exit.codes()).containsExactly(ServerExitCode.SHUTDOWN);
        }

        @Test
        void startShutdown_shouldStartOnTheWorldLoop_whenCalledOffIt() {
            shutdown.startShutdown(null, 0, false);
            final List<ServerExitCode> beforePass = List.copyOf(exit.codes());
            passSeconds(2);

            assertThat(beforePass).isEmpty();
            assertThat(exit.codes()).containsExactly(ServerExitCode.SHUTDOWN);
        }
    }

    @Nested
    class Abort {
        @Test
        void abort_shouldStopTheCountdown_andReopenTheLogin() {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 30, false));
            passSeconds(10);
            WorldLoopExtension.runInPass(() -> shutdown.abort(null));
            passSeconds(30);

            assertThat(exit.codes()).isEmpty();
            assertThat(shutdown.announcements())
                    .extracting(Announcement::secondsLeft)
                    .containsExactly(30);
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.AUTO);
        }

        @Test
        void abort_shouldCancelTheCountdownStepsAtOnce() {
            final List<Long> expected = new ArrayList<>();
            final List<Long> actual = new ArrayList<>();
            WorldLoopExtension.runInPass(() -> {
                final TimerQueue timers = WorldPartition.loop().timers(TimeBase.GAME);
                expected.add(timers.nextDeadline());
                expected.add((long) timers.size());
                shutdown.startShutdown(null, 3_600, false);
                shutdown.abort(null);
                actual.add(timers.nextDeadline());
                actual.add((long) timers.size());
            });

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void abort_shouldAnswerTooLate_whenTheCountdownHasFinished() {
            WorldLoopExtension.runInPass(() -> shutdown.startShutdown(null, 5, false));
            passSeconds(5);
            WorldLoopExtension.runInPass(() -> shutdown.abort(null));

            assertThat(exit.codes()).containsExactly(ServerExitCode.SHUTDOWN);
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.DOWN);
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("ABORT failed"))
                    .noneMatch(message -> message.contains("has been stopped"));
        }
    }

    private static void passSeconds(int seconds) {
        for (int i = 0; i < seconds; i++) {
            WorldLoopExtension.passAfter(Duration.ofSeconds(1));
        }
    }

    private record Announcement(long elapsedSeconds, int secondsLeft) {}

    private static final class RecordingShutdown extends Shutdown {
        private final Loop world;
        private final List<Announcement> announcements = new ArrayList<>();
        private long startedAt = Long.MIN_VALUE;

        private RecordingShutdown(Loop world) {
            this.world = world;
        }

        @Override
        void announce(int seconds) {
            if (startedAt == Long.MIN_VALUE) {
                startedAt = world.gameNow();
            }
            announcements.add(new Announcement((world.gameNow() - startedAt) / 1_000, seconds));
        }

        List<Announcement> announcements() {
            return announcements;
        }
    }

    private static final class RecordingExit extends ServerExit {
        private final List<ServerExitCode> codes = new ArrayList<>();

        private RecordingExit() {
            super(new GenericApplicationContext());
        }

        @Override
        public void exit(ServerExitCode code) {
            codes.add(code);
        }

        List<ServerExitCode> codes() {
            return codes;
        }
    }
}
