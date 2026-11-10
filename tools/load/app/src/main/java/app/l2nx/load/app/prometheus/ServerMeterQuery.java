/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import java.time.Duration;
import java.util.Collection;

/**
 * The PromQL of every server value of the run report. Engine rows group by {@code loop} (only the game server exposes
 * them), process rows by {@code job}.
 */
public enum ServerMeterQuery {
    PASS_COUNT("sum by (loop) " + delta("l2nx_engine_pass_seconds_count")),
    PASS_P50("histogram_quantile(0.5, sum by (loop, le) " + delta("l2nx_engine_pass_seconds_bucket") + ")"),
    PASS_P99("histogram_quantile(0.99, sum by (loop, le) " + delta("l2nx_engine_pass_seconds_bucket") + ")"),
    PASS_BUCKETS("sum by (loop, le) " + delta("l2nx_engine_pass_seconds_bucket")),
    PASS_MAX("max by (loop) (max_over_time(l2nx_engine_pass_seconds_max[%1$dms]))"),
    PASS_BUSY_SHARE("sum by (loop) " + delta("l2nx_engine_pass_seconds_sum") + " / (%1$d / 1000)"),
    PHASE_MEAN("sum by (loop, phase) " + delta("l2nx_engine_pass_phase_seconds_sum") + " / sum by (loop, phase) "
            + delta("l2nx_engine_pass_phase_seconds_count")),
    OVER_BUDGET("sum by (loop) " + delta("l2nx_engine_pass_over_budget_total")),
    FAILURES("sum by (loop) " + delta("l2nx_engine_failures_total")),
    TIMERS_OVERDUE("sum by (loop) " + delta("l2nx_engine_timers_overdue_total")),
    OWNERSHIP_VIOLATIONS("sum by (loop) " + delta("l2nx_engine_ownership_violations_total")),
    JDBC_ON_LOOP("sum by (loop) " + delta("l2nx_engine_jdbc_on_loop_seconds_count")),
    JDBC_ON_LOOP_SECONDS("sum by (loop) " + delta("l2nx_engine_jdbc_on_loop_seconds_sum")),
    JDBC_ON_LOOP_SHARE("sum by (loop) " + delta("l2nx_engine_jdbc_on_loop_seconds_sum") + " / sum by (loop) "
            + delta("l2nx_engine_pass_seconds_sum")),
    JDBC_READS_ON_LOOP("sum by (loop) " + delta("l2nx_engine_jdbc_on_loop_statements_total{kind=\"read\"}")),
    JDBC_WRITES_ON_LOOP("sum by (loop) " + delta("l2nx_engine_jdbc_on_loop_statements_total{kind=\"write\"}")),
    INBOX_DEPTH_MAX("max by (loop) (max_over_time(l2nx_engine_inbox_depth[%1$dms]))"),
    INBOX_DEPTH_P99("max by (loop) (quantile_over_time(0.99, l2nx_engine_inbox_depth[%1$dms]))"),
    SESSION_QUEUE_DEPTH_P99("histogram_quantile(0.99, sum by (le) " + delta("l2nx_session_queue_depth_bucket") + ")"),
    SESSION_QUEUE_DEPTH_BUCKETS("sum by (le) " + delta("l2nx_session_queue_depth_bucket")),
    SESSION_QUEUE_DEPTH_MAX("max(max_over_time(l2nx_session_queue_depth_max[%1$dms]))"),
    LEGACY_PACKET_MEAN("sum by (type) " + delta("l2nx_legacy_packet_seconds_sum") + " / sum by (type) "
            + delta("l2nx_legacy_packet_seconds_count")),
    INPUT_COUNT("sum by (type) " + delta("l2nx_session_input_seconds_count")),
    INPUT_P50("histogram_quantile(0.5, sum by (type, le) " + delta("l2nx_session_input_seconds_bucket") + ")"),
    INPUT_P99("histogram_quantile(0.99, sum by (type, le) " + delta("l2nx_session_input_seconds_bucket") + ")"),
    INPUT_BUCKETS("sum by (type, le) " + delta("l2nx_session_input_seconds_bucket")),
    INPUT_MAX("max by (type) (max_over_time(l2nx_session_input_seconds_max[%1$dms]))"),
    GC_PAUSE_P99("histogram_quantile(0.99, sum by (job, le) " + delta("jvm_gc_pause_seconds_bucket") + ")"),
    GC_PAUSE_BUCKETS("sum by (job, le) " + delta("jvm_gc_pause_seconds_bucket")),
    GC_PAUSE_MEAN("sum by (job) " + delta("jvm_gc_pause_seconds_sum") + " / sum by (job) "
            + delta("jvm_gc_pause_seconds_count")),
    GC_PAUSE_MAX("max by (job) (max_over_time(jvm_gc_pause_seconds_max[%1$dms]))"),
    CPU_MEAN("avg by (job) (avg_over_time(process_cpu_usage[%1$dms]))"),
    THREADS_LIVE_MAX("max by (job) (max_over_time(jvm_threads_live_threads[%1$dms]))");

    private final String template;

    ServerMeterQuery(String template) {
        this.template = template;
    }

    public String expression(long rangeMillis) {
        return template.formatted(rangeMillis);
    }

    /** The newest scrape of every target, to wait until Prometheus has ingested the window's end. */
    static String lastScrape(Collection<String> jobs) {
        return "min(timestamp(up{job=~\"" + String.join("|", jobs) + "\"}))";
    }

    static String up(String job) {
        return "up{job=\"" + job + "\"}";
    }

    static String processStart(String job) {
        return "process_start_time_seconds{job=\"" + job + "\"}";
    }

    /**
     * The lowest {@code up} from one scrape before the window start: {@code offset} finds no start sample when the latest
     * one is a failed scrape's stale marker, and the delta then counts the whole counter.
     */
    static String upThroughout(String job, long rangeMillis, Duration scrapeInterval) {
        return "min_over_time(" + up(job) + "[" + (rangeMillis + scrapeInterval.toMillis()) + "ms])";
    }

    /** Prometheus's own clock, as a vector so the instant-vector reader takes it. */
    static String now() {
        return "vector(time())";
    }

    /**
     * End minus start sample; a series registered inside the window (such as {@code jdbc.on.loop}) counts from 0, which
     * {@code increase} would drop. Counter resets are not handled: the servers do not restart within a run.
     */
    private static String delta(String counter) {
        return "(" + counter + " - ((" + counter + " offset %1$dms) or (" + counter + " * 0)))";
    }
}
