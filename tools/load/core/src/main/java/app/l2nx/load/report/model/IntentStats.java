/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * Client-side results of one intent kind over the steady window; latencies in milliseconds from the intended time.
 *
 * @param errors intents the server rejected and pending intents lost because their load bot stopped on an error
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
