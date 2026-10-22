/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import app.l2nx.load.scenario.model.ActionSchedule;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Load bots arriving over the ramp, its measured phase: the time of every lifecycle step up to the final state. A load
 * bot whose final state is {@code IN_GAME} stays there without acting until the ramp-down.
 */
public final class LoginStormLoadScenario implements LoadScenario {
    public static final String ID = "load-login-storm";

    // The schedule needs a positive interval: one slot a day wakes an idle load bot at most once in a run, and skips.
    private static final Duration IDLE_INTERVAL = Duration.ofDays(1);

    private static final ActionSchedule IDLE_SCHEDULE = ActionSchedule.builder()
            .interval(IDLE_INTERVAL)
            .jitter(Duration.ZERO)
            .build();
    private static final LoadBehaviour IDLE = new Idle();

    private final LoadBotState finalState;

    public LoginStormLoadScenario(LoadBotState finalState) {
        this.finalState = finalState;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<IntentKind> requires() {
        return Set.of();
    }

    @Override
    public MeasuredPhase measuredPhase() {
        return MeasuredPhase.RAMP;
    }

    @Override
    public LoadBotState finalState() {
        return finalState;
    }

    @Override
    public LoadBehaviour behaviour(LoadBotView bot) {
        return IDLE;
    }

    private static final class Idle implements LoadBehaviour {
        @Override
        public ActionSchedule schedule() {
            return IDLE_SCHEDULE;
        }

        @Override
        public Intent next(LoadBotView bot, RandomGenerator random) {
            return null;
        }
    }
}
