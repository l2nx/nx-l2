/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.model.Position;
import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class FakeScenario implements LoadScenario {
    private final ActionSchedule schedule;
    private final Runnable onAction;

    public FakeScenario(Duration interval, Duration jitter) {
        this(interval, jitter, () -> {});
    }

    public FakeScenario(Duration interval, Duration jitter, Runnable onAction) {
        schedule = ActionSchedule.builder().interval(interval).jitter(jitter).build();
        this.onAction = onAction;
    }

    @Override
    public String id() {
        return "load-fake";
    }

    @Override
    public Set<IntentKind> requires() {
        return Set.of(IntentKind.MOVE_TO);
    }

    @Override
    public LoadBehaviour behaviour(LoadBotView bot) {
        return new LoadBehaviour() {
            @Override
            public ActionSchedule schedule() {
                return schedule;
            }

            @Override
            public Intent next(LoadBotView view, RandomGenerator random) {
                onAction.run();
                final Position here = view.position();
                return new MoveTo(here.toBuilder()
                        .x(here.x() + random.nextInt(-500, 501))
                        .y(here.y() + random.nextInt(-500, 501))
                        .build());
            }
        };
    }
}
