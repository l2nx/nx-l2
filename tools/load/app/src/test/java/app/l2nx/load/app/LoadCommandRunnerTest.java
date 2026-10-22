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
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.protocol.p267.P267Protocol;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;

// Each test boots the application, and Boot sets up logging for the whole JVM: the boots must not overlap.
@Execution(ExecutionMode.SAME_THREAD)
class LoadCommandRunnerTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-21T08:00:00Z");

    private final ReportWriter reportWriter = new ReportWriter();

    @TempDir
    Path root;

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class Run {
        @Test
        void run_shouldWriteAValidReport_whenRoamingOnTheInMemoryProtocol() throws IOException {
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
                    "--l2nx.load.output-dir=" + root);

            final List<Path> summaries = summaries(root);
            assertThat(exitCode).isZero();
            assertThat(summaries).hasSize(1);
            final RunSummary actual = reportWriter.readSummary(summaries.getFirst());
            assertThat(actual.validity()).isEqualTo(RunValidity.VALID);
            assertThat(actual.server()).isNull();
            assertThat(actual.intents().get(IntentKind.MOVE_TO).acknowledged()).isPositive();
            assertThat(actual.intents().get(IntentKind.SAY).acknowledged()).isPositive();
            assertThat(summaries.getFirst().resolveSibling(ReportWriter.histogramFile(IntentKind.MOVE_TO)))
                    .exists();
        }

        @Test
        void run_shouldWriteTheClientReportWithoutServerMeters_whenThePrometheusEndpointIsUnreachable()
                throws IOException {
            final int exitCode = execute(
                    LoadCommandRunner.RUN,
                    "--l2nx.load.scenario=" + RoamLoadScenario.ID,
                    "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                    "--l2nx.load.bots=5",
                    "--l2nx.load.ramp=100ms",
                    "--l2nx.load.warm-up=100ms",
                    "--l2nx.load.steady=500ms",
                    "--l2nx.load.ramp-down=100ms",
                    "--l2nx.load.event-loop-threads=1",
                    "--l2nx.load.prometheus-url=http://127.0.0.1:" + closedPort() + "/actuator/prometheus",
                    "--l2nx.load.scrape-timeout=1s",
                    "--l2nx.load.output-dir=" + root);

            final List<Path> summaries = summaries(root);
            assertThat(exitCode).isZero();
            assertThat(summaries).hasSize(1);
            final RunSummary actual = reportWriter.readSummary(summaries.getFirst());
            assertThat(actual.server()).isNull();
            assertThat(actual.reachedFinalState()).isEqualTo(5);
            assertThat(summaries.getFirst().resolveSibling(ReportWriter.METRICS_START_FILE))
                    .doesNotExist();
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
        void seed_shouldLogEveryLoadBotInUpToTheServerListWithoutAReport() throws IOException {
            final int exitCode = execute(
                    LoadCommandRunner.SEED,
                    "--l2nx.load.protocol=" + InMemoryProtocol.ID,
                    "--l2nx.load.bots=10",
                    "--l2nx.load.ramp=200ms",
                    "--l2nx.load.warm-up=0s",
                    // Room for the last load bot to log in; the run ends once every load bot is done.
                    "--l2nx.load.steady=5s",
                    "--l2nx.load.ramp-down=0s",
                    "--l2nx.load.event-loop-threads=1",
                    "--l2nx.load.output-dir=" + root);

            assertThat(exitCode).as("every load bot reached the server list").isZero();
            assertThat(summaries(root))
                    .as("nothing a gate could take for a run")
                    .isEmpty();
        }

        @Test
        void seed_shouldFail_whenALoadBotDoesNotLogIn() {
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
                    .hasMessageContaining("2 of 2 load bots did not log in");
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
            final LoadProperties properties = LoadProperties.builder()
                    .scenario(RoamLoadScenario.ID)
                    .protocol(InMemoryProtocol.ID)
                    .bots(2)
                    .ramp(Duration.ofMillis(50))
                    .warmUp(Duration.ZERO)
                    .steady(Duration.ofMillis(200))
                    .rampDown(Duration.ZERO)
                    .eventLoopThreads(1)
                    .intentTimeout(Duration.ofSeconds(1))
                    .scrapeTimeout(Duration.ofSeconds(1))
                    .outputDir(root)
                    .accounts(LoadProperties.Accounts.builder()
                            .prefix("load")
                            .password("load")
                            .build())
                    .inMemory(LoadProperties.InMemory.builder()
                            .stepDelay(Duration.ofMillis(1))
                            .ackLatency(Duration.ofMillis(1))
                            .build())
                    .build();
            final ClientProtocolFactory selected = ClientProtocolFactory.builder()
                    .id(InMemoryProtocol.ID)
                    .create(() -> new InMemoryProtocol(properties))
                    .build();
            final LoadCommandRunner runner = new LoadCommandRunner(
                    properties, List.of(new RoamLoadScenario()), List.of(unselected, selected), null);

            runner.run(new DefaultApplicationArguments(LoadCommandRunner.RUN));

            assertThat(summaries(root)).hasSize(1);
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
                    new LoadCommandRunner(LoadProperties.builder().build(), List.of(), List.of(), database);

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
        @ParameterizedTest(name = "client p99 {0} ms against 10 ms -> exit code {1}")
        @CsvSource({"10.5, 0", "15.0, " + LoadCommandRunner.GATE_FAILED})
        void gate_shouldExitWithTheVerdict_whenGivenThreeReportsAndABaseline(double clientP99Millis, int expected)
                throws IOException {
            final Path baseline = reportWriter
                    .write(root.resolve("baseline"), summary(0, 10.0), Map.of(), Map.of(), null, null)
                    .resolve(ReportWriter.SUMMARY_FILE);
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 3; run++) {
                reportWriter.write(reports, summary(run, clientP99Millis), Map.of(), Map.of(), null, null);
            }

            // No l2nx.load.protocol: the gate connects no load bot.
            final int actual = execute(
                    LoadCommandRunner.GATE,
                    "--" + LoadCommandRunner.BASELINE + "=" + baseline,
                    "--" + LoadCommandRunner.REPORTS + "=" + reports);

            assertThat(actual).isEqualTo(expected);
        }
    }

    private static int execute(String... args) {
        return SpringApplication.exit(SpringApplication.run(LoadApplication.class, args));
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static List<Path> summaries(Path directory) throws IOException {
        try (Stream<Path> files =
                Files.find(directory, 2, (file, attributes) -> file.endsWith(ReportWriter.SUMMARY_FILE))) {
            return files.toList();
        }
    }

    private static RunSummary summary(int run, double clientP99Millis) {
        return RunSummary.builder()
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
                                .p99Millis(clientP99Millis)
                                .p999Millis(clientP99Millis * 2)
                                .maxMillis(clientP99Millis * 3)
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
                        .build())
                .build();
    }
}
