/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.injection.InjectionProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * The report of one run, written as {@code summary.json} and read back by the gate.
 *
 * @param timeoutShare intents that timed out over intents sent in the steady window
 * @param rejectedShare intents the server rejected or lost to a load bot error over intents sent in the steady window
 * @param stoppedShare load bots stopped on an error over all load bots
 * @param server {@code null} when the run read no server meters (an in-memory protocol)
 */
@Builder(toBuilder = true)
public record RunSummary(
        String runId,
        String scenario,
        String protocol,
        long seed,
        InjectionProfile injection,
        Instant startedAt,
        String serverCommit,
        String cpuAffinity,
        RunValidity validity,
        List<InvalidReason> invalidReasons,
        Map<IntentKind, IntentStats> intents,
        double generatorLagP99Millis,
        double timeoutShare,
        double rejectedShare,
        double stoppedShare,
        ServerStats server) {}
