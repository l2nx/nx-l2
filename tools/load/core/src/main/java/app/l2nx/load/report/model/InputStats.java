/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * Input time of one input class over the measured phase, every outcome, in milliseconds. A boxed value is {@code null}
 * when the window holds no observation it could come from.
 */
@Builder(toBuilder = true)
public record InputStats(long count, Double p50Millis, Double p99Millis, Double maxMillis) {}
