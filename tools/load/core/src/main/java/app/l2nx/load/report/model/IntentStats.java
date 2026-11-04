/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * Latencies in milliseconds from the intended time; a timed-out intent of the measured phase enters them at the intent
 * timeout. An arrival intent that failed outside the measured phase counts in {@code sent} and in its failure only.
 */
@Builder(toBuilder = true)
public record IntentStats(
        long sent,
        long acknowledged,
        long timeouts,
        long errors,
        double p50Millis,
        double p99Millis,
        double p999Millis,
        double maxMillis) {}
