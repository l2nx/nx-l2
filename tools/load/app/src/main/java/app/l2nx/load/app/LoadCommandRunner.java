/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.app.database.DatabaseCommands;
import app.l2nx.load.app.prometheus.PrometheusClient;
import app.l2nx.load.app.prometheus.PrometheusServerMeters;
import app.l2nx.load.app.seed.SeedLoadScenario;
import app.l2nx.load.app.seed.model.CharacterPlan;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.gate.Gate;
import app.l2nx.load.gate.model.GateThresholds;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.latency.LatencyRecorder;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.run.LoadRun;
import app.l2nx.load.run.RunSettings;
import app.l2nx.load.scenario.LoadScenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.HdrHistogram.Histogram;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Runs one command. Every {@code run} needs a freshly reset database and freshly started servers: the login server
 * keeps a load bot that went on to the game server as logged in for minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadCommandRunner implements ApplicationRunner, ExitCodeGenerator {
    static final String RUN = "run";
    static final String GATE = "gate";
    static final String SEED = "seed";
    static final String DB_CREATE = "db-create";
    static final String DB_SNAPSHOT = "db-snapshot";
    static final String DB_RESET = "db-reset";
    static final List<String> COMMANDS = List.of(RUN, GATE, SEED, DB_CREATE, DB_SNAPSHOT, DB_RESET);
    static final String BASELINE = "baseline";
    static final String REPORTS = "reports";
    // Boot exits with 1 when a command fails; a failed gate is a verdict, not a failure of the harness.
    static final int GATE_FAILED = 2;

    private static final int REPORT_SEARCH_DEPTH = 2;

    private final LoadProperties properties;
    private final List<LoadScenario> scenarios;
    private final List<ClientProtocolFactory> protocols;
    private final DatabaseCommands database;
    private final PrometheusClient prometheus;
    private final ReportWriter reportWriter = new ReportWriter();
    private int exitCode;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        final List<String> commands = args.getNonOptionArgs();
        if (commands.size() != 1) {
            throw new IllegalArgumentException(
                    "Give one command of " + COMMANDS + ", got " + (commands.isEmpty() ? "none" : commands));
        }
        switch (commands.getFirst()) {
            case RUN -> run();
            case GATE -> gate(args);
            case SEED -> seed();
            case DB_CREATE -> database.create();
            case DB_SNAPSHOT -> database.snapshot();
            case DB_RESET -> database.reset();
            default ->
                throw new IllegalArgumentException("Unknown command " + commands.getFirst() + ", known: " + COMMANDS);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private void run() throws IOException, InterruptedException {
        if (properties.scenario() == null) {
            throw new IllegalArgumentException(
                    "The run needs l2nx.load.scenario, one of " + ids(scenarios, LoadScenario::id));
        }
        final LoadScenario scenario = byId("scenario", scenarios, LoadScenario::id, properties.scenario());
        final ClientProtocol protocol = protocol(RUN);
        final RunSettings settings = settings(scenario, protocol);
        final LatencyRecorder latencies = new LatencyRecorder();
        final PrometheusServerMeters meters =
                protocol.hasServer() ? new PrometheusServerMeters(prometheus, properties.prometheus()) : null;
        final RunSummary summary = new LoadRun()
                .execute(settings, scenario, protocol, identities(scenario), latencies, meters, this::claimReport);
        final Path directory = write(summary, latencies, meters == null ? Map.of() : meters.snapshots());
        System.out.print(reportWriter.consoleTable(summary));
        log.info("Report of run {} written to {}", summary.runId(), directory);
    }

    private void claimReport(String runId) {
        try {
            reportWriter.claim(properties.outputDir(), runId);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create the report directory of run " + runId, e);
        }
    }

    /** @throws IllegalStateException when a load bot's character was not set up and stored */
    private void seed() throws InterruptedException {
        if (properties.levels().increment() != null) {
            throw new IllegalArgumentException("Seeding takes no levels: seed l2nx.load.bots accounts, one more than"
                    + " the top level of the capacity run");
        }
        for (CharacterPlan plan : CharacterPlan.values()) {
            seed(plan);
        }
        log.info("{} accounts seeded with the characters of {}", properties.bots(), List.of(CharacterPlan.values()));
    }

    /** The login server creates an account on its first login, so the first plan's pass makes the accounts too. */
    private void seed(CharacterPlan plan) throws InterruptedException {
        final SeedLoadScenario scenario =
                new SeedLoadScenario(plan, properties.accounts().prefix(), properties.bots());
        final ClientProtocol protocol = protocol(SEED);
        // Seeding is not measured: the servers run with the profile load-seed.
        final RunSummary summary = new LoadRun()
                .execute(
                        settings(scenario, protocol),
                        scenario,
                        protocol,
                        identities(scenario),
                        new LatencyRecorder(),
                        null);
        System.out.print(reportWriter.consoleTable(summary));
        final int failed = properties.bots() - Math.min(scenario.seeded(), summary.loggedOut());
        if (failed > 0) {
            throw new IllegalStateException(
                    failed + " of " + properties.bots() + " load bots were not seeded with "
                            + plan + " (" + scenario.seeded() + " set up, " + summary.loggedOut()
                            + " logged out); are the servers up with the profiles load and load-seed, on an unseeded database?");
        }
    }

    private ClientProtocol protocol(String command) {
        if (properties.protocol() == null) {
            throw new IllegalArgumentException("The " + command + " needs l2nx.load.protocol, one of "
                    + ids(protocols, ClientProtocolFactory::id));
        }
        return byId("protocol", protocols, ClientProtocolFactory::id, properties.protocol())
                .create()
                .get();
    }

    private LoadBotIdentities identities(LoadScenario scenario) {
        return new LoadBotIdentities(properties.accounts(), scenario.characterSlot());
    }

    private RunSettings settings(LoadScenario scenario, ClientProtocol protocol) {
        final RunSettings.RunSettingsBuilder settings = RunSettings.withDefaults()
                .scenario(scenario.id())
                .protocol(protocol.id())
                .injection(injection())
                .seed(properties.seed())
                .intentTimeout(properties.intentTimeout())
                .lifecycleTimeout(properties.lifecycleTimeout())
                .maxGeneratorLagP99(properties.maxGeneratorLagP99())
                .gate(thresholds())
                .serverCommit(properties.serverCommit())
                .cpuAffinity(properties.cpuAffinity());
        if (properties.eventLoopThreads() != null) {
            settings.eventLoopThreads(properties.eventLoopThreads());
        }
        return settings.build();
    }

    private InjectionProfile injection() {
        final LoadProperties.Levels levels = properties.levels();
        return InjectionProfile.builder()
                .bots(properties.bots())
                .ramp(properties.ramp())
                .warmUp(properties.warmUp())
                .steady(properties.steady())
                .rampDown(properties.rampDown())
                .levels(
                        levels.increment() == null
                                ? null
                                : InjectionProfile.Levels.builder()
                                        .increment(levels.increment())
                                        .count(levels.count())
                                        .duration(levels.duration())
                                        .ramp(levels.ramp())
                                        .build())
                .build();
    }

    private Path write(RunSummary summary, LatencyRecorder latencies, Map<String, String> metricsSnapshots)
            throws IOException {
        final Map<IntentKind, Histogram> histograms = new EnumMap<>(IntentKind.class);
        for (IntentKind kind : summary.intents().keySet()) {
            histograms.put(kind, latencies.histogram(kind));
        }
        final Map<LoadBotState, Histogram> stepHistograms = new EnumMap<>(LoadBotState.class);
        for (LoadBotState state : summary.steps().keySet()) {
            stepHistograms.put(state, latencies.stepHistogram(state));
        }
        return reportWriter.write(properties.outputDir(), summary, histograms, stepHistograms, metricsSnapshots);
    }

    private void gate(ApplicationArguments args) throws IOException {
        final List<String> reports = args.getOptionValues(REPORTS);
        if (reports == null || reports.isEmpty()) {
            throw new IllegalArgumentException("The gate needs --" + REPORTS + "=<dir or summary.json>[,...]");
        }
        final List<String> baselineValues = args.getOptionValues(BASELINE);
        final Set<Path> baselineFiles = baselineValues == null ? Set.of() : reportFiles(baselineValues, Set.of());
        final List<RunSummary> baselines = summaries(baselineFiles);
        final List<RunSummary> runs = summaries(reportFiles(reports, baselineFiles));
        final GateVerdict verdict = Gate.evaluate(runs, baselines, thresholds());
        System.out.print(reportWriter.verdictTable(verdict));
        exitCode = verdict.passed() ? 0 : GATE_FAILED;
    }

    private List<RunSummary> summaries(Set<Path> files) {
        final List<RunSummary> summaries = new ArrayList<>();
        for (Path file : files) {
            summaries.add(reportWriter.readSummary(file));
        }
        return summaries;
    }

    private static Set<Path> reportFiles(List<String> values, Set<Path> excluded) throws IOException {
        final Set<Path> files = new LinkedHashSet<>();
        for (String value : values) {
            for (String part : value.split(",")) {
                final Path path = Path.of(part.strip()).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) {
                    try (Stream<Path> found = Files.find(
                            path,
                            REPORT_SEARCH_DEPTH,
                            (file, attributes) -> attributes.isRegularFile()
                                    && file.getFileName().toString().equals(ReportWriter.SUMMARY_FILE))) {
                        found.sorted().forEach(files::add);
                    }
                } else if (Files.isRegularFile(path)) {
                    files.add(path);
                } else {
                    throw new IllegalArgumentException("No report at " + path);
                }
            }
        }
        files.removeAll(excluded);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No " + ReportWriter.SUMMARY_FILE + " in " + values);
        }
        return files;
    }

    private GateThresholds thresholds() {
        final LoadProperties.Gate gate = properties.gate();
        final GateThresholds.GateThresholdsBuilder thresholds = GateThresholds.defaults().toBuilder();
        if (gate.runs() != null) {
            thresholds.runs(gate.runs());
        }
        if (gate.passBudget() != null) {
            thresholds.passBudget(gate.passBudget());
        }
        if (gate.maxOverBudgetShare() != null) {
            thresholds.maxOverBudgetShare(gate.maxOverBudgetShare());
        }
        if (gate.maxInputP99Ratio() != null) {
            thresholds.maxInputP99Ratio(gate.maxInputP99Ratio());
        }
        if (gate.inputP99Floor() != null) {
            thresholds.inputP99Floor(gate.inputP99Floor());
        }
        if (gate.maxStepP99Ratio() != null) {
            thresholds.maxStepP99Ratio(gate.maxStepP99Ratio());
        }
        if (gate.stepP99Floor() != null) {
            thresholds.stepP99Floor(gate.stepP99Floor());
        }
        if (gate.maxTimeoutAndErrorShare() != null) {
            thresholds.maxTimeoutAndErrorShare(gate.maxTimeoutAndErrorShare());
        }
        if (gate.maxNotAtFinalStateShare() != null) {
            thresholds.maxNotAtFinalStateShare(gate.maxNotAtFinalStateShare());
        }
        return thresholds.build();
    }

    private static <T> T byId(String kind, List<T> candidates, Function<T, String> id, String wanted) {
        final List<T> matching = candidates.stream()
                .filter(candidate -> id.apply(candidate).equals(wanted))
                .toList();
        if (matching.isEmpty()) {
            throw new IllegalArgumentException("Unknown " + kind + " " + wanted + ", known: " + ids(candidates, id));
        }
        if (matching.size() > 1) {
            throw new IllegalStateException(matching.size() + " beans have the " + kind + " id " + wanted);
        }
        return matching.getFirst();
    }

    private static <T> List<String> ids(List<T> candidates, Function<T, String> id) {
        return candidates.stream().map(id).sorted().toList();
    }
}
