/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record GateVerdict(
        String scenario,
        String baselineRunId,
        List<String> validRunIds,
        List<String> invalidRunIds,
        List<CriterionResult> results) {

    public GateVerdict {
        validRunIds = List.copyOf(validRunIds);
        invalidRunIds = List.copyOf(invalidRunIds);
        results = List.copyOf(results);
    }

    public boolean passed() {
        return results.stream().allMatch(CriterionResult::passed);
    }
}
