/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.integration.prometheus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake {@code /actuator/prometheus}: each GET adds 100 loop passes (90 x 1 ms, 9 x 7 ms, 1 x 20 ms), so any window has
 * p50 in the first bucket and p99 on the 10 ms bound. The max gauge holds 30 ms, a pass before any window; the
 * JDBC-on-loop timer appears only after {@link #registerJdbcOnLoop()}. Each GET also adds 100 inputs of {@code a.MoveTo}
 * (90 under 0.5 ms, 9 under 1 ms, 1 under 2.5 ms), so the input p99 is 1 ms.
 */
final class MetricsStub implements AutoCloseable {
    static final String PATH = "/actuator/prometheus";
    static final long JDBC_CALLS = 7;
    static final long START_TIME_SECONDS = 1_790_000_000;

    private static final String JDBC_ON_LOOP = """
            # TYPE l2nx_engine_jdbc_on_loop_seconds summary
            l2nx_engine_jdbc_on_loop_seconds_count{loop="world"} %d
            l2nx_engine_jdbc_on_loop_seconds_sum{loop="world"} 0.014
            """.formatted(JDBC_CALLS);

    // Prometheus 3 refuses a scrape without a valid content type unless the job sets fallback_scrape_protocol.
    private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final HttpServer server;
    private final AtomicLong steps = new AtomicLong();
    private final AtomicBoolean jdbcOnLoop = new AtomicBoolean();
    private final AtomicBoolean failing = new AtomicBoolean();

    private MetricsStub(HttpServer server) {
        this.server = server;
    }

    static MetricsStub start() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            final MetricsStub stub = new MetricsStub(server);
            server.createContext(PATH, stub::answer);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to start the metrics stub", e);
        }
    }

    int port() {
        return server.getAddress().getPort();
    }

    void registerJdbcOnLoop() {
        jdbcOnLoop.set(true);
    }

    void failScrapes(boolean failing) {
        this.failing.set(failing);
    }

    URI metricsUrl() {
        return URI.create("http://127.0.0.1:" + port() + PATH);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        if (failing.get()) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        final String exposition = exposition(steps.incrementAndGet()) + (jdbcOnLoop.get() ? JDBC_ON_LOOP : "");
        final byte[] body = exposition.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String exposition(long n) {
        return """
            # TYPE l2nx_engine_pass_seconds histogram
            l2nx_engine_pass_seconds_bucket{loop="world",le="0.005"} %d
            l2nx_engine_pass_seconds_bucket{loop="world",le="0.01"} %d
            l2nx_engine_pass_seconds_bucket{loop="world",le="0.025"} %d
            l2nx_engine_pass_seconds_bucket{loop="world",le="+Inf"} %d
            l2nx_engine_pass_seconds_count{loop="world"} %d
            l2nx_engine_pass_seconds_sum{loop="world"} %s
            # TYPE l2nx_engine_pass_seconds_max gauge
            l2nx_engine_pass_seconds_max{loop="world"} 0.03
            # TYPE l2nx_engine_pass_over_budget_total counter
            l2nx_engine_pass_over_budget_total{loop="world"} 0
            # TYPE l2nx_engine_failures_total counter
            l2nx_engine_failures_total{loop="world",phase="inbox"} 0
            # TYPE l2nx_engine_timers_overdue_total counter
            l2nx_engine_timers_overdue_total{loop="world"} 0
            # TYPE l2nx_engine_ownership_violations_total counter
            l2nx_engine_ownership_violations_total{loop="world"} 0
            # TYPE l2nx_engine_inbox_depth gauge
            l2nx_engine_inbox_depth{loop="world"} 3
            # TYPE l2nx_session_input_seconds histogram
            l2nx_session_input_seconds_bucket{type="a.MoveTo",outcome="SUCCESS",exception="none",le="0.0005"} %d
            l2nx_session_input_seconds_bucket{type="a.MoveTo",outcome="SUCCESS",exception="none",le="0.001"} %d
            l2nx_session_input_seconds_bucket{type="a.MoveTo",outcome="SUCCESS",exception="none",le="0.0025"} %d
            l2nx_session_input_seconds_bucket{type="a.MoveTo",outcome="SUCCESS",exception="none",le="+Inf"} %d
            l2nx_session_input_seconds_count{type="a.MoveTo",outcome="SUCCESS",exception="none"} %d
            l2nx_session_input_seconds_sum{type="a.MoveTo",outcome="SUCCESS",exception="none"} %s
            # TYPE l2nx_session_input_seconds_max gauge
            l2nx_session_input_seconds_max{type="a.MoveTo",outcome="SUCCESS",exception="none"} 0.003
            # TYPE process_start_time_seconds gauge
            process_start_time_seconds %d
            """.formatted(
                        90 * n,
                        99 * n,
                        100 * n,
                        100 * n,
                        100 * n,
                        (0.09 + 0.063 + 0.02) * n,
                        90 * n,
                        99 * n,
                        100 * n,
                        100 * n,
                        100 * n,
                        (0.009 + 0.0099 + 0.0025) * n,
                        START_TIME_SECONDS);
    }
}
