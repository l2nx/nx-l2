/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.gate.model.CriterionResult;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.StepStats;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.HdrHistogram.Histogram;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class ReportWriter {

    public static final String SUMMARY_FILE = "summary.json";
    public static final String METRICS_START_FILE = "server-metrics-start.prom";
    public static final String METRICS_END_FILE = "server-metrics-end.prom";

    private static final DateTimeFormatter RUN_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final String NONE = "-";
    private static final String HISTOGRAM_EXTENSION = ".hgrm";
    private static final String STEP_HISTOGRAM_PREFIX = "step-";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion
                    .withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public static String runId(String scenario, Instant startedAt, long seed) {
        return scenario + "-" + RUN_ID_TIME.format(startedAt) + "-" + seed;
    }

    /**
     * @param histograms recorded in nanoseconds; written in milliseconds
     * @param stepHistograms recorded in nanoseconds; written in milliseconds
     * @param metricsStartText {@code null} writes no file
     * @param metricsEndText {@code null} writes no file
     */
    public Path write(
            Path root,
            RunSummary summary,
            Map<IntentKind, Histogram> histograms,
            Map<LoadBotState, Histogram> stepHistograms,
            String metricsStartText,
            String metricsEndText)
            throws IOException {
        var directory = Files.createDirectories(root.resolve(Objects.requireNonNull(summary.runId(), "runId")));
        jsonMapper.writeValue(directory.resolve(SUMMARY_FILE), summary);
        for (var entry : histograms.entrySet()) {
            writeHistogram(directory.resolve(histogramFile(entry.getKey())), entry.getValue());
        }
        for (var entry : stepHistograms.entrySet()) {
            writeHistogram(directory.resolve(stepHistogramFile(entry.getKey())), entry.getValue());
        }
        writeText(directory.resolve(METRICS_START_FILE), metricsStartText);
        writeText(directory.resolve(METRICS_END_FILE), metricsEndText);
        return directory;
    }

    public RunSummary readSummary(Path file) {
        return jsonMapper.readValue(file, RunSummary.class);
    }

    public static String histogramFile(IntentKind kind) {
        return kind.name().toLowerCase(Locale.ROOT) + HISTOGRAM_EXTENSION;
    }

    /** Prefixed, so a state never shares a file with an intent kind of the same name. */
    public static String stepHistogramFile(LoadBotState state) {
        return STEP_HISTOGRAM_PREFIX + state.name().toLowerCase(Locale.ROOT) + HISTOGRAM_EXTENSION;
    }

    public String consoleTable(RunSummary summary) {
        var out = new StringBuilder();
        var validity = String.valueOf(summary.validity());
        if (summary.invalidReasons() != null && !summary.invalidReasons().isEmpty()) {
            validity += " " + summary.invalidReasons();
        }
        line(out, "Run %s: %s", summary.runId(), validity);
        line(
                out,
                "scenario %s, protocol %s, seed %d, server commit %s, CPU affinity %s",
                summary.scenario(),
                summary.protocol(),
                summary.seed(),
                text(summary.serverCommit()),
                text(summary.cpuAffinity()));
        if (summary.injection() != null) {
            var injection = summary.injection();
            line(
                    out,
                    "%d load bots, ramp %s, warm-up %s, steady %s, ramp-down %s, measured %s",
                    injection.bots(),
                    injection.ramp(),
                    injection.warmUp(),
                    injection.steady(),
                    injection.rampDown(),
                    summary.measuredPhase());
        }
        line(
                out,
                "generator lag p99 %.2f ms, timeouts %.3f%%, rejected %.3f%%, stopped load bots %.3f%%,"
                        + " load bots at the final state %s: %s",
                summary.generatorLagP99Millis(),
                summary.timeoutShare() * 100,
                summary.rejectedShare() * 100,
                summary.stoppedShare() * 100,
                summary.finalState(),
                summary.reachedFinalState());
        out.append('\n');
        line(
                out,
                "%-16s %10s %10s %9s %9s %9s %9s %9s %9s",
                "intent",
                "sent",
                "acked",
                "timeouts",
                "errors",
                "p50 ms",
                "p99 ms",
                "p99.9 ms",
                "max ms");
        if (summary.intents() != null) {
            new TreeMap<>(summary.intents())
                    .forEach((kind, stats) -> line(
                            out,
                            "%-16s %10d %10d %9d %9d %9.2f %9.2f %9.2f %9.2f",
                            kind,
                            stats.sent(),
                            stats.acknowledged(),
                            stats.timeouts(),
                            stats.errors(),
                            stats.p50Millis(),
                            stats.p99Millis(),
                            stats.p999Millis(),
                            stats.maxMillis()));
        }
        out.append('\n');
        appendSteps(out, summary.steps());
        appendServer(out, summary.server());
        return out.toString();
    }

    public String verdictTable(GateVerdict verdict) {
        var out = new StringBuilder();
        line(
                out,
                "Gate %s against baseline %s: %s",
                verdict.scenario(),
                verdict.baselineRunId(),
                verdict.passed() ? "PASS" : "FAIL");
        line(out, "valid runs %s, ignored invalid runs %s", verdict.validRunIds(), verdict.invalidRunIds());
        line(out, "%-20s %-18s %12s %12s %-6s %s", "criterion", "subject", "measured", "threshold", "result", "reason");
        for (CriterionResult result : verdict.results()) {
            line(
                    out,
                    "%-20s %-18s %12s %12s %-6s %s",
                    result.criterion(),
                    text(result.subject()),
                    number(result.measured()),
                    number(result.threshold()),
                    result.passed() ? "PASS" : "FAIL",
                    text(result.reason()));
        }
        return out.toString();
    }

    private static void appendSteps(StringBuilder out, Map<LoadBotState, StepStats> steps) {
        if (steps.isEmpty()) {
            return;
        }
        line(out, "%-16s %10s %9s %9s %9s %9s", "lifecycle step", "count", "p50 ms", "p99 ms", "p99.9 ms", "max ms");
        new TreeMap<>(steps)
                .forEach((state, stats) -> line(
                        out,
                        "%-16s %10d %9.2f %9.2f %9.2f %9.2f",
                        state,
                        stats.count(),
                        stats.p50Millis(),
                        stats.p99Millis(),
                        stats.p999Millis(),
                        stats.maxMillis()));
        out.append('\n');
    }

    private static void appendServer(StringBuilder out, ServerStats server) {
        if (server == null) {
            line(out, "server meters: none");
            return;
        }
        line(
                out,
                "%-16s %10s %9s %9s %9s %11s %9s %9s %9s",
                "loop",
                "passes",
                "p50 ms",
                "p99 ms",
                "max ms",
                "over budget",
                "failures",
                "overdue",
                "inbox max");
        if (server.loops() != null) {
            new TreeMap<>(server.loops())
                    .forEach((loop, stats) -> line(
                            out,
                            "%-16s %10d %9s %9s %9s %11d %9d %9d %9s",
                            loop,
                            stats.passes(),
                            number(stats.passP50Millis()),
                            number(stats.passP99Millis()),
                            number(stats.passMaxMillis()),
                            stats.overBudget(),
                            stats.failures(),
                            stats.timersOverdue(),
                            number(stats.inboxDepthMax())));
        }
        line(
                out,
                "session queue depth p99 %s max %s, GC pause p99 %s ms mean %s ms max %s ms, process CPU mean %s,"
                        + " live threads max %s",
                number(server.sessionQueueDepthP99()),
                number(server.sessionQueueDepthMax()),
                number(server.gcPauseP99Millis()),
                number(server.gcPauseMeanMillis()),
                number(server.gcPauseMaxMillis()),
                number(server.processCpuMean()),
                number(server.threadsLiveMax()));
        var legacyMean = server.legacyMeanMillis() == null ? Map.<String, Double>of() : server.legacyMeanMillis();
        var legacyP99 = server.legacyP99Millis() == null ? Map.<String, Double>of() : server.legacyP99Millis();
        var legacyMax = server.legacyMaxMillis() == null ? Map.<String, Double>of() : server.legacyMaxMillis();
        var legacyMeters = new TreeSet<>(legacyMean.keySet());
        legacyMeters.addAll(legacyP99.keySet());
        legacyMeters.addAll(legacyMax.keySet());
        for (var meter : legacyMeters) {
            line(
                    out,
                    "%s mean %s ms p99 %s ms max %s ms",
                    meter,
                    number(legacyMean.get(meter)),
                    number(legacyP99.get(meter)),
                    number(legacyMax.get(meter)));
        }
    }

    private static void writeHistogram(Path file, Histogram histogram) throws IOException {
        try (var out = new PrintStream(Files.newOutputStream(file), false, StandardCharsets.UTF_8)) {
            histogram.outputPercentileDistribution(out, NANOS_PER_MILLI);
            if (out.checkError()) {
                throw new IOException("Failed to write " + file);
            }
        }
    }

    private static void writeText(Path file, String text) throws IOException {
        if (text != null) {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        }
    }

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(String.format(Locale.ROOT, format, args)).append('\n');
    }

    private static String number(Double value) {
        return value == null ? NONE : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String text(String value) {
        return value == null ? NONE : value;
    }
}
