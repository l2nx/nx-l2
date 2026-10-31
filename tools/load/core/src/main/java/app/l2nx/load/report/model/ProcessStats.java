/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * JVM and process meters of one server, keyed in {@link ServerStats} by its Prometheus job. A value is {@code null}
 * when the window holds no observation of it.
 */
@Builder(toBuilder = true)
public record ProcessStats(
        Double gcPauseP99Millis,
        Double gcPauseMeanMillis,
        Double gcPauseMaxMillis,
        Double cpuMean,
        Double threadsLiveMax) {}
