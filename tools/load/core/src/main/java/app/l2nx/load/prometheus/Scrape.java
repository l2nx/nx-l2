/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import io.prometheus.client.Metrics.MetricFamily;
import java.util.Map;
import lombok.Builder;

/**
 * One read of the server's Prometheus endpoint.
 *
 * @param families metric families by their Prometheus name, such as {@code l2nx_engine_pass_seconds}
 * @param text the text exposition kept verbatim for the report; {@code null} for the per-second samples
 */
@Builder(toBuilder = true)
public record Scrape(Map<String, MetricFamily> families, String text) {
    public Scrape {
        families = Map.copyOf(families);
    }
}
