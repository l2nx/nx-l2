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

enum Idle implements LoadBehaviour {
    INSTANCE;

    @Override
    public Optional<ActionSchedule> schedule() {
        return Optional.empty();
    }

    @Override
    public Intent next(LoadBotView bot, RandomGenerator random) {
        throw new IllegalStateException("An idle load bot has no action slots");
    }
}
