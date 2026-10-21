/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.app.memory.InMemoryProtocol;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
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
                    "--l2nx.load.steady=1500ms",
                    "--l2nx.load.ramp-down=200ms",
                    "--l2nx.load.seed=42",
                    "--l2nx.load.repeats=1",
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
                    "--l2nx.load.repeats=1",
                    "--l2nx.load.event-loop-threads=1",
                    "--l2nx.load.prometheus-url=http://127.0.0.1:" + closedPort() + "/actuator/prometheus",
                    "--l2nx.load.scrape-timeout=1s",
                    "--l2nx.load.output-dir=" + root);

            final List<Path> summaries = summaries(root);
            assertThat(exitCode).isZero();
            assertThat(summaries).hasSize(1);
            final RunSummary actual = reportWriter.readSummary(summaries.getFirst());
            assertThat(actual.server()).isNull();
            assertThat(actual.intents().get(IntentKind.MOVE_TO).acknowledged()).isPositive();
            assertThat(summaries.getFirst().resolveSibling(ReportWriter.METRICS_START_FILE))
                    .doesNotExist();
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
                    .write(root.resolve("baseline"), summary(0, 10.0), Map.of(), null, null)
                    .resolve(ReportWriter.SUMMARY_FILE);
            final Path reports = root.resolve("reports");
            for (int run = 1; run <= 3; run++) {
                reportWriter.write(reports, summary(run, clientP99Millis), Map.of(), null, null);
            }

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
