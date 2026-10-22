/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

import lombok.Builder;

/**
 * Values are milliseconds for the p99 criteria, fractions for the shares and a run count for
 * {@link GateCriterion#VALID_RUNS}.
 *
 * @param subject the loop, intent kind or share checked, {@code null} when the check covers the whole run set
 * @param measured the median over the valid runs, {@code null} when a meter is missing in a run or the check does not
 *     apply
 * @param threshold the limit, {@code null} when the baseline gives none
 * @param reason why there is no measurement (meter missing, or not applicable to the final state); {@code null} when
 *     measured
 */
@Builder(toBuilder = true)
public record CriterionResult(
        GateCriterion criterion, String subject, Double measured, Double threshold, boolean passed, String reason) {}
