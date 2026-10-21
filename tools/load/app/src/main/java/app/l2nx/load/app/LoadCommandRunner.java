/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.gate.Gate;
import app.l2nx.load.gate.model.GateThresholds;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.latency.LatencyRecorder;
import app.l2nx.load.prometheus.PrometheusMeterWindow;
import app.l2nx.load.prometheus.PrometheusScraper;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.run.LoadRun;
import app.l2nx.load.run.RunSettings;
import app.l2nx.load.scenario.LoadScenario;
import java.io.IOException;
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
 * Runs {@code run} or {@code gate --baseline=<summary.json> --reports=<dir or summary.json>[,...]}. A reports
 * directory stands for every {@code summary.json} in it and its run directories; the baseline is never one of the runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadCommandRunner implements ApplicationRunner, ExitCodeGenerator {
    static final String RUN = "run";
    static final String GATE = "gate";
    static final String BASELINE = "baseline";
    static final String REPORTS = "reports";
    // Boot exits with 1 when a command fails; a failed gate is a verdict, not a failure of the harness.
    static final int GATE_FAILED = 2;

    private static final int CHARACTER_SLOT = 0;
    // A report root holds run directories, each holding its summary.
    private static final int REPORT_SEARCH_DEPTH = 2;

    private final LoadProperties properties;
    private final List<LoadScenario> scenarios;
    private final List<ClientProtocol> protocols;
    private final ReportWriter reportWriter = new ReportWriter();
    private int exitCode;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        final List<String> commands = args.getNonOptionArgs();
        if (commands.size() != 1) {
            throw new IllegalArgumentException(
                    "Give one command, " + RUN + " or " + GATE + ", got " + (commands.isEmpty() ? "none" : commands));
        }
        switch (commands.getFirst()) {
            case RUN -> run();
            case GATE -> gate(args);
            default ->
                throw new IllegalArgumentException(
                        "Unknown command " + commands.getFirst() + ", known: " + List.of(RUN, GATE));
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
        final ClientProtocol protocol = byId("protocol", protocols, ClientProtocol::id, properties.protocol());
        final RunSettings settings = settings(scenario, protocol);
        for (int repeat = 1; repeat <= properties.repeats(); repeat++) {
            log.info("Run {} of {}", repeat, properties.repeats());
            final LatencyRecorder latencies = new LatencyRecorder();
            // A null resource is skipped on close: a run without a Prometheus URL reads no server meters.
            try (PrometheusScraper scraper = settings.prometheusUrl() == null
                    ? null
                    : new PrometheusScraper(settings.prometheusUrl(), properties.scrapeTimeout())) {
                final PrometheusMeterWindow window = scraper == null ? null : new PrometheusMeterWindow(scraper);
                final RunSummary summary =
                        new LoadRun().execute(settings, scenario, protocol, this::identity, latencies, window);
                final Path directory = write(
                        summary,
                        latencies,
                        window == null ? null : window.getStartText(),
                        window == null ? null : window.getEndText());
                System.out.print(reportWriter.consoleTable(summary));
                log.info("Report of run {} written to {}", summary.runId(), directory);
            }
        }
    }

    private RunSettings settings(LoadScenario scenario, ClientProtocol protocol) {
        final RunSettings.RunSettingsBuilder settings = RunSettings.withDefaults()
                .scenario(scenario.id())
                .protocol(protocol.id())
                .injection(InjectionProfile.builder()
                        .bots(properties.bots())
                        .ramp(properties.ramp())
                        .warmUp(properties.warmUp())
                        .steady(properties.steady())
                        .rampDown(properties.rampDown())
                        .build())
                .seed(properties.seed())
                .intentTimeout(properties.intentTimeout())
                .prometheusUrl(properties.prometheusUrl())
                .serverCommit(properties.serverCommit())
                .cpuAffinity(properties.cpuAffinity());
        if (properties.eventLoopThreads() != null) {
            settings.eventLoopThreads(properties.eventLoopThreads());
        }
        return settings.build();
    }

    private LoadBotIdentity identity(int index) {
        return LoadBotIdentity.builder()
                .index(index)
                .account(properties.accounts().prefix() + index)
                .password(properties.accounts().password())
                .characterSlot(CHARACTER_SLOT)
                .build();
    }

    private Path write(RunSummary summary, LatencyRecorder latencies, String startText, String endText)
            throws IOException {
        final Path directory = properties.outputDir().resolve(summary.runId());
        // The run id has a one-second resolution: a second run of the same seed within it would overwrite the first.
        if (Files.exists(directory)) {
            throw new IllegalStateException("The report directory " + directory + " already exists");
        }
        final Map<IntentKind, Histogram> histograms = new EnumMap<>(IntentKind.class);
        for (IntentKind kind : summary.intents().keySet()) {
            histograms.put(kind, latencies.histogram(kind));
        }
        return reportWriter.write(properties.outputDir(), summary, histograms, startText, endText);
    }

    private void gate(ApplicationArguments args) throws IOException {
        final List<String> baselines = args.getOptionValues(BASELINE);
        if (baselines == null || baselines.size() != 1) {
            throw new IllegalArgumentException("The gate needs one --" + BASELINE + "=<summary.json>");
        }
        final List<String> reports = args.getOptionValues(REPORTS);
        if (reports == null || reports.isEmpty()) {
            throw new IllegalArgumentException("The gate needs --" + REPORTS + "=<dir or summary.json>[,...]");
        }
        final Path baselineFile = Path.of(baselines.getFirst());
        final RunSummary baseline = reportWriter.readSummary(baselineFile);
        final List<RunSummary> runs = new ArrayList<>();
        for (Path file : reportFiles(reports, baselineFile)) {
            runs.add(reportWriter.readSummary(file));
        }
        final GateVerdict verdict = Gate.evaluate(runs, baseline, thresholds());
        System.out.print(reportWriter.verdictTable(verdict));
        exitCode = verdict.passed() ? 0 : GATE_FAILED;
    }

    private static Set<Path> reportFiles(List<String> values, Path baselineFile) throws IOException {
        final Path baseline = baselineFile.toAbsolutePath().normalize();
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
        files.remove(baseline);
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
        if (gate.maxClientP99Ratio() != null) {
            thresholds.maxClientP99Ratio(gate.maxClientP99Ratio());
        }
        if (gate.maxTimeoutAndErrorShare() != null) {
            thresholds.maxTimeoutAndErrorShare(gate.maxTimeoutAndErrorShare());
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
