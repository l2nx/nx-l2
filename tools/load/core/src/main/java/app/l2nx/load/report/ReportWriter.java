/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.gate.model.CriterionResult;
import app.l2nx.load.gate.model.GateVerdict;
import app.l2nx.load.report.model.Capacity;
import app.l2nx.load.report.model.FrameStats;
import app.l2nx.load.report.model.LevelStats;
import app.l2nx.load.report.model.RunEnvironment;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import app.l2nx.load.report.model.StepStats;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.HdrHistogram.Histogram;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class ReportWriter {

    public static final String SUMMARY_FILE = "summary.json";

    private static final DateTimeFormatter RUN_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final String NONE = "-";
    private static final String HISTOGRAM_EXTENSION = ".hgrm";
    private static final String STEP_HISTOGRAM_PREFIX = "step-";
    private static final String METRICS_PREFIX = "server-metrics-";
    private static final String METRICS_EXTENSION = ".prom";

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
     * Run ids have one-second resolution; a duplicate found only at {@link #write} would throw a finished run away.
     */
    public Path claim(Path root, String runId) throws IOException {
        Files.createDirectories(root);
        var directory = root.resolve(runId);
        try {
            return Files.createDirectory(directory);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("The report directory " + directory + " already exists", e);
        }
    }

    /**
     * @param histograms recorded in nanoseconds; written in milliseconds
     * @param stepHistograms recorded in nanoseconds; written in milliseconds
     * @param metricsSnapshots file name to verbatim text, named by {@link #metricsFile}; an empty map writes none
     */
    public Path write(
            Path root,
            RunSummary summary,
            Map<IntentKind, Histogram> histograms,
            Map<LoadBotState, Histogram> stepHistograms,
            Map<String, String> metricsSnapshots)
            throws IOException {
        var directory = Files.createDirectories(root.resolve(Objects.requireNonNull(summary.runId(), "runId")));
        jsonMapper.writeValue(directory.resolve(SUMMARY_FILE), summary);
        for (var entry : histograms.entrySet()) {
            writeHistogram(directory.resolve(histogramFile(entry.getKey())), entry.getValue());
        }
        for (var entry : stepHistograms.entrySet()) {
            writeHistogram(directory.resolve(stepHistogramFile(entry.getKey())), entry.getValue());
        }
        for (var entry : metricsSnapshots.entrySet()) {
            Files.writeString(directory.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
        }
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

    public static String metricsFile(String job, String boundary) {
        return METRICS_PREFIX + job + "-" + boundary + METRICS_EXTENSION;
    }

    public String consoleTable(RunSummary summary) {
        var out = new StringBuilder();
        var validity = String.valueOf(summary.validity());
        if (summary.invalidReasons() != null && !summary.invalidReasons().isEmpty()) {
            validity += " " + summary.invalidReasons();
        }
        line(out, "Run %s: %s", summary.runId(), validity);
        line(out, "report version %d", summary.reportVersion());
        line(
                out,
                "scenario %s, protocol %s, seed %d, server commit %s, CPU affinity %s",
                summary.scenario(),
                summary.protocol(),
                summary.seed(),
                text(summary.serverCommit()),
                text(summary.cpuAffinity()));
        appendEnvironment(out, summary.environment());
        if (summary.injection() != null) {
            var injection = summary.injection();
            line(
                    out,
                    "%d load bots, ramp %s, warm-up %s, steady %s, ramp-down %s, measured %s,"
                            + " intent timeout %s, lifecycle timeout %s",
                    injection.bots(),
                    injection.ramp(),
                    injection.warmUp(),
                    injection.steady(),
                    injection.rampDown(),
                    summary.measuredPhase(),
                    summary.intentTimeout(),
                    summary.lifecycleTimeout());
            if (injection.levels() != null) {
                var levels = injection.levels();
                line(
                        out,
                        "levels: %d, +%d load bots each, each level %s, ramp between levels %s",
                        levels.count(),
                        levels.increment(),
                        levels.duration(),
                        levels.ramp());
            }
        }
        line(
                out,
                "generator lag p99 %.2f ms, timeouts %.3f%%, rejected %.3f%%, stopped load bots %.3f%%,"
                        + " load bots at the final state %s: %s, not at it %.3f%%",
                summary.generatorLagP99Millis(),
                summary.timeoutShare() * 100,
                summary.rejectedShare() * 100,
                summary.stoppedShare() * 100,
                summary.finalState(),
                summary.reachedFinalState(),
                summary.notAtFinalStateShare() * 100);
        appendInboundFrames(out, summary.inboundFrames());
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
        if (summary.serverStartedAt() != null) {
            line(out, "server started %s", summary.serverStartedAt());
        }
        // A run with levels has no run-wide server values: its level rows carry them.
        if (summary.levels() == null) {
            appendServer(out, summary.serverWindow(), summary.server());
        }
        appendLevels(out, summary.levels(), summary.capacity());
        return out.toString();
    }

    public String verdictTable(GateVerdict verdict) {
        var out = new StringBuilder();
        line(
                out,
                "Gate %s against baseline %s: %s",
                verdict.scenario(),
                verdict.baselineRunIds(),
                verdict.passed() ? "PASS" : "FAIL");
        line(out, "valid runs %s, ignored invalid runs %s", verdict.validRunIds(), verdict.invalidRunIds());
        var subject = "%-" + width("subject", verdict.results().stream().map(result -> text(result.subject()))) + "s";
        var row = "%-20s " + subject + " %12s %12s %-6s %s";
        line(out, row, "criterion", "subject", "measured", "threshold", "result", "reason");
        for (CriterionResult result : verdict.results()) {
            line(
                    out,
                    row,
                    result.criterion(),
                    text(result.subject()),
                    number(result.measured()),
                    number(result.threshold()),
                    result.passed() ? "PASS" : "FAIL",
                    text(result.reason()));
        }
        return out.toString();
    }

    private static void appendEnvironment(StringBuilder out, RunEnvironment environment) {
        if (environment != null) {
            line(
                    out,
                    "%s, kernel %s, CPU %s, %d logical cores, cpuset %s, JVM %s",
                    text(environment.operatingSystem()),
                    text(environment.kernel()),
                    text(environment.cpuModel()),
                    environment.logicalCores(),
                    text(environment.cpuset()),
                    text(environment.jvm()));
        }
    }

    private static void appendInboundFrames(StringBuilder out, FrameStats frames) {
        if (frames != null) {
            line(
                    out,
                    "inbound frames %d, channelRead to handling p50 %.3f ms, p99 %.3f ms, p99.9 %.3f ms, max %.3f ms",
                    frames.frames(),
                    frames.p50Millis(),
                    frames.p99Millis(),
                    frames.p999Millis(),
                    frames.maxMillis());
        }
    }

    private static void appendSteps(StringBuilder out, Map<LoadBotState, StepStats> steps) {
        if (steps.isEmpty()) {
            return;
        }
        line(
                out,
                "%-16s %10s %9s %9s %9s %9s %9s",
                "lifecycle step",
                "count",
                "timeouts",
                "p50 ms",
                "p99 ms",
                "p99.9 ms",
                "max ms");
        new TreeMap<>(steps)
                .forEach((state, stats) -> line(
                        out,
                        "%-16s %10d %9d %9.2f %9.2f %9.2f %9.2f",
                        state,
                        stats.count(),
                        stats.timeouts(),
                        stats.p50Millis(),
                        stats.p99Millis(),
                        stats.p999Millis(),
                        stats.maxMillis()));
        out.append('\n');
    }

    private static void appendServer(StringBuilder out, ServerWindow window, ServerStats server) {
        if (window != null) {
            line(out, "server window %s .. %s (%d ms)", window.start(), window.end(), window.rangeMillis());
        }
        if (server == null) {
            line(out, "server meters: none");
            return;
        }
        line(
                out,
                "%-20s %10s %9s %9s %9s %11s %9s %9s %9s %9s %9s %9s %9s %9s",
                "loop",
                "passes",
                "p50 ms",
                "p99 ms",
                "max ms",
                "over budget",
                "failures",
                "overdue",
                "ownership",
                "jdbc",
                "jdbc s",
                "jdbc %",
                "inbox max",
                "busy %");
        if (server.loops() != null) {
            new TreeMap<>(server.loops())
                    .forEach((loop, stats) -> line(
                            out,
                            "%-20s %10d %9s %9s %9s %11d %9d %9d %9d %9d %9s %9s %9s %9s",
                            loop,
                            stats.passes(),
                            number(stats.passP50Millis()),
                            number(stats.passP99Millis()),
                            number(stats.passMaxMillis()),
                            stats.overBudget(),
                            stats.failures(),
                            stats.timersOverdue(),
                            stats.ownershipViolations(),
                            stats.jdbcOnLoop(),
                            number(stats.jdbcOnLoopSeconds()),
                            number(stats.jdbcOnLoopShare() == null ? null : stats.jdbcOnLoopShare() * 100),
                            number(stats.inboxDepthMax()),
                            number(stats.busyShare() == null ? null : stats.busyShare() * 100)));
        }
        line(
                out,
                "session queue depth p99 %s max %s",
                number(server.sessionQueueDepthP99()),
                number(server.sessionQueueDepthMax()));
        if (server.processes() != null) {
            new TreeMap<>(server.processes())
                    .forEach((job, process) -> line(
                            out,
                            "%s: GC pause p99 %s ms mean %s ms max %s ms, CPU mean %s, live threads max %s",
                            job,
                            number(process.gcPauseP99Millis()),
                            number(process.gcPauseMeanMillis()),
                            number(process.gcPauseMaxMillis()),
                            number(process.cpuMean()),
                            number(process.threadsLiveMax())));
        }
        if (server.legacyPacketMeanMillis() != null) {
            new TreeMap<>(server.legacyPacketMeanMillis())
                    .forEach((type, mean) -> line(out, "%s mean %s ms", type, number(mean)));
        }
        if (server.inputs() != null && !server.inputs().isEmpty()) {
            var row = "%-" + width("input", server.inputs().keySet().stream()) + "s %10s %9s %9s %9s";
            line(out, row, "input", "count", "p50 ms", "p99 ms", "max ms");
            new TreeMap<>(server.inputs())
                    .forEach((type, stats) -> line(
                            out,
                            row,
                            type,
                            stats.count(),
                            number(stats.p50Millis()),
                            number(stats.p99Millis()),
                            number(stats.maxMillis())));
        }
    }

    private static void appendLevels(StringBuilder out, List<LevelStats> levels, Capacity capacity) {
        if (levels == null) {
            return;
        }
        out.append('\n');
        line(
                out,
                "%-10s %-24s %12s %13s %10s %10s %10s %10s %14s %13s %-5s",
                "level bots",
                "server window end",
                "pass p99 ms",
                "over budget %",
                "busy %",
                "timeouts %",
                "rejected %",
                "stopped %",
                "not at final %",
                "generator ms",
                "valid");
        for (LevelStats level : levels) {
            line(
                    out,
                    "%-10d %-24s %12s %13s %10s %10.3f %10.3f %10.3f %14.3f %13s %-5s",
                    level.bots(),
                    level.window() == null ? NONE : level.window().end().toString(),
                    number(level.passP99Millis()),
                    number(level.overBudgetShare() == null ? null : level.overBudgetShare() * 100),
                    number(
                            level.busyShare() == null || level.busyShare().isEmpty()
                                    ? null
                                    : Collections.max(level.busyShare().values()) * 100),
                    level.timeoutShare() * 100,
                    level.rejectedShare() * 100,
                    level.stoppedShare() * 100,
                    level.notAtFinalStateShare() * 100,
                    number(level.generatorLagP99Millis()),
                    level.valid() ? "yes" : "no");
        }
        if (capacity != null) {
            line(
                    out,
                    "capacity %s load bots, limited by %s: %s",
                    capacity.bots() == null ? NONE : capacity.bots().toString(),
                    capacity.limitedBy(),
                    capacity.reason());
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

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(String.format(Locale.ROOT, format, args)).append('\n');
    }

    private static int width(String header, Stream<String> values) {
        return Math.max(header.length(), values.mapToInt(String::length).max().orElse(0));
    }

    private static String number(Double value) {
        return value == null ? NONE : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String text(String value) {
        return value == null ? NONE : value;
    }
}
