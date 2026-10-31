/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record Sample(Map<String, String> labels, double value) {
    /** Prometheus writes every time and duration in seconds. */
    public static final double MILLIS_PER_SECOND = 1_000.0;

    public Sample {
        labels = Map.copyOf(labels);
    }

    public Instant unixTime() {
        return Instant.ofEpochMilli(Math.round(value * MILLIS_PER_SECOND));
    }
}
