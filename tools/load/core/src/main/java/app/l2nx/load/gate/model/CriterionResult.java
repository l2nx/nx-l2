/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

import lombok.Builder;

/**
 * Values are milliseconds for the p99 criteria, fractions for the shares, a run count for
 * {@link GateCriterion#VALID_RUNS} and a count for {@link GateCriterion#OWNERSHIP_VIOLATIONS}.
 *
 * @param subject the loop, intent kind, lifecycle step or share checked, {@code null} when the check covers the whole
 *     run set
 * @param measured the median over the valid runs, {@code null} when a meter is missing in a run or the check does not
 *     apply
 * @param threshold {@code null} when the baseline gives none
 */
@Builder(toBuilder = true)
public record CriterionResult(
        GateCriterion criterion, String subject, Double measured, Double threshold, boolean passed, String reason) {}
