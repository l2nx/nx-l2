/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * @param reachedFinalState counted over the whole run, not only the measured phase
 * @param server {@code null} when the run read no server meters (an in-memory protocol)
 */
@Builder(toBuilder = true)
public record RunSummary(
        String runId,
        String scenario,
        String protocol,
        long seed,
        InjectionProfile injection,
        MeasuredPhase measuredPhase,
        LoadBotState finalState,
        Instant startedAt,
        String serverCommit,
        String cpuAffinity,
        RunValidity validity,
        List<InvalidReason> invalidReasons,
        Map<IntentKind, IntentStats> intents,
        Map<LoadBotState, StepStats> steps,
        double generatorLagP99Millis,
        double timeoutShare,
        double rejectedShare,
        double stoppedShare,
        int reachedFinalState,
        ServerStats server) {

    public RunSummary {
        require("measured phase", measuredPhase);
        require("final state", finalState);
        require("steps", steps);
    }

    private static void require(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("The " + name + " of a summary is required");
        }
    }
}
