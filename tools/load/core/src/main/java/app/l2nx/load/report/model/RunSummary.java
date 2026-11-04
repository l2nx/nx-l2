/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * @param cpuAffinity as the start script declared it, for the services {@code environment} cannot see from here
 * @param reachedFinalState counted over the whole run, not only the measured phase
 * @param inboundFrames {@code null} when the protocol reported no frame
 * @param serverStartedAt {@code process_start_time_seconds} of each server by Prometheus job, read before the run;
 *     {@code null} when the run read no server meters
 * @param serverWindow {@code null} when the run read no server meters
 * @param server {@code null} when the run read no server meters or they could not be queried; then
 *     {@code serverWindow} still names the window
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
        Duration intentTimeout,
        Duration lifecycleTimeout,
        Instant startedAt,
        String serverCommit,
        RunEnvironment environment,
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
        double notAtFinalStateShare,
        int loggedOut,
        FrameStats inboundFrames,
        Map<String, Instant> serverStartedAt,
        ServerWindow serverWindow,
        ServerStats server) {

    public RunSummary {
        require("measured phase", measuredPhase);
        require("final state", finalState);
        require("steps", steps);
        serverStartedAt = ServerStats.sortedCopy(serverStartedAt);
    }

    private static void require(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("The " + name + " of a summary is required");
        }
    }
}
