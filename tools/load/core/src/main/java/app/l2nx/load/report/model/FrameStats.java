/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * Milliseconds from {@code channelRead} of a frame's bytes to the end of its handling: high values mean a generator
 * slowed by inbound traffic, whatever the client latencies say of the server.
 */
@Builder(toBuilder = true)
public record FrameStats(long frames, double p50Millis, double p99Millis, double p999Millis, double maxMillis) {}
