/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus.model;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;

/** The envelope of {@code /api/v1/targets}; only the active targets' job and scrape interval are read. */
@Builder(toBuilder = true)
public record TargetsResponse(String status, Data data, String errorType, String error) {
    static final String JOB = "job";

    // Prometheus's model.ParseDuration: units from years down to milliseconds, each at most once, in this order.
    private static final Pattern DURATION =
            Pattern.compile("(?:(\\d+)y)?(?:(\\d+)w)?(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?(?:(\\d+)ms)?");
    private static final List<Duration> UNITS = List.of(
            Duration.ofDays(365),
            Duration.ofDays(7),
            Duration.ofDays(1),
            Duration.ofHours(1),
            Duration.ofMinutes(1),
            Duration.ofSeconds(1),
            Duration.ofMillis(1));

    @Builder(toBuilder = true)
    public record Data(List<Target> activeTargets) {
        public Data {
            activeTargets = activeTargets == null ? null : List.copyOf(activeTargets);
        }
    }

    /** @param labels the target's labels after relabelling, as its series carry them */
    @Builder(toBuilder = true)
    public record Target(Map<String, String> labels, String scrapeInterval) {
        public Target {
            labels = labels == null ? null : Map.copyOf(labels);
        }
    }

    /**
     * The longest scrape interval of the active targets of every job.
     *
     * @throws IllegalStateException on an error envelope or a scrape interval Prometheus would not write
     */
    public Map<String, Duration> scrapeIntervals() {
        if (!"success".equals(status)) {
            throw new IllegalStateException("Prometheus refused its targets: " + errorType + ": " + error);
        }
        final Map<String, Duration> intervals = new HashMap<>();
        for (Target target : data.activeTargets()) {
            final String job = target.labels() == null ? null : target.labels().get(JOB);
            if (job != null) {
                intervals.merge(job, duration(target.scrapeInterval()), (a, b) -> a.compareTo(b) >= 0 ? a : b);
            }
        }
        return Map.copyOf(intervals);
    }

    static Duration duration(String text) {
        final Matcher matcher = text == null ? null : DURATION.matcher(text);
        if (matcher == null || text.isEmpty() || !matcher.matches()) {
            throw new IllegalStateException("Prometheus wrote a scrape interval of " + text);
        }
        Duration duration = Duration.ZERO;
        for (int unit = 0; unit < UNITS.size(); unit++) {
            final String count = matcher.group(unit + 1);
            if (count != null) {
                duration = duration.plus(UNITS.get(unit).multipliedBy(Long.parseLong(count)));
            }
        }
        return duration;
    }
}
