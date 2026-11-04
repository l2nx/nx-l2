/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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
import app.l2nx.load.report.model.ServerWindow;
import app.l2nx.load.scenario.FakeScenario;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final Instant SERVER_STARTED_AT = Instant.parse("2026-10-21T08:10:00Z");
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
        void execute_shouldFailBeforeAnyLoadBotStarts_whenTheRunIdIsRefused() {
            final List<String> claimed = new CopyOnWriteArrayList<>();

            assertThatIllegalStateException()
                    .isThrownBy(() -> run.execute(
                            SETTINGS,
                            new FakeScenario(INTERVAL, Duration.ZERO),
                            protocol,
                            IDENTITIES,
                            latencies,
                            null,
                            runId -> {
                                claimed.add(runId);
                                throw new IllegalStateException("The report directory already exists");
                            }))
                    .withMessageContaining("already exists");
            assertThat(claimed).containsExactly("load-fake-20261021-081530-42");
            assertThat(protocol.sessions()).isEmpty();
        }

        @Test
        void execute_shouldFailBeforeAnyLoadBotStarts_whenServerMetersAreNotReady() {
            final RecordingMeterWindow meters = new RecordingMeterWindow().notReady();

            assertThatIllegalStateException()
                    .isThrownBy(() -> execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), meters))
                    .withMessageContaining("not ready");
            assertThat(meters.calls).containsExactly("checkReady");
            assertThat(protocol.sessions()).isEmpty();
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
            assertThat(actual.notAtFinalStateShare()).isZero();
            assertThat(actual.timeoutShare()).isZero();
            assertThat(actual.rejectedShare()).isZero();
            assertThat(actual.intentTimeout()).isEqualTo(SETTINGS.intentTimeout());
            assertThat(actual.lifecycleTimeout()).isEqualTo(SETTINGS.lifecycleTimeout());
            assertThat(actual.environment().logicalCores()).isPositive();
            assertThat(actual.environment().jvm()).isNotBlank();
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
        void execute_shouldStayValidAndReportTheShares_whenLoadBotsStopOnErrors() throws InterruptedException {
            protocol.failOnEnter(index -> index % 4 == 0);
            final RunSettings settings = SETTINGS.toBuilder()
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();

            final RunSummary actual = execute(settings, new FakeScenario(INTERVAL, Duration.ZERO), null);

            assertThat(actual.validity())
                    .as("a server that drops load bots fails the gate, it does not invalidate the generator's run")
                    .isEqualTo(RunValidity.VALID);
            assertThat(actual.invalidReasons()).isEmpty();
            assertThat(actual.stoppedShare()).isEqualTo(0.25);
            assertThat(actual.reachedFinalState())
                    .as("the load bots that entered the game")
                    .isEqualTo(6);
            assertThat(actual.notAtFinalStateShare()).isEqualTo(0.25);
        }

        @Test
        void execute_shouldCountALoadBotStuckBeforeItsFinalState_whenTheServerNeverAnswers()
                throws InterruptedException {
            protocol.neverAcknowledge(IntentKind.LOGIN);
            final RunSettings settings = SETTINGS.toBuilder()
                    .lifecycleTimeout(Duration.ofMillis(250))
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();

            final RunSummary actual = execute(settings, new FakeScenario(INTERVAL, Duration.ZERO), null);

            assertThat(actual.validity()).isEqualTo(RunValidity.VALID);
            assertThat(actual.stoppedShare()).isEqualTo(1);
            assertThat(actual.notAtFinalStateShare()).isEqualTo(1);
            assertThat(actual.timeoutShare())
                    .as("every login timed out in the ramp, before the steady window")
                    .isEqualTo(1);
            assertThat(actual.steps().get(LoadBotState.LOGIN_AUTH).timeouts()).isEqualTo(8);
        }

        @Test
        void execute_shouldRecordTheServerStartTimes_whenTheServerMetersAreRead() throws InterruptedException {
            final RunSummary actual = execute(
                    SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), new RecordingMeterWindow().unreadable());

            assertThat(actual.server()).isNull();
            assertThat(actual.serverStartedAt()).isEqualTo(Map.of("game", SERVER_STARTED_AT));
        }

        @Test
        void execute_shouldReportRejectedShare_whenServerRejectsIntents() throws InterruptedException {
            // Alternating ordinals: the share stays the same wherever the timers cut the measured phase.
            for (int ordinal = 1; ordinal < 1_000; ordinal += 2) {
                protocol.reject(ordinal);
            }

            final RunSummary actual = execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), null);

            final IntentStats moves = actual.intents().get(IntentKind.MOVE_TO);
            assertThat(moves.errors()).isPositive();
            assertThat(moves.acknowledged() + moves.errors())
                    .as("every counted intent is either acknowledged or rejected")
                    .isEqualTo(moves.sent());
            assertThat(Math.abs(moves.errors() - moves.acknowledged()))
                    .as("alternating answers differ by at most one per load bot")
                    .isLessThanOrEqualTo(SETTINGS.injection().bots());
            // Over all kinds: on a cold JVM the event loops start late, so arrival intents land in the measured phase.
            final long sent = actual.intents().values().stream()
                    .mapToLong(IntentStats::sent)
                    .sum();
            assertThat(actual.rejectedShare()).isEqualTo((double) moves.errors() / sent);
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
        void execute_shouldQueryServerMetersOverTheSteadyWindow() throws InterruptedException {
            final RecordingMeterWindow meters = new RecordingMeterWindow();
            final long startedAt = System.nanoTime();

            final RunSummary actual = execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), meters);

            assertThat(meters.calls).containsExactly("checkReady", "start", "finish");
            assertThat(Duration.ofNanos(meters.startedAt.get() - startedAt))
                    .as("not before the ramp and the warm-up are over")
                    .isGreaterThanOrEqualTo(Duration.ofMillis(100));
            assertThat(Duration.ofNanos(meters.finishedAt.get() - startedAt))
                    .as("not before the steady window is over")
                    .isGreaterThanOrEqualTo(Duration.ofMillis(500));
            final ServerWindow expected = ServerWindow.builder()
                    .start(STARTED_AT.minusMillis(1))
                    .end(STARTED_AT)
                    .rangeMillis(1)
                    .build();
            assertThat(meters.window.get()).as("a fixed wall clock").isEqualTo(expected);
            assertThat(actual.serverWindow()).isEqualTo(expected);
            assertThat(actual.server()).isSameAs(RecordingMeterWindow.STATS);
        }

        @Test
        void execute_shouldKeepTheClientResultsAndTheWindow_whenTheServerMetersCannotBeQueried()
                throws InterruptedException {
            final RecordingMeterWindow meters = new RecordingMeterWindow().unreadable();

            final RunSummary actual = execute(SETTINGS, new FakeScenario(INTERVAL, Duration.ZERO), meters);

            assertThat(actual.server()).isNull();
            assertThat(actual.serverWindow()).isEqualTo(ServerWindow.of(STARTED_AT, STARTED_AT));
            assertThat(actual.reachedFinalState()).isEqualTo(8);
            assertThat(actual.intents().get(IntentKind.MOVE_TO).sent()).isPositive();
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
            assertThat(meters.calls).containsExactly("checkReady", "start", "finish");
            assertThat(actual.reachedFinalState()).isEqualTo(8);
            assertThat(actual.steps().get(LoadBotState.LOGIN_CONNECT).count())
                    .as("every arrival, though it ended after the ramp")
                    .isEqualTo(8);
        }

        @Test
        void execute_shouldReadNoServerMetersButKeepTheWindow_whenTheRampOutlastsTheDrain()
                throws InterruptedException {
            protocol.capabilities(Set.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER, IntentKind.MOVE_TO))
                    .connectDelay(Duration.ofSeconds(30));
            final RunSettings settings = SETTINGS.toBuilder()
                    .injection(SETTINGS.injection().toBuilder()
                            .ramp(Duration.ofMillis(200))
                            .steady(Duration.ofMillis(50))
                            .build())
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .build();
            final FakeScenario scenario = new FakeScenario(INTERVAL, Duration.ZERO)
                    .measuredPhase(MeasuredPhase.RAMP)
                    .finalState(LoadBotState.GAME_CONNECT);
            final RecordingMeterWindow meters = new RecordingMeterWindow();

            final RunSummary actual = execute(settings, scenario, meters);

            assertThat(meters.calls).as("no load bot connects before the drain").containsExactly("checkReady", "start");
            assertThat(actual.server()).isNull();
            assertThat(actual.serverWindow()).isEqualTo(ServerWindow.of(STARTED_AT, STARTED_AT));
            assertThat(actual.reachedFinalState()).isZero();
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

        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final AtomicLong startedAt = new AtomicLong();
        private final AtomicLong finishedAt = new AtomicLong();
        private final AtomicReference<ServerWindow> window = new AtomicReference<>();
        private boolean ready = true;
        private boolean readable = true;

        RecordingMeterWindow notReady() {
            ready = false;
            return this;
        }

        RecordingMeterWindow unreadable() {
            readable = false;
            return this;
        }

        @Override
        public Map<String, Instant> checkReady() {
            calls.add("checkReady");
            if (!ready) {
                throw new IllegalStateException("Prometheus is not ready");
            }
            return Map.of("game", SERVER_STARTED_AT);
        }

        @Override
        public void start() {
            startedAt.set(System.nanoTime());
            calls.add("start");
        }

        @Override
        public ServerStats finish(ServerWindow finished) {
            finishedAt.set(System.nanoTime());
            window.set(finished);
            calls.add("finish");
            return readable ? STATS : null;
        }
    }
}
