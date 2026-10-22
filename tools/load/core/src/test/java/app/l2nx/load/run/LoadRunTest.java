/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.latency.LatencyRecorder;
import app.l2nx.load.protocol.FakeProtocol;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.InvalidReason;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.scenario.FakeScenario;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Real runs on a real event loop group with sub-second phases; isolated so other tests do not add generator lag. */
@Isolated
class LoadRunTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-21T08:15:30Z");
    private static final Duration INTERVAL = Duration.ofMillis(10);
    private static final RunSettings SETTINGS = RunSettings.withDefaults()
            .scenario("load-fake")
            .protocol("fake")
            .injection(InjectionProfile.builder()
                    .bots(8)
                    .ramp(Duration.ofMillis(50))
                    .warmUp(Duration.ofMillis(50))
                    .steady(Duration.ofMillis(400))
                    .rampDown(Duration.ofMillis(50))
                    .build())
            .seed(42)
            .eventLoopThreads(2)
            .intentTimeout(Duration.ofMillis(200))
            .scrapeInterval(Duration.ofMillis(100))
            .build();
    private static final IntFunction<LoadBotIdentity> IDENTITIES = index -> LoadBotIdentity.builder()
            .index(index)
            .account("load" + index)
            .password("secret")
            .characterSlot(0)
            .build();

    private final LoadRun run = new LoadRun(System::nanoTime, InstantSource.fixed(STARTED_AT));
    private final FakeProtocol protocol = new FakeProtocol(System::nanoTime).ackDelay(Duration.ofMillis(2));
    private final LatencyRecorder latencies = new LatencyRecorder();

    private RunSummary execute(RunSettings settings, FakeScenario scenario, ServerMeterWindow serverMeters)
            throws InterruptedException {
        return run.execute(settings, scenario, protocol, IDENTITIES, latencies, serverMeters);
    }

    @Nested
    class Check {
        static Stream<Arguments> missingCapabilities() {
            return Stream.of(Arguments.of(IntentKind.MOVE_TO, "scenario"), Arguments.of(IntentKind.LOGIN, "lifecycle"));
        }

        @ParameterizedTest(name = "{0} missing ({1})")
        @MethodSource("missingCapabilities")
        void execute_shouldFailBeforeStart_whenProtocolMissesAnIntentKind(IntentKind missing, String needer) {
            final Set<IntentKind> capabilities = EnumSet.allOf(IntentKind.class);
            capabilities.remove(missing);
            protocol.capabilities(capabilities);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), null))
                    .withMessageContaining(missing.name());
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenProtocolMissesALifecycleIntentUpToTheFinalState() {
            protocol.capabilities(Set.of(IntentKind.LOGIN, IntentKind.MOVE_TO));
            final FakeScenario scenario =
                    new FakeScenario(INTERVAL, Duration.ZERO).finalState(LoadBotState.GAME_CONNECT);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(SETTINGS, scenario, null))
                    .withMessageContaining(IntentKind.SELECT_SERVER.name());
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenTheFinalStateIsBeyondTheProtocolsLastState() {
            protocol.lastState(LoadBotState.GAME_CONNECT);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), null))
                    .withMessageContaining("Protocol fake reaches up to GAME_CONNECT")
                    .withMessageContaining("load-fake stops at IN_GAME");
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenTheFinalStateIsClosing() {
            final FakeScenario scenario = new FakeScenario(INTERVAL, Duration.ZERO).finalState(LoadBotState.CLOSING);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(SETTINGS, scenario, null))
                    .withMessageContaining(LoadBotState.CLOSING.name());
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenTheMeasuredRampIsEmpty() {
            final RunSettings settings = SETTINGS.toBuilder()
                    .injection(
                            SETTINGS.injection().toBuilder().ramp(Duration.ZERO).build())
                    .build();
            final FakeScenario scenario = new FakeScenario(INTERVAL, Duration.ZERO).measuredPhase(MeasuredPhase.RAMP);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(settings, scenario, null))
                    .withMessageContaining("positive ramp");
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenSettingsNameAnotherScenario() {
            final RunSettings settings =
                    SETTINGS.toBuilder().scenario("load-roam").build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(settings, new FakeScenario(INTERVAL, Duration.ZERO), null));
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenTheProtocolCannotCarryAnIdentity() {
            protocol.rejectIdentity(index -> index == 5);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), null))
                    .withMessageContaining("Protocol fake cannot carry load bot 5")
                    .withMessageContaining("load5");
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeStart_whenPrometheusUrlIsSetWithoutMeterWindow() {
            final RunSettings settings = SETTINGS.toBuilder()
                    .prometheusUrl(URI.create("http://localhost:9014/actuator/prometheus"))
                    .build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> execute(settings, new FakeScenario(INTERVAL, Duration.ZERO), null));
        }
    }

    @Nested
    class Summary {
        @Test
        void execute_shouldReturnValidSummary_whenLoadBotsActOnTime() throws InterruptedException {
            // A 15.6 ms Windows timer tick alone can exceed the 10 ms lag limit; the limit has a test of its own.
            final RunSettings settings = SETTINGS.toBuilder()
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();

            final RunSummary actual = execute(settings, new FakeScenario(INTERVAL, Duration.ofMillis(2)), null);

            assertThat(actual.validity())
                    .as("lag p99 %s ms", actual.generatorLagP99Millis())
                    .isEqualTo(RunValidity.VALID);
            assertThat(actual.invalidReasons()).isEmpty();
            assertThat(actual.runId()).isEqualTo("load-fake-20261021-081530-42");
            assertThat(actual.startedAt()).isEqualTo(STARTED_AT);
            assertThat(actual.server()).isNull();
            assertThat(actual.stoppedShare()).isZero();
            assertThat(actual.reachedFinalState()).isEqualTo(8);
            assertThat(actual.timeoutShare()).isZero();
            assertThat(actual.rejectedShare()).isZero();
            assertThat(actual.measuredPhase()).isEqualTo(MeasuredPhase.STEADY);
            assertThat(actual.finalState()).isEqualTo(LoadBotState.IN_GAME);
            assertThat(actual.steps()).as("the lifecycle ran in the ramp").isEmpty();
            final IntentStats moves = actual.intents().get(IntentKind.MOVE_TO);
            assertThat(moves.sent()).isBetween(300L, 340L);
            assertThat(moves.acknowledged()).isEqualTo(moves.sent());
            assertThat(protocol.sessions()).hasSize(8).noneMatch(FakeProtocol.Session::open);
        }

        @Test
        void execute_shouldReportLifecycleStepsAndEndEarly_whenLoadBotsCloseOnAFinalStateInTheRamp()
                throws InterruptedException {
            // Only the intents up to the final state: the run's capability check needs no more.
            protocol.capabilities(Set.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER, IntentKind.MOVE_TO));
            final RunSettings settings = SETTINGS.toBuilder()
                    .injection(SETTINGS.injection().toBuilder()
                            // Wide enough that a 15.6 ms Windows timer tick never pushes a step out of the ramp.
                            .ramp(Duration.ofSeconds(1))
                            .steady(Duration.ofSeconds(30))
                            .build())
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();
            final FakeScenario scenario = new FakeScenario(INTERVAL, Duration.ZERO)
                    .measuredPhase(MeasuredPhase.RAMP)
                    .finalState(LoadBotState.GAME_CONNECT);
            final long startedAt = System.nanoTime();

            final RunSummary actual = execute(settings, scenario, null);

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .as("done once every load bot closed, long before the steady window ends")
                    .isLessThan(Duration.ofSeconds(10));
            assertThat(actual.validity())
                    .as("lag p99 %s ms", actual.generatorLagP99Millis())
                    .isEqualTo(RunValidity.VALID);
            assertThat(actual.measuredPhase()).isEqualTo(MeasuredPhase.RAMP);
            assertThat(actual.stoppedShare()).isZero();
            assertThat(actual.reachedFinalState()).isEqualTo(8);
            assertThat(actual.timeoutShare()).isZero();
            assertThat(actual.finalState()).isEqualTo(LoadBotState.GAME_CONNECT);
            assertThat(actual.steps())
                    .as("the states left on the way to the final one")
                    .containsOnlyKeys(LoadBotState.LOGIN_CONNECT, LoadBotState.LOGIN_AUTH, LoadBotState.SERVER_LIST)
                    .extractingFromEntries(entry -> entry.getValue().count())
                    .containsOnly(8L);
            assertThat(actual.intents()).containsOnlyKeys(IntentKind.LOGIN, IntentKind.SELECT_SERVER);
            assertThat(protocol.sessions()).hasSize(8).noneMatch(FakeProtocol.Session::open);
        }

        @Test
        void execute_shouldMarkRunInvalid_whenGeneratorLags() throws InterruptedException {
            final FakeScenario blocking = new FakeScenario(INTERVAL, Duration.ZERO, LoadRunTest::sleepTwentyMillis);

            final RunSummary actual = execute(SETTINGS, blocking, null);

            assertThat(actual.validity()).isEqualTo(RunValidity.INVALID);
            assertThat(actual.invalidReasons()).contains(InvalidReason.GENERATOR_LAG);
            assertThat(actual.generatorLagP99Millis()).isGreaterThan(10.0);
        }

        @Test
        void execute_shouldMarkRunInvalid_whenLoadBotsStopOnErrors() throws InterruptedException {
            protocol.failOnEnter(index -> index % 4 == 0);
            final RunSettings settings = SETTINGS.toBuilder()
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();

            final RunSummary actual = execute(settings, new FakeScenario(INTERVAL, Duration.ZERO), null);

            assertThat(actual.validity()).isEqualTo(RunValidity.INVALID);
            assertThat(actual.invalidReasons()).containsExactly(InvalidReason.LOAD_BOTS_STOPPED);
            assertThat(actual.stoppedShare()).isEqualTo(0.25);
            assertThat(actual.reachedFinalState())
                    .as("the load bots that entered the game")
                    .isEqualTo(6);
        }

        @Test
        void execute_shouldReportRejectedShare_whenServerRejectsIntents() throws InterruptedException {
            protocol.reject(20);

            final RunSummary actual = execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), null);

            final IntentStats moves = actual.intents().get(IntentKind.MOVE_TO);
            assertThat(moves.errors()).isEqualTo(8);
            assertThat(actual.rejectedShare()).isEqualTo((double) moves.errors() / moves.sent());
            assertThat(actual.timeoutShare()).isZero();
        }

        @Test
        void execute_shouldThrowTheOriginalFailure_whenAnIdentityHasAnotherIndex() {
            final IntFunction<LoadBotIdentity> shifted = index -> IDENTITIES.apply(index + 1);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> run.execute(
                            SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), protocol, shifted, latencies, null))
                    .withMessageContaining("has index 1")
                    .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldReadServerMetersOverTheSteadyWindow_whenPrometheusUrlIsSet() throws InterruptedException {
            final RunSettings settings = SETTINGS.toBuilder()
                    .prometheusUrl(URI.create("http://localhost:9014/actuator/prometheus"))
                    .build();
            final RecordingMeterWindow meters = new RecordingMeterWindow();

            final RunSummary actual = execute(settings, new FakeScenario(INTERVAL, Duration.ZERO), meters);

            assertThat(actual.server()).isSameAs(RecordingMeterWindow.STATS);
            assertThat(meters.starts).hasValue(1);
            assertThat(meters.samples).as("one per interval inside the window").hasValue(3);
            assertThat(meters.finishes).hasValue(1);
        }

        @Test
        void execute_shouldReadServerMetersUntilEveryLoadBotArrived_whenTheRampIsMeasured()
                throws InterruptedException {
            protocol.capabilities(Set.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER, IntentKind.MOVE_TO))
                    .connectDelay(Duration.ofMillis(300));
            final RunSettings settings = SETTINGS.toBuilder()
                    .injection(SETTINGS.injection().toBuilder()
                            .ramp(Duration.ofMillis(200))
                            .steady(Duration.ofSeconds(30))
                            .build())
                    .prometheusUrl(URI.create("http://localhost:9014/actuator/prometheus"))
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();
            final FakeScenario scenario = new FakeScenario(INTERVAL, Duration.ZERO)
                    .measuredPhase(MeasuredPhase.RAMP)
                    .finalState(LoadBotState.GAME_CONNECT);
            final RecordingMeterWindow meters = new RecordingMeterWindow();
            final long startedAt = System.nanoTime();

            final RunSummary actual = execute(settings, scenario, meters);

            final Duration finishedAfter = Duration.ofNanos(meters.finishedAt.get() - startedAt);
            assertThat(finishedAfter)
                    .as("past the ramp: the last load bot connects 300 ms after its start at 175 ms")
                    .isGreaterThanOrEqualTo(Duration.ofMillis(475))
                    .isLessThan(Duration.ofSeconds(10));
            assertThat(meters.starts).hasValue(1);
            assertThat(meters.finishes).hasValue(1);
            assertThat(actual.reachedFinalState()).isEqualTo(8);
            assertThat(actual.steps().get(LoadBotState.LOGIN_CONNECT).count())
                    .as("every arrival, though it ended after the ramp")
                    .isEqualTo(8);
        }
    }

    private static void sleepTwentyMillis() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RecordingMeterWindow implements ServerMeterWindow {
        static final ServerStats STATS = ServerStats.builder().loops(Map.of()).build();

        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger samples = new AtomicInteger();
        private final AtomicInteger finishes = new AtomicInteger();
        private final AtomicLong finishedAt = new AtomicLong();

        @Override
        public void start() {
            starts.incrementAndGet();
        }

        @Override
        public void sample() {
            samples.incrementAndGet();
        }

        @Override
        public ServerStats finish() {
            finishedAt.set(System.nanoTime());
            finishes.incrementAndGet();
            return STATS;
        }
    }
}
