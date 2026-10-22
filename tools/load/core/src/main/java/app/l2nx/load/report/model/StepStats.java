/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * Lifecycle steps of one state over the measured phase: milliseconds a load bot spent in the state before the next
 * one. A load bot enters {@code LOGIN_CONNECT} at its intended start.
 *
 * @param count steps counted, those whose state was entered in the measured phase
 */
@Builder(toBuilder = true)
public record StepStats(long count, double p50Millis, double p99Millis, double p999Millis, double maxMillis) {}
