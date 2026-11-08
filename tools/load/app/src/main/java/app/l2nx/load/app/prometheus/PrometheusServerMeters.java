/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import app.l2nx.load.run.ServerMeterWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Slf4j
public final class PrometheusServerMeters implements ServerMeterWindow {
    static final Duration INGEST_WAIT = Duration.ofSeconds(10);
    static final Duration INGEST_POLL = Duration.ofMillis(200);
    static final String START = "start";
    static final String END = "end";

    private final PrometheusClient client;
    private final List<LoadProperties.Target> targets;
    private final Duration maxClockSkew;
    private final InstantSource wallClock;
    private final Duration ingestWait;
    private final Duration ingestPoll;
    private final Map<String, String> snapshots = new LinkedHashMap<>();
    private Map<String, Duration> scrapeIntervals;

    public PrometheusServerMeters(PrometheusClient client, LoadProperties.Prometheus settings) {
        this(client, settings.targets(), settings.maxClockSkew(), InstantSource.system(), INGEST_WAIT, INGEST_POLL);
    }

    PrometheusServerMeters(
            PrometheusClient client,
            List<LoadProperties.Target> targets,
            Duration maxClockSkew,
            InstantSource wallClock,
            Duration ingestWait,
            Duration ingestPoll) {
        this.client = client;
        this.targets = List.copyOf(targets);
        this.maxClockSkew = maxClockSkew;
        this.wallClock = wallClock;
        this.ingestWait = ingestWait;
        this.ingestPoll = ingestPoll;
    }

    /** @return the start time of each job's server; of a job's several processes the earliest, the one to go stale */
    @Override
    public Map<String, Instant> checkReady() {
        if (!client.ready()) {
            throw new IllegalStateException("Prometheus at " + client.url()
                    + " is not ready; start it with docker compose --profile load up -d --wait prometheus");
        }
        checkClock();
        final Map<String, Duration> scraped = query(client::scrapeIntervals);
        final Map<String, Duration> intervals = new HashMap<>();
        final Map<String, Instant> startTimes = new TreeMap<>();
        for (LoadProperties.Target target : targets) {
            final Duration interval = scraped.get(target.job());
            if (interval == null) {
                throw new IllegalStateException("Prometheus scrapes no job " + target.job()
                        + "; the jobs of docker/prometheus/prometheus.yml and l2nx.load.prometheus.targets differ");
            }
            final List<Sample> up = query(() -> client.query(ServerMeterQuery.up(target.job()), null));
            if (up.isEmpty()) {
                throw new IllegalStateException("Prometheus has not scraped job " + target.job() + " yet");
            }
            if (up.stream().anyMatch(sample -> sample.value() != 1)) {
                throw new IllegalStateException(
                        "The " + target.job() + " server is down for Prometheus (" + target.metricsUrl() + ")");
            }
            intervals.put(target.job(), interval);
            startTimes.put(target.job(), startTime(target));
        }
        scrapeIntervals = Map.copyOf(intervals);
        return startTimes;
    }

    private Instant startTime(LoadProperties.Target target) {
        return query(() -> client.query(ServerMeterQuery.processStart(target.job()), null)).stream()
                .map(Sample::unixTime)
                .min(Instant::compareTo)
                .orElseThrow(() -> new IllegalStateException("The " + target.job()
                        + " server exposes no process_start_time_seconds (" + target.metricsUrl() + ")"));
    }

    @Override
    public void start() {
        snapshot(START);
    }

    @Override
    public void end() {
        requireReady();
        snapshot(END);
    }

    /** @throws IllegalStateException when {@link #checkReady()} has not passed: the scrape intervals are unknown */
    @Override
    public ServerStats measure(ServerWindow window) throws InterruptedException {
        requireReady();
        try {
            if (!awaitIngested(window.end())) {
                log.warn(
                        "Prometheus has no scrape at or after {} within {}, the report has no server meters",
                        window.end(),
                        ingestWait);
                return null;
            }
            final String unstable = unstableTarget(window);
            if (unstable != null) {
                log.warn(
                        "Target {} was not up through {} (a failed scrape leaves no start sample), the report has no"
                                + " server meters",
                        unstable,
                        window);
                return null;
            }
            final Map<ServerMeterQuery, List<Sample>> results = new EnumMap<>(ServerMeterQuery.class);
            for (ServerMeterQuery query : ServerMeterQuery.values()) {
                results.put(query, client.query(query.expression(window.rangeMillis()), window.end()));
            }
            return ServerStatsAssembler.assemble(results);
        } catch (RuntimeException e) {
            log.warn("Server meters of {} could not be queried, the report has no server meters", window, e);
            return null;
        }
    }

    private void requireReady() {
        if (scrapeIntervals == null) {
            throw new IllegalStateException("The server meters are read only after checkReady passed");
        }
    }

    // The first target without a series or with a sample below 1 in the window, else null.
    private String unstableTarget(ServerWindow window) {
        for (LoadProperties.Target target : targets) {
            final List<Sample> up = client.query(
                    ServerMeterQuery.upThroughout(
                            target.job(), window.rangeMillis(), scrapeIntervals.get(target.job())),
                    window.end());
            if (up.isEmpty() || up.stream().anyMatch(sample -> sample.value() < 1)) {
                return target.job();
            }
        }
        return null;
    }

    // Window bounds come from this clock but Prometheus evaluates them on its own (Docker VM): a skew moves the window.
    // Compared before and after the query, so the round trip is no skew.
    private void checkClock() {
        final Instant before = wallClock.instant();
        final List<Sample> now = query(() -> client.query(ServerMeterQuery.now(), null));
        final Instant after = wallClock.instant();
        if (now.size() != 1) {
            throw new IllegalStateException("Prometheus answered its time with " + now.size() + " samples");
        }
        final Instant prometheus = now.getFirst().unixTime();
        final Duration skew = prometheus.isBefore(before)
                ? Duration.between(prometheus, before)
                : prometheus.isAfter(after) ? Duration.between(after, prometheus) : Duration.ZERO;
        if (skew.compareTo(maxClockSkew) > 0) {
            throw new IllegalStateException("Prometheus's clock reads " + prometheus + " while the harness's read "
                    + before + " to " + after + ": a skew of " + skew.toMillis() + " ms, over the limit of "
                    + maxClockSkew.toMillis()
                    + " ms (l2nx.load.prometheus.max-clock-skew); resync the Docker VM clock");
        }
    }

    /** File name to text, for the report. */
    public Map<String, String> snapshots() {
        return Collections.unmodifiableMap(snapshots);
    }

    // A failed snapshot only loses the raw file: the numbers come from Prometheus.
    private void snapshot(String boundary) {
        for (LoadProperties.Target target : targets) {
            try {
                snapshots.put(ReportWriter.metricsFile(target.job(), boundary), client.text(target.metricsUrl()));
            } catch (RestClientException e) {
                log.warn("Snapshot of {} at the window {} failed", target.metricsUrl(), boundary, e);
            }
        }
    }

    // A sample carries its scrape's start time and lands up to a scrape later: until every target has a scrape at or
    // after the window end, the last part of the window is missing.
    private boolean awaitIngested(Instant end) throws InterruptedException {
        final String expression = ServerMeterQuery.lastScrape(
                targets.stream().map(LoadProperties.Target::job).toList());
        final Instant deadline = wallClock.instant().plus(ingestWait);
        while (true) {
            if (ingested(expression, end)) {
                return true;
            }
            if (!wallClock.instant().isBefore(deadline)) {
                return false;
            }
            Thread.sleep(ingestPoll);
        }
    }

    /** @throws IllegalStateException when Prometheus refuses the query: waiting longer would not change its answer */
    private boolean ingested(String expression, Instant end) {
        try {
            final List<Sample> last = client.query(expression, null);
            return !last.isEmpty() && !last.getFirst().unixTime().isBefore(end);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            log.debug("Last scrape time unknown yet", e);
            return false;
        }
    }

    private <T> T query(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            throw new IllegalStateException("Prometheus at " + client.url() + " could not be queried", e);
        }
    }
}
