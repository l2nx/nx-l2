/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.CreateCharacter;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.util.Set;

/** Ids carry the {@code load-} prefix. */
public interface LoadScenario {
    String id();

    Set<IntentKind> requires();

    default MeasuredPhase measuredPhase() {
        return MeasuredPhase.STEADY;
    }

    /**
     * The state at which a load bot is done: before {@code IN_GAME} it closes the session there without an error;
     * {@code IN_GAME} plays until the ramp-down, then logs out. Never {@code CLOSING} or {@code STOPPED}.
     */
    default LoadBotState finalState() {
        return LoadBotState.IN_GAME;
    }

    default int characterSlot() {
        return 0;
    }

    /** @return the character a load bot creates before selecting one; {@code null} when it already exists */
    default CreateCharacter characterToCreate(LoadBotIdentity identity) {
        return null;
    }

    /** The behaviour of one load bot, created when it first reaches {@code IN_GAME}. */
    LoadBehaviour behaviour(LoadBotView bot);
}
