/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import lombok.Builder;

/**
 * The highest level, counted from the first up, that stayed within the gate settings; a level row holds the worst
 * values over its loops, so judging them judges every loop.
 *
 * @param bots {@code null} when no level passed; {@code reason} says why
 */
@Builder(toBuilder = true)
public record Capacity(Integer bots, CapacityLimit limitedBy, String reason) {}
