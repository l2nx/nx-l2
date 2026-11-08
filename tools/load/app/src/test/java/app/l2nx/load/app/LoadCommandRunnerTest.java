/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import app.l2nx.load.app.database.DatabaseCommands;
import app.l2nx.load.app.memory.InMemoryProtocol;
import app.l2nx.load.app.scenario.LoginStormLoadScenario;
import app.l2nx.load.app.scenario.RoamLoadScenario;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.load.protocol.p267.P267Protocol;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.Capacity;
import app.l2nx.load.report.model.CapacityLimit;
import app.l2nx.load.report.model.InputStats;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.LevelStats;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.StepStats;
import app.l2nx.load.scenario.model.MeasuredPhase;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;

// Each test boots the application, and Boot sets up logging for the whole JVM: the boots must not overlap.
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("spring-boot-logging")
class LoadCommandRunnerTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-21T08:00:00Z");

    private final ReportWriter reportWriter = new ReportWriter();

    @TempDir
    Path root;

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class Run {
        @Test
        void run_shouldWriteACompleteClientReport_whenRoamingOnTheInMemoryProtocol() throws IOException {
            final int exitCode = execute(
                    LoadCommandRunner.RUN,
                    "--l2nx.load.scenario=" + RoamLoadScenario.ID,
                    "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                    "--l2nx.load.bots=20",
                    "--l2nx.load.ramp=200ms",
                    "--l2nx.load.warm-up=300ms",
                    // Longer than the action interval with its jitter, so every load bot acts in the window.
                    "--l2nx.load.steady=3s",
                    "--l2nx.load.ramp-down=200ms",
                    "--l2nx.load.seed=42",
                    "--l2nx.load.event-loop-threads=2",
                    // A 15.6 ms Windows timer tick alone can exceed the default limit, with about 30 samples p99 is the
                    // max.
                    "--l2nx.load.max-generator-lag-p99=100ms",
                    "--l2nx.load.output-dir=" + root);

            final List<Path> summaries = summaries(root);
            assertThat(exitCode).isZero();
            assertThat(summaries).hasSize(1);
            final RunSummary actual = reportWriter.readSummary(summaries.getFirst());
            assertThat(actual.validity())
                    .as("lag p99 %s ms", actual.generatorLagP99Millis())
                    .isEqualTo(RunValidity.VALID);
            assertThat(actual.reachedFinalState()).isEqualTo(20);
            assertThat(actual.stoppedShare()).isZero();
            assertThat(actual.timeoutShare()).isZero();
            assertThat(actual.rejectedShare()).isZero();
            assertThat(actual.serverWindow())
                    .as("no server behind the in-memory protocol")
                    .isNull();
            assertThat(actual.server()).isNull();
            assertThat(actual.intents().get(IntentKind.MOVE_TO).acknowledged()).isPositive();
            assertThat(actual.intents().get(IntentKind.SAY).acknowledged()).isPositive();
            assertThat(summaries.getFirst().resolveSibling(ReportWriter.histogramFile(IntentKind.MOVE_TO)))
                    .exists();
        }

        @Test
        void run_shouldReportEveryLevelAndTheCapacity_whenLevelsAreGiven() throws IOException {
            final int exitCode = execute(
                    LoadCommandRunner.RUN,
                    "--l2nx.load.scenario=" + RoamLoadScenario.ID,
                    "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                    "--l2nx.load.bots=4",
                    "--l2nx.load.ramp=200ms",
                    "--l2nx.load.warm-up=300ms",
                    "--l2nx.load.levels.increment=4",
                    "--l2nx.load.levels.count=2",
                    "--l2nx.load.levels.duration=2s",
                    "--l2nx.load.levels.ramp=100ms",
                    "--l2nx.load.ramp-down=200ms",
                    "--l2nx.load.seed=42",
                    "--l2nx.load.event-loop-threads=2",
                    "--l2nx.load.max-generator-lag-p99=100ms",
                    "--l2nx.load.output-dir=" + root);

            final List<Path> summaries = summaries(root);
            assertThat(exitCode).isZero();
            final RunSummary actual = reportWriter.readSummary(summaries.getFirst());
            assertThat(actual.injection().levels().increment()).isEqualTo(4);
            assertThat(actual.levels()).extracting(LevelStats::bots).containsExactly(4, 8);
            assertThat(actual.capacity())
                    .as("no server behind the in-memory protocol")
                    .extracting(Capacity::bots, Capacity::limitedBy)
                    .containsExactly(null, CapacityLimit.SERVER_METERS);
        }

        @Test
        void run_shouldFailBeforeAnyLoadBotStarts_whenPrometheusIsNotReady() throws IOException {
            try (Socket closed = closedPort()) {
                final int closedPort = closed.getLocalPort();

                assertThatThrownBy(() -> execute(
                                LoadCommandRunner.RUN,
                                "--l2nx.load.scenario=" + LoginStormLoadScenario.ID,
                                "--l2nx.load.login-storm.final-state=" + LoadBotState.GAME_CONNECT,
                                "--l2nx.load.protocol=" + P267Protocol.ID,
                                "--l2nx.load.bots=1",
                                "--l2nx.load.prometheus.url=http://127.0.0.1:" + closedPort,
                                "--l2nx.load.p267.login-port=" + closedPort,
                                "--l2nx.load.output-dir=" + root))
                        .hasStackTraceContaining("Prometheus at http://127.0.0.1:" + closedPort + " is not ready");
            }
            assertThat(summaries(root)).isEmpty();
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class LoginStorm {
        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"SERVER_LIST", "GAME_CONNECT", "IN_GAME"})
        void run_shouldTimeTheStepsUpToTheFinalState_whenTheSettingsNameIt(LoadBotState finalState) throws IOException {
            final int exitCode = execute(
                    LoadCommandRunner.RUN,
                    "--l2nx.load.scenario=" + LoginStormLoadScenario.ID,
                    "--l2nx.load.login-storm.final-state=" + finalState,
                    "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                    "--l2nx.load.bots=10",
                    "--l2nx.load.ramp=300ms",
                    "--l2nx.load.warm-up=0s",
                    "--l2nx.load.steady=0s",
                    "--l2nx.load.ramp-down=100ms",
                    "--l2nx.load.event-loop-threads=1",
                    "--l2nx.load.output-dir=" + root);

            final List<Path> summaries = summaries(root);
            assertThat(exitCode).isZero();
            assertThat(summaries).hasSize(1);
            final RunSummary actual = reportWriter.readSummary(summaries.getFirst());
            assertThat(actual.finalState()).isEqualTo(finalState);
            assertThat(actual.steps())
                    .as("the states left on the way, not the final one")
                    .containsKey(LoadBotState.values()[finalState.ordinal() - 1]);
            assertThat(actual.steps().keySet()).allMatch(state -> state.compareTo(finalState) < 0);
            assertThat(actual.stoppedShare()).isZero();
        }

        @Test
        void run_shouldFailBeforeAnyRun_whenTheFinalStateEndsTheSession() {
            assertThatThrownBy(() -> execute(
                            LoadCommandRunner.RUN,
                            "--l2nx.load.scenario=" + LoginStormLoadScenario.ID,
                            "--l2nx.load.login-storm.final-state=" + LoadBotState.CLOSING,
                            "--l2nx.load.output-dir=" + root))
                    .hasStackTraceContaining("l2nx.load.login-storm.final-state");
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class Seed {
        @Test
        void seed_shouldSetUpEveryLoadBotWithoutAReport() throws IOException {
            final int exitCode = execute(
                    LoadCommandRunner.SEED,
                    "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                    "--l2nx.load.bots=10",
                    "--l2nx.load.ramp=200ms",
                    "--l2nx.load.warm-up=0s",
                    "--l2nx.load.steady=5s",
                    "--l2nx.load.ramp-down=0s",
                    "--l2nx.load.event-loop-threads=1",
                    "--l2nx.load.output-dir=" + root);

            assertThat(exitCode).as("every load bot was set up and logged out").isZero();
            assertThat(summaries(root))
                    .as("nothing a gate could take for a run")
                    .isEmpty();
        }

        @Test
        void seed_shouldFail_whenALoadBotIsNotSetUp() {
            // Steps slower than the whole run: the run closes every load bot before its login is answered.
            assertThatThrownBy(() -> execute(
                            LoadCommandRunner.SEED,
                            "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                            "--l2nx.load.bots=2",
                            "--l2nx.load.ramp=100ms",
                            "--l2nx.load.warm-up=0s",
                            "--l2nx.load.steady=0s",
                            "--l2nx.load.ramp-down=0s",
                            "--l2nx.load.intent-timeout=100ms",
                            "--l2nx.load.in-memory.step-delay=10s",
                            "--l2nx.load.event-loop-threads=1",
                            "--l2nx.load.output-dir=" + root))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("2 of 2 load bots were not seeded with ARENA");
        }

        @Test
        void seed_shouldFailBeforeAnyLoadBotStarts_whenAnAccountDoesNotFitTheP267Login() throws IOException {
            assertThatThrownBy(() -> execute(
                            LoadCommandRunner.SEED,
                            "--l2nx.load.protocol=" + P267Protocol.ID,
                            "--l2nx.load.bots=1",
                            "--l2nx.load.accounts.prefix=loadbotsxxxxxx",
                            "--l2nx.load.output-dir=" + root))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Protocol " + P267Protocol.ID + " cannot carry load bot 0")
                    .hasMessageContaining("loadbotsxxxxxx0");
            assertThat(summaries(root)).isEmpty();
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class Protocol {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {LoadCommandRunner.RUN, LoadCommandRunner.SEED})
        void run_shouldNameEveryProtocol_whenTheCommandNeedsOneAndNoneIsSet(String command) {
            assertThatThrownBy(() -> execute(
                            command, "--l2nx.load.scenario=" + RoamLoadScenario.ID, "--l2nx.load.output-dir=" + root))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("The " + command + " needs l2nx.load.protocol")
                    .hasMessageContaining(
                            List.of(InMemoryProtocol.ID, P267Protocol.ID).toString());
        }

        @Test
        void run_shouldFailAtStart_whenTheP267ServerIdTakesMoreThanOneByte() {
            assertThatThrownBy(() -> execute(LoadCommandRunner.GATE, "--l2nx.load.p267.server-id=256"))
                    .hasStackTraceContaining("p267.serverId");
        }

        @Test
        void run_shouldBuildOnlyTheSelectedProtocol() throws Exception {
            final ClientProtocolFactory unselected = ClientProtocolFactory.builder()
                    .id(P267Protocol.ID)
                    .create(() -> {
                        throw new AssertionError("The unselected protocol was built");
                    })
                    .build();
            final LoadProperties properties = withoutPrometheus(InMemoryProtocol.ID);
            final ClientProtocolFactory selected = ClientProtocolFactory.builder()
                    .id(InMemoryProtocol.ID)
                    .create(() -> new InMemoryProtocol(properties))
                    .build();
            final LoadCommandRunner runner = new LoadCommandRunner(
                    properties, List.of(new RoamLoadScenario()), List.of(unselected, selected), null, null);

            runner.run(new DefaultApplicationArguments(LoadCommandRunner.RUN));

            assertThat(summaries(root)).hasSize(1);
        }

        @Test
        void run_shouldReadNoServerMeters_whenTheProtocolHasNoServer() throws Exception {
            final LoadProperties properties = withoutPrometheus(StandIn.ID);
            final ClientProtocolFactory standIn = ClientProtocolFactory.builder()
                    .id(StandIn.ID)
                    .create(() -> new StandIn(new InMemoryProtocol(properties)))
                    .build();
            final LoadCommandRunner runner =
                    new LoadCommandRunner(properties, List.of(new RoamLoadScenario()), List.of(standIn), null, null);

            runner.run(new DefaultApplicationArguments(LoadCommandRunner.RUN));

            final List<Path> summaries = summaries(root);
            assertThat(summaries).hasSize(1);
            assertThat(reportWriter.readSummary(summaries.getFirst()).serverWindow())
                    .isNull();
        }

        private LoadProperties withoutPrometheus(String protocol) {
            return LoadProperties.builder()
                    .scenario(RoamLoadScenario.ID)
                    .protocol(protocol)
                    .bots(2)
                    .ramp(Duration.ofMillis(50))
                    .warmUp(Duration.ZERO)
                    .steady(Duration.ofMillis(200))
                    .rampDown(Duration.ZERO)
                    .eventLoopThreads(1)
                    .intentTimeout(Duration.ofSeconds(1))
                    .lifecycleTimeout(Duration.ofSeconds(30))
                    .maxGeneratorLagP99(Duration.ofMillis(100))
                    .outputDir(root)
                    .gate(LoadProperties.Gate.builder().build())
                    .levels(LoadProperties.Levels.builder().ramp(Duration.ZERO).build())
                    .accounts(LoadProperties.Accounts.builder()
                            .prefix("load")
                            .password("load")
                            .build())
                    .inMemory(LoadProperties.InMemory.builder()
                            .stepDelay(Duration.ofMillis(1))
                            .ackLatency(Duration.ofMillis(1))
                            .build())
                    .build();
        }
    }

    /** The in-memory protocol under another id: the runner must ask the protocol, not compare ids. */
    private record StandIn(InMemoryProtocol delegate) implements ClientProtocol {
        static final String ID = "stand-in";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public Set<IntentKind> capabilities() {
            return delegate.capabilities();
        }

        @Override
        public LoadBotState lastState() {
            return delegate.lastState();
        }

        @Override
        public boolean hasServer() {
            return false;
        }

        @Override
        public IoHandlerFactory ioHandlerFactory() {
            return delegate.ioHandlerFactory();
        }

        @Override
        public ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener listener) {
            return delegate.open(identity, loop, listener);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class Commands {
        @Test
        void run_shouldNameEveryCommand_whenTheCommandIsUnknown() {
            assertThatThrownBy(() -> execute("deploy"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(LoadCommandRunner.COMMANDS.toString());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {LoadCommandRunner.DB_CREATE, LoadCommandRunner.DB_SNAPSHOT, LoadCommandRunner.DB_RESET})
        void run_shouldRunTheDatabaseCommandOfItsName(String command) throws Exception {
            final DatabaseCommands database = mock(DatabaseCommands.class);
            final LoadCommandRunner runner =
                    new LoadCommandRunner(LoadProperties.builder().build(), List.of(), List.of(), database, null);

            runner.run(new DefaultApplicationArguments(command));

            switch (command) {
                case LoadCommandRunner.DB_CREATE -> verify(database).create();
                case LoadCommandRunner.DB_SNAPSHOT -> verify(database).snapshot();
                case LoadCommandRunner.DB_RESET -> verify(database).reset();
                default -> throw new AssertionError("Not a database command: " + command);
            }
            verifyNoMoreInteractions(database);
        }
    }

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class Gate {
        @ParameterizedTest(name = "input p99 {0} ms against 1 ms -> exit code {1}")
        @CsvSource({"1.4, 0", "1.6, " + LoadCommandRunner.GATE_FAILED})
        void gate_shouldExitWithTheVerdict_whenGivenFiveReportsAndABaselineOfFive(double inputP99Millis, int expected)
                throws IOException {
            final Path baseline = root.resolve("baseline");
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 5; run++) {
                reportWriter.write(baseline, summary(run, 1.0), Map.of(), Map.of(), Map.of());
                reportWriter.write(reports, summary(run + 5, inputP99Millis), Map.of(), Map.of(), Map.of());
            }

            // No l2nx.load.protocol: the gate connects no load bot.
            final int actual = execute(
                    LoadCommandRunner.GATE,
                    "--" + LoadCommandRunner.BASELINE + "=" + baseline,
                    "--" + LoadCommandRunner.REPORTS + "=" + reports);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void gate_shouldTakeTheFloorFromTheSettings_whenOneIsGiven() throws IOException {
            final Path baseline = root.resolve("baseline");
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 5; run++) {
                reportWriter.write(baseline, summary(run, 1.0), Map.of(), Map.of(), Map.of());
                reportWriter.write(reports, summary(run + 5, 1.4), Map.of(), Map.of(), Map.of());
            }

            final int actual = execute(
                    LoadCommandRunner.GATE,
                    "--" + LoadCommandRunner.BASELINE + "=" + baseline,
                    "--" + LoadCommandRunner.REPORTS + "=" + reports,
                    "--l2nx.load.gate.input-p99-floor=100us");

            assertThat(actual).isEqualTo(LoadCommandRunner.GATE_FAILED);
        }

        @ParameterizedTest(name = "step p99 105 ms against 100 ms, ratio {0}, floor {1} -> exit code {2}")
        @CsvSource({",, 0", "1.01, 4ms, " + LoadCommandRunner.GATE_FAILED, "1.01, 6ms, 0"})
        void gate_shouldGateTheLifecycleStepsWithTheSettings_whenTheStepP99Grows(
                Double ratio, String floor, int expected) throws IOException {
            final Path baseline = root.resolve("baseline");
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 5; run++) {
                reportWriter.write(baseline, withLoginStep(summary(run, 1.0), 100.0), Map.of(), Map.of(), Map.of());
                reportWriter.write(reports, withLoginStep(summary(run + 5, 1.0), 105.0), Map.of(), Map.of(), Map.of());
            }
            final List<String> args = new ArrayList<>(List.of(
                    LoadCommandRunner.GATE,
                    "--" + LoadCommandRunner.BASELINE + "=" + baseline,
                    "--" + LoadCommandRunner.REPORTS + "=" + reports));
            if (ratio != null) {
                args.add("--l2nx.load.gate.max-step-p99-ratio=" + ratio);
                args.add("--l2nx.load.gate.step-p99-floor=" + floor);
            }

            final int actual = execute(args.toArray(String[]::new));

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void gate_shouldNotCountABaselineFileAsARun_whenTheReportsDirectoryHoldsBoth() throws IOException {
            final Path reports = root.resolve("reports");
            final StringBuilder baseline = new StringBuilder();
            for (int run = 1; run <= 5; run++) {
                final Path directory = reportWriter.write(reports, summary(run, 1.0), Map.of(), Map.of(), Map.of());
                baseline.append(directory.resolve(ReportWriter.SUMMARY_FILE)).append(',');
                reportWriter.write(reports, summary(run + 5, 10.0), Map.of(), Map.of(), Map.of());
            }

            final int actual = execute(
                    LoadCommandRunner.GATE,
                    "--" + LoadCommandRunner.BASELINE + "=" + baseline,
                    "--" + LoadCommandRunner.REPORTS + "=" + reports);

            assertThat(actual)
                    .as("the slow runs alone are measured against the baseline")
                    .isEqualTo(LoadCommandRunner.GATE_FAILED);
        }

        @Test
        void gate_shouldPassOnTheAbsoluteCriteria_whenNoBaselineIsGiven() throws IOException {
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 5; run++) {
                reportWriter.write(reports, summary(run, 10.0), Map.of(), Map.of(), Map.of());
            }

            final int actual = execute(LoadCommandRunner.GATE, "--" + LoadCommandRunner.REPORTS + "=" + reports);

            assertThat(actual).isZero();
        }

        @Test
        void gate_shouldFail_whenTheBaselineWasTakenWithReportVersionOne() throws IOException {
            final Path baseline = root.resolve("baseline");
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 5; run++) {
                reportWriter.write(
                        baseline, summary(run, 1.0).toBuilder().reportVersion(1).build(), Map.of(), Map.of(), Map.of());
                reportWriter.write(reports, summary(run + 5, 1.0), Map.of(), Map.of(), Map.of());
            }

            final int actual = execute(
                    LoadCommandRunner.GATE,
                    "--" + LoadCommandRunner.BASELINE + "=" + baseline,
                    "--" + LoadCommandRunner.REPORTS + "=" + reports);

            assertThat(actual).isEqualTo(LoadCommandRunner.GATE_FAILED);
        }

        @Test
        void gate_shouldFail_whenTheBaselineIsGivenButHoldsNoReport() throws IOException {
            final Path reports = root.resolve("reports");
            reportWriter.write(reports, summary(1, 10.0), Map.of(), Map.of(), Map.of());
            final Path empty = Files.createDirectory(root.resolve("empty"));

            assertThatThrownBy(() -> execute(
                            LoadCommandRunner.GATE,
                            "--" + LoadCommandRunner.BASELINE + "=" + empty,
                            "--" + LoadCommandRunner.REPORTS + "=" + reports))
                    .hasStackTraceContaining("No " + ReportWriter.SUMMARY_FILE);
        }
    }

    private static int execute(String... args) {
        return SpringApplication.exit(SpringApplication.run(LoadApplication.class, args));
    }

    /** Bound but not listening: a connect is refused, and nothing else can take the port while the socket is held. */
    private static Socket closedPort() throws IOException {
        final Socket socket = new Socket();
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        return socket;
    }

    private static List<Path> summaries(Path directory) throws IOException {
        try (Stream<Path> files =
                Files.find(directory, 2, (file, attributes) -> file.endsWith(ReportWriter.SUMMARY_FILE))) {
            return files.toList();
        }
    }

    private static RunSummary withLoginStep(RunSummary summary, double p99Millis) {
        return summary.toBuilder()
                .steps(Map.of(
                        LoadBotState.LOGIN_AUTH,
                        StepStats.builder()
                                .count(500)
                                .p50Millis(p99Millis / 2)
                                .p99Millis(p99Millis)
                                .p999Millis(p99Millis)
                                .maxMillis(p99Millis)
                                .build()))
                .build();
    }

    private static RunSummary summary(int run, double inputP99Millis) {
        return RunSummary.builder()
                .reportVersion(RunSummary.REPORT_VERSION)
                .runId(RoamLoadScenario.ID + "-" + run)
                .scenario(RoamLoadScenario.ID)
                .protocol(InMemoryProtocol.ID)
                .seed(run)
                .injection(InjectionProfile.builder()
                        .bots(500)
                        .ramp(Duration.ofSeconds(30))
                        .warmUp(Duration.ofSeconds(30))
                        .steady(Duration.ofMinutes(5))
                        .rampDown(Duration.ofSeconds(10))
                        .build())
                .measuredPhase(MeasuredPhase.STEADY)
                .finalState(LoadBotState.IN_GAME)
                .startedAt(STARTED_AT.plus(Duration.ofMinutes(10L * run)))
                .validity(RunValidity.VALID)
                .invalidReasons(List.of())
                .intents(Map.of(
                        IntentKind.MOVE_TO,
                        IntentStats.builder()
                                .sent(10_000)
                                .acknowledged(10_000)
                                .p50Millis(4.0)
                                .p99Millis(40.0)
                                .p999Millis(80.0)
                                .maxMillis(120.0)
                                .build()))
                .steps(Map.of())
                .generatorLagP99Millis(1.0)
                .server(ServerStats.builder()
                        .loops(Map.of(
                                "world",
                                LoopStats.builder()
                                        .passes(6_000)
                                        .passP50Millis(5.0)
                                        .passP99Millis(20.0)
                                        .passMaxMillis(40.0)
                                        .build()))
                        .inputs(Map.of(
                                "app.l2nx.highfive.movement.command.MoveTo",
                                InputStats.builder()
                                        .count(10_000)
                                        .p50Millis(inputP99Millis / 4)
                                        .p99Millis(inputP99Millis)
                                        .maxMillis(inputP99Millis * 2)
                                        .build()))
                        .build())
                .build();
    }
}
