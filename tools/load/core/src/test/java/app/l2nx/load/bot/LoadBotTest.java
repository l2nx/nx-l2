/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.load.bot.intent.EnterWorld;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.Login;
import app.l2nx.load.bot.intent.Logout;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.intent.SelectCharacter;
import app.l2nx.load.bot.intent.SelectServer;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.Died;
import app.l2nx.load.bot.observation.ObjectGone;
import app.l2nx.load.bot.observation.ObjectMoved;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.bot.observation.StatusChanged;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.injection.RunTimeline;
import app.l2nx.load.latency.GeneratorLagMonitor;
import app.l2nx.load.latency.LatencyRecorder;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.FakeProtocol;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.StepStats;
import app.l2nx.load.scenario.FakeScenario;
import app.l2nx.load.scenario.LoadScenario;
import app.l2nx.load.scenario.model.MeasuredPhase;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Ticker;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class LoadBotTest {
    private static final Duration INTERVAL = Duration.ofMillis(100);
    private static final Duration WARM_UP = Duration.ofSeconds(1);
    private static final Duration RAMP = Duration.ofSeconds(1);
    private static final Duration STEADY = Duration.ofSeconds(5);
    private static final long STEADY_INTENTS = STEADY.toMillis() / INTERVAL.toMillis();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    // The warm-up holds the first ten scenario intents, so this one and its neighbours fall in the steady window.
    private static final int STEADY_ORDINAL = 20;

    @Nested
    class Lifecycle {
        @Test
        void start_shouldSendLifecycleIntentsInOrder_whenProtocolWalksTheLifecycle() {
            final Harness harness = new Harness();
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runFor(Duration.ofMillis(50));

            assertThat(bot.state()).isEqualTo(LoadBotState.IN_GAME);
            assertThat(harness.session().sent().subList(0, 4))
                    .containsExactly(
                            Login.builder().account("load0").password("secret").build(),
                            new SelectServer(),
                            new SelectCharacter(2),
                            new EnterWorld());
        }

        @Test
        void stateChanged_shouldKeepTheConfiguredRate_whenInGameIsReportedAgain() {
            final Harness harness = new Harness();
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            harness.runUntil(harness.timeline.measuredStart() - TimeUnit.MILLISECONDS.toNanos(450));

            harness.channel.eventLoop().execute(() -> harness.listener.stateChanged(LoadBotState.IN_GAME));
            harness.runToEnd();

            assertThat(harness.latencies.sent(IntentKind.MOVE_TO))
                    .as("one action chain")
                    .isEqualTo(STEADY_INTENTS);
        }

        @Test
        void leave_shouldLogOutAndStopWithoutError_whenRampDownIsReached() {
            final Harness harness = new Harness();
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            final List<Intent> sent = harness.session().sent();
            assertThat(sent.getLast()).isEqualTo(new Logout());
            assertThat(bot.state()).isEqualTo(LoadBotState.STOPPED);
            assertThat(bot.stoppedOnError()).isFalse();
            assertThat(harness.stops).isEqualTo(1);
            assertThat(harness.session().open()).isFalse();
        }

        @Test
        void leave_shouldCloseWithoutLoggingIn_whenAGameBoundLoadBotIsStillConnecting() {
            final Harness harness = new Harness();
            harness.protocol.connectDelay(Duration.ofSeconds(7));
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            assertThat(bot.state()).isEqualTo(LoadBotState.STOPPED);
            assertThat(bot.stoppedOnError()).isFalse();
            assertThat(bot.reachedFinalState()).isFalse();
            assertThat(harness.session().sent()).isEmpty();
        }

        @Test
        void leave_shouldKeepWalkingToTheFinalState_whenItIsBeforeTheGameAndNotReachedYet() {
            final Harness harness = new Harness();
            harness.protocol.connectDelay(Duration.ofSeconds(7));
            final LoadBot bot =
                    harness.start(new FakeScenario(INTERVAL, Duration.ZERO).finalState(LoadBotState.SERVER_LIST));
            harness.runUntil(harness.timeline.end() + TimeUnit.MILLISECONDS.toNanos(100));
            assertThat(bot.state()).as("still connecting after its leave time").isEqualTo(LoadBotState.LOGIN_CONNECT);

            harness.runToEnd();

            assertThat(bot.reachedFinalState()).isTrue();
            assertThat(bot.stoppedOnError()).isFalse();
            assertThat(harness.session().sent())
                    .containsExactly(
                            Login.builder().account("load0").password("secret").build());
            assertThat(harness.session().open()).isFalse();
        }
    }

    @Nested
    class FinalState {
        static Stream<Arguments> lifecycleIntentsByFinalState() {
            return Stream.of(
                    Arguments.of(LoadBotState.LOGIN_AUTH, Set.of()),
                    Arguments.of(LoadBotState.SERVER_LIST, Set.of(IntentKind.LOGIN)),
                    Arguments.of(LoadBotState.GAME_CONNECT, Set.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER)),
                    Arguments.of(LoadBotState.CHARACTER_SELECT, Set.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER)),
                    Arguments.of(
                            LoadBotState.ENTERING,
                            Set.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER, IntentKind.SELECT_CHARACTER)),
                    Arguments.of(
                            LoadBotState.IN_GAME,
                            Set.of(
                                    IntentKind.LOGIN,
                                    IntentKind.SELECT_SERVER,
                                    IntentKind.SELECT_CHARACTER,
                                    IntentKind.ENTER_WORLD,
                                    IntentKind.LOGOUT)));
        }

        @ParameterizedTest(name = "final state {0}")
        @MethodSource("lifecycleIntentsByFinalState")
        void lifecycleIntents_shouldNameTheIntentsUpToTheFinalState(LoadBotState finalState, Set<IntentKind> expected) {
            assertThat(LoadBot.lifecycleIntents(finalState)).containsExactlyInAnyOrderElementsOf(expected);
        }

        @ParameterizedTest(name = "final state {0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"LOGIN_CONNECT", "CLOSING", "STOPPED"})
        void lifecycleIntents_shouldReject_whenTheFinalStateMeasuresNothingOrEndsTheSession(LoadBotState finalState) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> LoadBot.lifecycleIntents(finalState))
                    .withMessageContaining(finalState.name());
        }

        @Test
        void constructor_shouldReject_whenTheScenarioStopsAtClosing() {
            final Harness harness = new Harness();
            final FakeScenario scenario = new FakeScenario(INTERVAL, Duration.ZERO).finalState(LoadBotState.CLOSING);

            assertThatIllegalArgumentException().isThrownBy(() -> harness.start(scenario));
        }

        @ParameterizedTest(name = "final state {0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"SERVER_LIST", "GAME_CONNECT", "CHARACTER_SELECT", "ENTERING"})
        void stateChanged_shouldCloseWithoutError_whenTheFinalStateBeforeTheGameIsReached(LoadBotState finalState) {
            final Harness harness = new Harness().ramp(RAMP);
            // Only the intents up to the final state: any other one would stop the load bot on an error.
            harness.protocol.capabilities(LoadBot.lifecycleIntents(finalState));
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO)
                    .measuredPhase(MeasuredPhase.RAMP)
                    .finalState(finalState));

            harness.runFor(Duration.ofMillis(50));

            assertThat(bot.state()).isEqualTo(LoadBotState.STOPPED);
            assertThat(bot.stoppedOnError()).isFalse();
            assertThat(harness.stops).isEqualTo(1);
            assertThat(harness.session().open()).isFalse();
            assertThat(harness.session().sent()).noneMatch(Logout.class::isInstance);
            assertThat(harness.latencies.stats().values())
                    .allSatisfy(stats ->
                            assertThat(stats.timeouts() + stats.errors()).isZero());
            assertThat(harness.latencies.stepStats())
                    .as("every state left on the way, not the final one")
                    .containsOnlyKeys(EnumSet.range(LoadBotState.LOGIN_CONNECT, previous(finalState)));
        }

        @Test
        void stateChanged_shouldCloseAtTheFinalState_whenTheAdapterSkipsIt() {
            final Harness harness = new Harness();
            // Held in LOGIN_CONNECT, so the test alone reports the next state.
            harness.protocol.connectDelay(Duration.ofSeconds(1));
            final LoadBot bot =
                    harness.start(new FakeScenario(INTERVAL, Duration.ZERO).finalState(LoadBotState.GAME_CONNECT));
            harness.runFor(Duration.ofMillis(10));

            harness.channel.eventLoop().execute(() -> harness.listener.stateChanged(LoadBotState.CHARACTER_SELECT));
            harness.runFor(Duration.ofMillis(1));

            assertThat(bot.state()).isEqualTo(LoadBotState.STOPPED);
            assertThat(bot.reachedFinalState()).isTrue();
            assertThat(bot.stoppedOnError()).isFalse();
            assertThat(harness.session().sent())
                    .as("no intent past the final state")
                    .isEmpty();
        }

        @Test
        void stateChanged_shouldNotCountAnError_whenStoppedIsReportedAfterTheLoadBotClosedOnItsFinalState() {
            final Harness harness = new Harness();
            final LoadBot bot =
                    harness.start(new FakeScenario(INTERVAL, Duration.ZERO).finalState(LoadBotState.GAME_CONNECT));
            harness.runFor(Duration.ofMillis(10));

            harness.channel.eventLoop().execute(() -> harness.listener.stateChanged(LoadBotState.STOPPED));
            harness.runToEnd();

            assertThat(bot.stoppedOnError()).isFalse();
            assertThat(harness.stops).isEqualTo(1);
        }
    }

    @Nested
    class ReachedFinalState {
        @ParameterizedTest(name = "final state {0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"SERVER_LIST", "GAME_CONNECT", "IN_GAME"})
        void reachedFinalState_shouldBeTrue_whenTheLoadBotReachedItsFinalState(LoadBotState finalState) {
            final Harness harness = new Harness();
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO).finalState(finalState));

            harness.runToEnd();

            assertThat(bot.reachedFinalState()).isTrue();
            assertThat(bot.stoppedOnError()).isFalse();
        }

        @Test
        void reachedFinalState_shouldStayTrue_whenTheLoadBotFailsInTheGame() {
            final Harness harness = new Harness();
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            harness.runUntil(harness.timeline.measuredStart());

            harness.session().fail();
            harness.runToEnd();

            assertThat(bot.reachedFinalState()).isTrue();
            assertThat(bot.stoppedOnError()).isTrue();
        }

        @Test
        void reachedFinalState_shouldBeFalse_whenTheLoadBotStopsBeforeItsFinalState() {
            final Harness harness = new Harness();
            harness.protocol.failOnEnter(index -> true);
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            assertThat(bot.reachedFinalState()).isFalse();
            assertThat(bot.stoppedOnError()).isTrue();
        }
    }

    @Nested
    class Steps {
        @Test
        void stateChanged_shouldRecordEveryStateLeftBeforeTheFinalOne_whenTheRampIsMeasured() {
            final Harness harness = new Harness().ramp(RAMP);
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO).measuredPhase(MeasuredPhase.RAMP));

            harness.runToEnd();

            final Map<LoadBotState, StepStats> actual = harness.latencies.stepStats();
            assertThat(actual)
                    .as("no IN_GAME, CLOSING or STOPPED")
                    .containsOnlyKeys(EnumSet.range(LoadBotState.LOGIN_CONNECT, LoadBotState.ENTERING))
                    .allSatisfy((state, stats) -> assertThat(stats.count()).isEqualTo(1));
            assertThat(harness.latencies.stats())
                    .as("the lifecycle intents of the arrival, neither actions nor the logout")
                    .containsOnlyKeys(
                            IntentKind.LOGIN,
                            IntentKind.SELECT_SERVER,
                            IntentKind.SELECT_CHARACTER,
                            IntentKind.ENTER_WORLD);
            assertThat(harness.latencies.stats().get(IntentKind.LOGIN).acknowledged())
                    .isEqualTo(1);
        }

        @Test
        void stateChanged_shouldRecordTheWholeArrival_whenTheConnectStallsPastTheRamp() {
            final Harness harness = new Harness().ramp(RAMP);
            harness.protocol.connectDelay(Duration.ofSeconds(2));
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO).measuredPhase(MeasuredPhase.RAMP));

            harness.runToEnd();

            final Map<LoadBotState, StepStats> actual = harness.latencies.stepStats();
            assertThat(actual)
                    .as("its intended start lies in the ramp: the later states count too")
                    .containsOnlyKeys(EnumSet.range(LoadBotState.LOGIN_CONNECT, LoadBotState.ENTERING));
            assertThat(actual.get(LoadBotState.LOGIN_CONNECT).maxMillis()).isBetween(1_999.0, 2_002.0);
            assertThat(harness.latencies.stats().get(IntentKind.LOGIN).acknowledged())
                    .as("intended after the ramp")
                    .isEqualTo(1);
        }

        @Test
        void stateChanged_shouldMeasureFromTheIntendedStart_whenTheEventLoopStallsBeforeTheConnect() {
            final Harness harness = new Harness().ramp(RAMP);
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO).measuredPhase(MeasuredPhase.RAMP));

            harness.ticker.advance(2, TimeUnit.SECONDS);
            harness.runToEnd();

            final StepStats actual = harness.latencies.stepStats().get(LoadBotState.LOGIN_CONNECT);
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.maxMillis()).isBetween(1_999.0, 2_002.0);
        }

        @Test
        void stateChanged_shouldRecordNoStep_whenTheLifecycleRunsBeforeTheSteadyWindow() {
            final Harness harness = new Harness();
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            assertThat(harness.latencies.stepStats()).isEmpty();
            assertThat(harness.latencies.sent(IntentKind.MOVE_TO)).isEqualTo(STEADY_INTENTS);
        }
    }

    @Nested
    class Latency {
        @Test
        void acknowledged_shouldIncludeTheStall_whenServerStallsForTwoSeconds() {
            final Harness harness = new Harness();
            harness.protocol.ackDelay(Duration.ofMillis(10));
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            final long stallFrom = harness.timeline.measuredStart() + TimeUnit.SECONDS.toNanos(1);
            harness.protocol.stall(stallFrom, stallFrom + TimeUnit.SECONDS.toNanos(2));

            harness.runToEnd();

            final IntentStats actual = harness.latencies.stats().get(IntentKind.MOVE_TO);
            assertThat(actual.sent()).as("open rate: no wait for answers").isEqualTo(STEADY_INTENTS);
            assertThat(actual.acknowledged()).isEqualTo(STEADY_INTENTS);
            assertThat(actual.maxMillis()).isBetween(1_890.0, 2_010.0);
            assertThat(actual.p50Millis()).isBetween(9.0, 12.0);
        }

        @Test
        void acknowledged_shouldMeasureFromIntendedTime_whenEventLoopStallsForTwoSeconds() {
            final Harness harness = new Harness();
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            harness.runUntil(harness.timeline.measuredStart() + TimeUnit.SECONDS.toNanos(1));

            harness.ticker.advance(2, TimeUnit.SECONDS);
            harness.runToEnd();

            final IntentStats actual = harness.latencies.stats().get(IntentKind.MOVE_TO);
            assertThat(actual.sent())
                    .as("late actions keep their intended times")
                    .isEqualTo(STEADY_INTENTS);
            assertThat(actual.maxMillis()).isBetween(1_890.0, 2_010.0);
            assertThat(harness.lag.histogram().getMaxValue()).isBetween(1_890_000_000L, 2_010_000_000L);
        }
    }

    @Nested
    class GeneratorLag {
        @Test
        void schedule_shouldRecordTheLag_whileALoadBotIsStillArrivingAfterTheRamp() {
            final Harness harness = new Harness().ramp(RAMP);
            harness.protocol.connectDelay(Duration.ofSeconds(8));
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO)
                    .measuredPhase(MeasuredPhase.RAMP)
                    .finalState(LoadBotState.SERVER_LIST));
            harness.runUntil(harness.timeline.end() - TimeUnit.MILLISECONDS.toNanos(10));

            harness.ticker.advance(500, TimeUnit.MILLISECONDS);
            harness.runToEnd();

            assertThat(harness.lag.histogram().getMaxValue())
                    .as("the leave task, run late")
                    .isBetween(480_000_000L, 500_000_000L);
        }

        @Test
        void schedule_shouldRecordNoLag_whenEveryLoadBotIsDoneArriving() {
            final Harness harness = new Harness().ramp(RAMP);
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO).measuredPhase(MeasuredPhase.RAMP));
            harness.runUntil(harness.timeline.start() + TimeUnit.SECONDS.toNanos(3));

            harness.ticker.advance(2, TimeUnit.SECONDS);
            harness.runToEnd();

            assertThat(harness.lag.histogram().getMaxValue())
                    .as("only the connect, on time")
                    .isLessThan(TimeUnit.MILLISECONDS.toNanos(2));
        }
    }

    @Nested
    class SteadyWindow {
        @Test
        void send_shouldRecordOnlyIntentsIntendedInTheSteadyWindow() {
            final Harness harness = new Harness();
            harness.start(new FakeScenario(INTERVAL, Duration.ofMillis(30)));

            harness.runToEnd();

            final long moves = harness.session().sent().stream()
                    .filter(MoveTo.class::isInstance)
                    .count();
            assertThat(moves).as("warm-up moves are sent too").isGreaterThan(STEADY_INTENTS);
            assertThat(harness.latencies.stats()).containsOnlyKeys(IntentKind.MOVE_TO);
            assertThat(harness.latencies.sent(IntentKind.MOVE_TO)).isBetween(STEADY_INTENTS - 1, STEADY_INTENTS + 1);
        }
    }

    @Nested
    class Timeout {
        @Test
        void expire_shouldCountTimeouts_whenServerNeverAcknowledges() {
            final Harness harness = new Harness();
            harness.protocol.neverAcknowledge(IntentKind.MOVE_TO);
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO), Duration.ofMillis(500));

            harness.runToEnd();

            final IntentStats actual = harness.latencies.stats().get(IntentKind.MOVE_TO);
            assertThat(actual.sent()).isEqualTo(STEADY_INTENTS);
            assertThat(actual.acknowledged()).isZero();
            assertThat(actual.timeouts()).isEqualTo(STEADY_INTENTS);
            assertThat(harness.session().sent().getLast()).isEqualTo(new Logout());
            assertThat(bot.stoppedOnError()).isFalse();
        }

        @Test
        void acknowledged_shouldNotShortenLaterLatencies_whenAcknowledgementComesAfterTheTimeout() {
            final Harness harness = new Harness();
            harness.protocol.ackDelay(Duration.ofMillis(10)).slowAnswer(STEADY_ORDINAL, Duration.ofMillis(300));
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO), Duration.ofMillis(250));

            harness.runToEnd();

            final IntentStats actual = harness.latencies.stats().get(IntentKind.MOVE_TO);
            assertThat(actual.timeouts()).isEqualTo(1);
            assertThat(actual.acknowledged()).isEqualTo(STEADY_INTENTS - 1);
            assertThat(actual.maxMillis())
                    .as("the next intent waited behind the slow one")
                    .isBetween(199.0, 203.0);
            assertThat(harness.latencies.histogram(IntentKind.MOVE_TO).getMinValue())
                    .as("no acknowledgement credited to a younger intent")
                    .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(10));
        }

        @Test
        void rejected_shouldTakeTheOldestIntentAndCountAnError() {
            final Harness harness = new Harness();
            harness.protocol.ackDelay(Duration.ofMillis(10)).reject(STEADY_ORDINAL);
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            final IntentStats actual = harness.latencies.stats().get(IntentKind.MOVE_TO);
            assertThat(harness.latencies.errors(IntentKind.MOVE_TO)).isEqualTo(1);
            assertThat(actual.timeouts()).isZero();
            assertThat(actual.acknowledged()).isEqualTo(STEADY_INTENTS - 1);
            assertThat(actual.maxMillis()).isBetween(9.0, 12.0);
        }

        @Test
        void expire_shouldPurgeExpiredIntents_whenTwoIntentTimeoutsPassed() {
            final Harness harness = new Harness();
            harness.protocol.neverAcknowledge(IntentKind.MOVE_TO);
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO), Duration.ofMillis(500));

            harness.runUntil(harness.timeline.measuredStart() + TimeUnit.SECONDS.toNanos(3));

            assertThat(bot.pendingCount()).as("only the last second of intents").isBetween(9, 11);
            assertThat(harness.latencies.timeouts(IntentKind.MOVE_TO)).isBetween(25L, 26L);
        }
    }

    @Nested
    class Failure {
        @Test
        void failed_shouldStopWithErrorAndCountPendingIntentsAsErrors() {
            final Harness harness = new Harness();
            harness.protocol.neverAcknowledge(IntentKind.MOVE_TO);
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            harness.runUntil(harness.timeline.measuredStart() + TimeUnit.SECONDS.toNanos(1));

            harness.session().fail();
            harness.runToEnd();

            assertThat(bot.stoppedOnError()).isTrue();
            assertThat(bot.state()).isEqualTo(LoadBotState.STOPPED);
            assertThat(harness.stops).isEqualTo(1);
            assertThat(harness.latencies.sent(IntentKind.MOVE_TO)).isEqualTo(10);
            assertThat(harness.latencies.errors(IntentKind.MOVE_TO)).isEqualTo(10);
            assertThat(harness.latencies.timeouts(IntentKind.MOVE_TO)).isZero();
        }

        @Test
        void stateChanged_shouldStopWithError_whenConnectionDropsBeforeLogout() {
            final Harness harness = new Harness();
            harness.protocol.dropOnEnter(index -> true);
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            assertThat(bot.stoppedOnError()).isTrue();
            assertThat(harness.stops).isEqualTo(1);
        }

        @Test
        void send_shouldStopWithError_whenProtocolCannotExpressTheIntent() {
            final Harness harness = new Harness();
            harness.protocol.capabilities(LoadBot.lifecycleIntents(LoadBotState.IN_GAME));
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));

            harness.runToEnd();

            assertThat(bot.stoppedOnError()).isTrue();
            assertThat(harness.session().sent()).noneMatch(MoveTo.class::isInstance);
        }
    }

    @Nested
    class Observations {
        @Test
        void observed_shouldKeepOwnStateAndVisibleObjects() {
            final Harness harness = new Harness();
            final LoadBot bot = harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            harness.runFor(Duration.ofMillis(10));
            final Position there = Position.builder().x(1).y(2).z(3).build();
            final Position elsewhere = Position.builder().x(4).y(5).z(6).build();
            final ObjectSeen monster = ObjectSeen.builder()
                    .objectId(2_000)
                    .kind(ObjectSeen.Kind.NPC)
                    .position(there)
                    .build();

            harness.session().emit(monster);
            harness.session()
                    .emit(ObjectSeen.builder()
                            .objectId(2_001)
                            .kind(ObjectSeen.Kind.CHARACTER)
                            .position(there)
                            .team(1)
                            .build());
            harness.session()
                    .emit(ObjectMoved.builder()
                            .objectId(2_000)
                            .position(elsewhere)
                            .build());
            harness.session().emit(new Died(2_000));
            harness.session().emit(new ObjectGone(2_001));
            harness.session()
                    .emit(ObjectMoved.builder()
                            .objectId(bot.selfObjectId())
                            .position(there)
                            .build());
            harness.session().emit(StatusChanged.builder().hp(50).mp(20).build());
            harness.runFor(Duration.ofMillis(1));

            assertThat(bot.selfObjectId()).isEqualTo(1_000);
            assertThat(bot.position()).isEqualTo(there);
            assertThat(bot.hp()).isEqualTo(50);
            assertThat(bot.mp()).isEqualTo(20);
            assertThat(bot.dead()).isFalse();
            assertThat(bot.visibleObjects())
                    .containsExactly(
                            monster.toBuilder().position(elsewhere).dead(true).build());
        }
    }

    @Nested
    class Determinism {
        @Test
        void act_shouldSendTheSameIntents_whenSeedAndIndexAreTheSame() {
            final List<Intent> first = sentIntents(42, 3);
            final List<Intent> second = sentIntents(42, 3);

            assertThat(second).isEqualTo(first);
            assertThat(sentIntents(42, 4)).isNotEqualTo(first);
            assertThat(sentIntents(43, 3)).isNotEqualTo(first);
        }

        private List<Intent> sentIntents(long seed, int index) {
            final Harness harness = new Harness();
            harness.start(new FakeScenario(INTERVAL, Duration.ofMillis(30)), seed, index, DEFAULT_TIMEOUT);
            harness.runToEnd();
            return harness.session().sent();
        }

        @ParameterizedTest(name = "seed {0}, index {1} vs {2}")
        @CsvSource({"0, 0, 1", "42, 1, 2", "-1, 7, 8", "42, 0, 1000"})
        void seedOf_shouldDiffer_whenIndexesDiffer(long seed, int index, int other) {
            assertThat(LoadBot.seedOf(seed, index)).isNotEqualTo(LoadBot.seedOf(seed, other));
        }
    }

    private static LoadBotState previous(LoadBotState state) {
        return LoadBotState.values()[state.ordinal() - 1];
    }

    private static final class Harness {
        private final MockTicker ticker = Ticker.newMockTicker();
        private final EmbeddedChannel channel =
                EmbeddedChannel.builder().ticker(ticker).build();
        private final FakeProtocol protocol = new FakeProtocol(ticker::nanoTime);
        private final LatencyRecorder latencies = new LatencyRecorder();
        private final GeneratorLagMonitor lag = new GeneratorLagMonitor();
        private Duration ramp = Duration.ZERO;
        private RunTimeline timeline;
        private SessionListener listener;
        private int stops;

        Harness ramp(Duration value) {
            ramp = value;
            return this;
        }

        LoadBot start(LoadScenario scenario) {
            return start(scenario, DEFAULT_TIMEOUT);
        }

        LoadBot start(LoadScenario scenario, Duration intentTimeout) {
            return start(scenario, 42, 0, intentTimeout);
        }

        LoadBot start(LoadScenario scenario, long seed, int index, Duration intentTimeout) {
            final InjectionProfile profile = InjectionProfile.builder()
                    .bots(index + 1)
                    .ramp(ramp)
                    .warmUp(WARM_UP)
                    .steady(STEADY)
                    .rampDown(Duration.ZERO)
                    .build();
            timeline = new RunTimeline(profile, scenario.measuredPhase(), ticker::nanoTime);
            final LoadBotIdentity identity = LoadBotIdentity.builder()
                    .index(index)
                    .account("load" + index)
                    .password("secret")
                    .characterSlot(2)
                    .build();
            final LoadBot bot = new LoadBot(
                    identity,
                    seed,
                    channel.eventLoop(),
                    new ListenerCapture(),
                    scenario,
                    timeline,
                    latencies,
                    lag,
                    intentTimeout,
                    () -> stops++);
            bot.start();
            return bot;
        }

        FakeProtocol.Session session() {
            return protocol.sessions().getFirst();
        }

        void runFor(Duration duration) {
            runUntil(ticker.nanoTime() + duration.toNanos());
        }

        void runUntil(long time) {
            channel.runPendingTasks();
            while (ticker.nanoTime() - time < 0) {
                ticker.advance(1, TimeUnit.MILLISECONDS);
                channel.runPendingTasks();
            }
        }

        void runToEnd() {
            runUntil(timeline.end() + TimeUnit.SECONDS.toNanos(6));
        }

        /** The fake protocol, keeping the listener so a test can report lifecycle steps the fake never sends. */
        private final class ListenerCapture implements ClientProtocol {
            @Override
            public String id() {
                return protocol.id();
            }

            @Override
            public Set<IntentKind> capabilities() {
                return protocol.capabilities();
            }

            @Override
            public LoadBotState lastState() {
                return protocol.lastState();
            }

            @Override
            public IoHandlerFactory ioHandlerFactory() {
                return protocol.ioHandlerFactory();
            }

            @Override
            public ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener sessionListener) {
                listener = sessionListener;
                return protocol.open(identity, loop, sessionListener);
            }
        }
    }
}
