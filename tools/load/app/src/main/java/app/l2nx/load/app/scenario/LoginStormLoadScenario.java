/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.util.Set;

/**
 * A load bot whose final state is {@code IN_GAME} stays there without acting until the ramp-down.
 */
public final class LoginStormLoadScenario implements LoadScenario {
    public static final String ID = "load-login-storm";

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
        return LoadBehaviour.idle();
    }
}
