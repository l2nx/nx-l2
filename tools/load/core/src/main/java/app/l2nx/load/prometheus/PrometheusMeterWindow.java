/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.run.ServerMeterWindow;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The steady window read from the server's Prometheus endpoint: a scrape with the text exposition at each boundary,
 * the per-second samples between them, and the {@link ServerStats} of all of them at the end. A boundary scrape is
 * retried; one that still fails leaves the run without server meters instead of failing it, so the client results
 * are kept. One instance per run; the scraper stays the caller's to close.
 */
@Slf4j
public final class PrometheusMeterWindow implements ServerMeterWindow {
    static final int BOUNDARY_RETRIES = 3;

    private static final Duration RETRY_PAUSE = Duration.ofSeconds(1);

    private final PrometheusScraper scraper;
    private final Duration retryPause;
    private final List<Scrape> scrapes = new ArrayList<>();
    private boolean started;

    /** The text exposition at the window start, verbatim; {@code null} until {@link #start()} succeeded. */
    @Getter
    private String startText;

    /** The text exposition at the window end, verbatim; {@code null} until {@link #finish()} succeeded. */
    @Getter
    private String endText;

    public PrometheusMeterWindow(PrometheusScraper scraper) {
        this(scraper, RETRY_PAUSE);
    }

    PrometheusMeterWindow(PrometheusScraper scraper, Duration retryPause) {
        this.scraper = scraper;
        this.retryPause = retryPause;
    }

    /** A start that fails after its retries turns the samples off and makes {@link #finish()} return {@code null}. */
    @Override
    public void start() {
        scrapes.clear();
        startText = null;
        endText = null;
        started = true;
        final Scrape start = boundary("start");
        if (start != null) {
            scrapes.add(start);
            startText = start.text();
        }
    }

    /** A failed sample is logged and left out: the window still has its boundaries, only a gauge reading is lost. */
    @Override
    public void sample() {
        requireStarted();
        if (scrapes.isEmpty()) {
            return;
        }
        try {
            scrapes.add(scraper.scrape());
        } catch (IOException e) {
            log.warn("Server meter sample {} skipped", scrapes.size(), e);
        } catch (InterruptedException e) {
            // The run's next sleep sees the flag and ends the run.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return {@code null} when a boundary scrape failed after its retries (the window has no deltas then) or the
     *     window's meters cannot be read
     */
    @Override
    public ServerStats finish() {
        requireStarted();
        if (scrapes.isEmpty()) {
            return null;
        }
        final Scrape end = boundary("end");
        if (end == null) {
            return null;
        }
        scrapes.add(end);
        endText = end.text();
        try {
            return ServerStatsReader.read(new MetricWindow(scrapes));
        } catch (RuntimeException e) {
            log.warn("Server meters of the steady window are unreadable, the report has no server meters", e);
            return null;
        }
    }

    private void requireStarted() {
        if (!started) {
            throw new IllegalStateException("The server meter window has not started");
        }
    }

    private Scrape boundary(String which) {
        IOException failure = null;
        try {
            for (int attempt = 0; attempt <= BOUNDARY_RETRIES; attempt++) {
                if (attempt > 0) {
                    log.info(
                            "Server meter scrape at the steady window {} failed, retrying: {}",
                            which,
                            failure.toString());
                    Thread.sleep(retryPause);
                }
                try {
                    return scraper.scrapeWithText();
                } catch (IOException e) {
                    failure = e;
                }
            }
        } catch (InterruptedException e) {
            // The run's next sleep sees the flag and ends the run.
            Thread.currentThread().interrupt();
            log.warn("Server meter scrape at the steady window {} interrupted, the report has no server meters", which);
            return null;
        }
        log.warn(
                "Server meter scrape at the steady window {} failed {} times, the report has no server meters",
                which,
                BOUNDARY_RETRIES + 1,
                failure);
        return null;
    }
}
