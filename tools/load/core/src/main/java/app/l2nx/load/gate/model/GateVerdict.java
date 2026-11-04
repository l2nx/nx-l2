/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.gate.model;

import java.util.List;
import lombok.Builder;

/** @param baselineRunIds the valid baseline runs compared with; empty while there is no baseline */
@Builder(toBuilder = true)
public record GateVerdict(
        String scenario,
        List<String> baselineRunIds,
        List<String> validRunIds,
        List<String> invalidRunIds,
        List<CriterionResult> results) {

    public GateVerdict {
        baselineRunIds = List.copyOf(baselineRunIds);
        validRunIds = List.copyOf(validRunIds);
        invalidRunIds = List.copyOf(invalidRunIds);
        results = List.copyOf(results);
    }

    public boolean passed() {
        return results.stream().allMatch(CriterionResult::passed);
    }
}
