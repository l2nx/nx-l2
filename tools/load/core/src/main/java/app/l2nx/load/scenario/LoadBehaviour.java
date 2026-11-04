/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.scenario.model.ActionSchedule;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Runs on the load bot's event loop, so it needs no synchronisation. */
public interface LoadBehaviour {
    /** Empty: the load bot stays in the game without acting until the ramp-down. */
    Optional<ActionSchedule> schedule();

    /**
     * @param random the load bot's own generator: the only source of randomness, so a seed reproduces the inputs
     * @return {@code null} to skip this slot; a {@code Logout} ends the load bot's play ahead of the ramp-down
     */
    Intent next(LoadBotView bot, RandomGenerator random);

    static LoadBehaviour idle() {
        return Idle.INSTANCE;
    }
}
