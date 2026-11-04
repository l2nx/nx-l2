/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.gate.model.CriterionResult;
import app.l2nx.load.gate.model.GateCriterion;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.report.model.FrameStats;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.InvalidReason;
import app.l2nx.load.report.model.LoopStats;
import app.l2nx.load.report.model.ProcessStats;
import app.l2nx.load.report.model.RunEnvironment;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import app.l2nx.load.report.model.StepStats;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-21T08:15:30Z");

    private final ReportWriter writer = new ReportWriter();

    @TempDir
    Path root;

    @Nested
    class RunId {

        @Test
        void runId_shouldJoinScenarioUtcStartAndSeed() {
            var actual = ReportWriter.runId("load-roam", STARTED_AT, 42);

            assertThat(actual).isEqualTo("load-roam-20261021-081530-42");
        }
    }

    @Nested
    class Claim {

        @Test
        void claim_shouldCreateTheDirectoryOfTheRun_whenItIsFree() throws IOException {
            var actual = writer.claim(root.resolve("reports"), "load-roam-1");

            assertThat(actual).isDirectory().isEqualTo(root.resolve("reports/load-roam-1"));
        }

        @Test
        void claim_shouldFailNamingTheDirectory_whenItExists() throws IOException {
            writer.claim(root, "load-roam-1");

            assertThatIllegalStateException()
                    .isThrownBy(() -> writer.claim(root, "load-roam-1"))
                    .withMessageContaining("load-roam-1")
                    .withMessageContaining("already exists");
        }

        @Test
        void write_shouldFillTheClaimedDirectory() throws IOException {
            var claimed = writer.claim(root, summary().runId());

            var actual = writer.write(root, summary(), Map.of(), Map.of(), Map.of());

            assertThat(actual).isEqualTo(claimed);
            assertThat(actual.resolve(ReportWriter.SUMMARY_FILE)).exists();
        }
    }

    @Nested
    class Write {

        @Test
        void write_shouldWriteSummaryWithIsoTimesAndWithoutNulls() throws IOException {
            var directory = writer.write(root, summary(), Map.of(), Map.of(), Map.of());

            var actual = Files.readString(directory.resolve(ReportWriter.SUMMARY_FILE));

            assertThat(directory).isEqualTo(root.resolve("load-roam-20261021-081530-42"));
            assertThat(actual)
                    .contains("\"startedAt\" : \"2026-10-21T08:15:30Z\"")
                    .contains("\"steady\" : \"PT5M\"")
                    .contains("\"MOVE_TO\" : {")
                    .doesNotContain("serverCommit")
                    .doesNotContain("passMaxMillis")
                    .doesNotContain("null");
        }

        @Test
        void write_shouldWriteHistogramPerIntentInMilliseconds() throws IOException {
            var histogram = new Histogram(3);
            histogram.recordValue(Duration.ofMillis(12).toNanos());

            var directory = writer.write(root, summary(), Map.of(IntentKind.MOVE_TO, histogram), Map.of(), Map.of());

            var actual = Files.readString(directory.resolve("move_to.hgrm"));
            assertThat(actual).contains("Value     Percentile").contains("12.0").contains("#[Max     =       12.0");
        }

        @Test
        void write_shouldWriteHistogramPerLifecycleStepNextToTheIntentOnes() throws IOException {
            var intent = new Histogram(3);
            intent.recordValue(Duration.ofMillis(12).toNanos());
            var step = new Histogram(3);
            step.recordValue(Duration.ofMillis(250).toNanos());

            var directory = writer.write(
                    root, summary(), Map.of(IntentKind.LOGIN, intent), Map.of(LoadBotState.LOGIN_AUTH, step), Map.of());

            assertThat(directory.resolve("login.hgrm")).exists();
            var actual = Files.readString(directory.resolve(ReportWriter.stepHistogramFile(LoadBotState.LOGIN_AUTH)));
            assertThat(ReportWriter.stepHistogramFile(LoadBotState.LOGIN_AUTH)).isEqualTo("step-login_auth.hgrm");
            assertThat(actual).contains("#[Max     =      250.0");
        }

        @Test
        void write_shouldWriteSnapshotsVerbatimUnderTheirNames_whenTaken() throws IOException {
            var gameStart = "# TYPE l2nx_engine_pass_seconds histogram\nl2nx_engine_pass_seconds_count 1\n";
            var loginEnd = "jvm_threads_live_threads 42\n";

            var directory = writer.write(
                    root,
                    summary(),
                    Map.of(),
                    Map.of(),
                    Map.of(
                            ReportWriter.metricsFile("game", "start"),
                            gameStart,
                            ReportWriter.metricsFile("login", "end"),
                            loginEnd));

            assertThat(ReportWriter.metricsFile("game", "start")).isEqualTo("server-metrics-game-start.prom");
            assertThat(directory.resolve("server-metrics-game-start.prom")).hasContent(gameStart);
            assertThat(directory.resolve("server-metrics-login-end.prom")).hasContent(loginEnd);
        }

        @Test
        void write_shouldWriteNoSnapshotFile_whenNoneTaken() throws IOException {
            var directory = writer.write(root, summary(), Map.of(), Map.of(), Map.of());

            try (var files = Files.list(directory)) {
                assertThat(files.map(file -> file.getFileName().toString())).noneMatch(name -> name.endsWith(".prom"));
            }
        }
    }

    @Nested
    class ReadSummary {

        @Test
        void readSummary_shouldReturnWrittenSummary() throws IOException {
            var expected = summary();
            var directory = writer.write(root, expected, Map.of(), Map.of(), Map.of());

            var actual = writer.readSummary(directory.resolve(ReportWriter.SUMMARY_FILE));

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void readSummary_shouldKeepNullServer_whenRunReadNoServerMeters() throws IOException {
            var expected = summary().toBuilder().serverWindow(null).server(null).build();
            var directory = writer.write(root, expected, Map.of(), Map.of(), Map.of());

            var actual = writer.readSummary(directory.resolve(ReportWriter.SUMMARY_FILE));

            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class ConsoleTable {

        @Test
        void consoleTable_shouldListIntentsAndLoops() {
            var actual = writer.consoleTable(summary());

            assertThat(actual)
                    .contains("Run load-roam-20261021-081530-42: VALID")
                    .contains("server commit -")
                    .contains("timeouts 0.200%, rejected 0.100%, stopped load bots 0.000%,"
                            + " load bots at the final state IN_GAME: 20, not at it 0.000%")
                    .contains("ramp-down PT5S, measured STEADY, intent timeout PT5S, lifecycle timeout PT30S")
                    .contains("Linux amd64, kernel 6.8.0, CPU AMD Ryzen 5 5600X, 6 logical cores, cpuset 0-5,"
                            + " JVM OpenJDK 25")
                    .contains("server started {game=2026-10-21T08:00:00Z}")
                    .containsPattern("MOVE_TO\\s+1000\\s+997\\s+2\\s+1\\s+4\\.00\\s+11\\.50\\s+20\\.00\\s+25\\.00")
                    .containsPattern("lifecycle step\\s+count")
                    .containsPattern("LOGIN_AUTH\\s+20\\s+1\\s+3\\.00\\s+40\\.00\\s+60\\.00\\s+61\\.00")
                    .containsPattern("world-0\\s+6000\\s+5\\.000\\s+20\\.000\\s+-\\s+3\\s+0\\s+0\\s+2\\s+7"
                            + "\\s+0\\.014\\s+2\\.500\\s+12\\.000")
                    .containsPattern("l2nx-game-packets\\s+0\\s+-\\s+-\\s+-\\s+0\\s+4\\s+0\\s+0\\s+0\\s+-\\s+-\\s+-")
                    .contains("server window 2026-10-21T08:16:30Z .. 2026-10-21T08:21:30Z (300000 ms)")
                    .contains("game: GC pause p99 3.000 ms mean 1.200 ms max - ms, CPU mean 0.350, live threads max"
                            + " 64.000")
                    .contains("login: GC pause p99 - ms mean - ms max - ms, CPU mean 0.050, live threads max -")
                    .contains("MoveBackwardToLocation mean 0.100 ms");
        }

        @Test
        void consoleTable_shouldShowTheInboundFrames_whenTheProtocolReportedThem() {
            var withFrames = summary().toBuilder()
                    .inboundFrames(FrameStats.builder()
                            .frames(12)
                            .p50Millis(0.5)
                            .p99Millis(2.0)
                            .p999Millis(3.0)
                            .maxMillis(4.0)
                            .build())
                    .build();

            assertThat(writer.consoleTable(withFrames))
                    .contains("inbound frames 12, channelRead to handling p50 0.500 ms, p99 2.000 ms,"
                            + " p99.9 3.000 ms, max 4.000 ms");
            assertThat(writer.consoleTable(summary())).doesNotContain("inbound frames");
        }

        @Test
        void consoleTable_shouldNameInvalidReasonsAndMissingServer_whenRunIsInvalid() {
            var invalid = summary().toBuilder()
                    .validity(RunValidity.INVALID)
                    .invalidReasons(List.of(InvalidReason.GENERATOR_LAG))
                    .server(null)
                    .build();

            var actual = writer.consoleTable(invalid);

            assertThat(actual)
                    .contains(": INVALID [GENERATOR_LAG]")
                    .contains("server window 2026-10-21T08:16:30Z")
                    .contains("server meters: none");
        }
    }

    @Nested
    class VerdictTable {

        @Test
        void verdictTable_shouldListEveryCriterionWithReason() {
            var verdict = GateVerdict.builder()
                    .scenario("load-roam")
                    .baselineRunIds(List.of("base"))
                    .validRunIds(List.of("a", "b"))
                    .invalidRunIds(List.of("c"))
                    .results(List.of(
                            CriterionResult.builder()
                                    .criterion(GateCriterion.VALID_RUNS)
                                    .measured(2.0)
                                    .threshold(3.0)
                                    .passed(false)
                                    .build(),
                            CriterionResult.builder()
                                    .criterion(GateCriterion.ENGINE_PASS_P99)
                                    .threshold(50.0)
                                    .passed(false)
                                    .reason("no engine loop meters in runs [a]")
                                    .build()))
                    .build();

            var actual = writer.verdictTable(verdict);

            assertThat(actual)
                    .contains("Gate load-roam against baseline [base]: FAIL")
                    .contains("ignored invalid runs [c]")
                    .containsPattern("VALID_RUNS\\s+-\\s+2\\.000\\s+3\\.000\\s+FAIL")
                    .containsPattern(
                            "ENGINE_PASS_P99\\s+-\\s+-\\s+50\\.000\\s+FAIL\\s+no engine loop meters in runs \\[a]");
        }
    }

    private static RunSummary summary() {
        return RunSummary.builder()
                .runId(ReportWriter.runId("load-roam", STARTED_AT, 42))
                .scenario("load-roam")
                .protocol("in-memory")
                .seed(42)
                .injection(InjectionProfile.builder()
                        .bots(20)
                        .ramp(Duration.ofSeconds(10))
                        .warmUp(Duration.ofSeconds(30))
                        .steady(Duration.ofMinutes(5))
                        .rampDown(Duration.ofSeconds(5))
                        .build())
                .measuredPhase(MeasuredPhase.STEADY)
                .finalState(LoadBotState.IN_GAME)
                .intentTimeout(Duration.ofSeconds(5))
                .lifecycleTimeout(Duration.ofSeconds(30))
                .startedAt(STARTED_AT)
                .environment(RunEnvironment.builder()
                        .operatingSystem("Linux amd64")
                        .kernel("6.8.0")
                        .cpuModel("AMD Ryzen 5 5600X")
                        .logicalCores(6)
                        .cpuset("0-5")
                        .jvm("OpenJDK 25")
                        .build())
                .cpuAffinity("server 0-3, load bots 4-7")
                .validity(RunValidity.VALID)
                .invalidReasons(List.of())
                .intents(Map.of(
                        IntentKind.MOVE_TO,
                        IntentStats.builder()
                                .sent(1000)
                                .acknowledged(997)
                                .timeouts(2)
                                .errors(1)
                                .p50Millis(4)
                                .p99Millis(11.5)
                                .p999Millis(20)
                                .maxMillis(25)
                                .build()))
                .steps(Map.of(
                        LoadBotState.LOGIN_AUTH,
                        StepStats.builder()
                                .count(20)
                                .timeouts(1)
                                .p50Millis(3)
                                .p99Millis(40)
                                .p999Millis(60)
                                .maxMillis(61)
                                .build()))
                .generatorLagP99Millis(0.8)
                .timeoutShare(0.002)
                .rejectedShare(0.001)
                .stoppedShare(0)
                .reachedFinalState(20)
                .serverStartedAt(Map.of("game", Instant.parse("2026-10-21T08:00:00Z")))
                .serverWindow(ServerWindow.of(STARTED_AT.plusSeconds(60), STARTED_AT.plusSeconds(360)))
                .server(ServerStats.builder()
                        .loops(Map.of(
                                "world-0",
                                LoopStats.builder()
                                        .passes(6000)
                                        .passP50Millis(5.0)
                                        .passP99Millis(20.0)
                                        .overBudget(3)
                                        .ownershipViolations(2)
                                        .jdbcOnLoop(7)
                                        .jdbcOnLoopSeconds(0.014)
                                        .jdbcOnLoopShare(0.025)
                                        .phaseMeanMillis(Map.of("input", 0.4, "tick", 2.5))
                                        .inboxDepthMax(12.0)
                                        .inboxDepthP99(4.0)
                                        .build(),
                                "l2nx-game-packets",
                                LoopStats.builder()
                                        .untimed(true)
                                        .failures(4)
                                        .phaseMeanMillis(Map.of())
                                        .build()))
                        .legacyPacketMeanMillis(Map.of("MoveBackwardToLocation", 0.1))
                        .processes(Map.of(
                                "game",
                                ProcessStats.builder()
                                        .gcPauseP99Millis(3.0)
                                        .gcPauseMeanMillis(1.2)
                                        .cpuMean(0.35)
                                        .threadsLiveMax(64.0)
                                        .build(),
                                "login",
                                ProcessStats.builder().cpuMean(0.05).build()))
                        .build())
                .build();
    }
}
