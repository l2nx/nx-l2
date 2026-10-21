/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot;

import static org.assertj.core.api.Assertions.assertThat;

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
import app.l2nx.load.scenario.FakeScenario;
import app.l2nx.load.scenario.LoadScenario;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoadBotTest {
    private static final Duration INTERVAL = Duration.ofMillis(100);
    private static final Duration WARM_UP = Duration.ofSeconds(1);
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
            harness.runUntil(harness.timeline.steadyStart() - TimeUnit.MILLISECONDS.toNanos(450));

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
    }

    @Nested
    class Latency {
        @Test
        void acknowledged_shouldIncludeTheStall_whenServerStallsForTwoSeconds() {
            final Harness harness = new Harness();
            harness.protocol.ackDelay(Duration.ofMillis(10));
            harness.start(new FakeScenario(INTERVAL, Duration.ZERO));
            final long stallFrom = harness.timeline.steadyStart() + TimeUnit.SECONDS.toNanos(1);
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
            harness.runUntil(harness.timeline.steadyStart() + TimeUnit.SECONDS.toNanos(1));

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

            harness.runUntil(harness.timeline.steadyStart() + TimeUnit.SECONDS.toNanos(3));

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
            harness.runUntil(harness.timeline.steadyStart() + TimeUnit.SECONDS.toNanos(1));

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
            harness.protocol.capabilities(LoadBot.LIFECYCLE_INTENTS);
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

    private static final class Harness {
        private final MockTicker ticker = Ticker.newMockTicker();
        private final EmbeddedChannel channel =
                EmbeddedChannel.builder().ticker(ticker).build();
        private final FakeProtocol protocol = new FakeProtocol(ticker::nanoTime);
        private final LatencyRecorder latencies = new LatencyRecorder();
        private final GeneratorLagMonitor lag = new GeneratorLagMonitor();
        private RunTimeline timeline;
        private SessionListener listener;
        private int stops;

        LoadBot start(LoadScenario scenario) {
            return start(scenario, DEFAULT_TIMEOUT);
        }

        LoadBot start(LoadScenario scenario, Duration intentTimeout) {
            return start(scenario, 42, 0, intentTimeout);
        }

        LoadBot start(LoadScenario scenario, long seed, int index, Duration intentTimeout) {
            final InjectionProfile profile = InjectionProfile.builder()
                    .bots(index + 1)
                    .ramp(Duration.ZERO)
                    .warmUp(WARM_UP)
                    .steady(STEADY)
                    .rampDown(Duration.ZERO)
                    .build();
            timeline = new RunTimeline(profile, ticker::nanoTime);
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

        /** Past the leave and the longest drain the tests use. */
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
            public ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener sessionListener) {
                listener = sessionListener;
                return protocol.open(identity, loop, sessionListener);
            }
        }
    }
}
