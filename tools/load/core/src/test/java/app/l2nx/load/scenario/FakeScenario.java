/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.scenario.model.ActionSchedule;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class FakeScenario implements LoadScenario {
    private final ActionSchedule schedule;
    private final Runnable onAction;
    private MeasuredPhase measuredPhase = MeasuredPhase.STEADY;
    private LoadBotState finalState = LoadBotState.IN_GAME;
    private boolean idle;

    public FakeScenario(Duration interval, Duration jitter) {
        this(interval, jitter, () -> {});
    }

    public FakeScenario(Duration interval, Duration jitter, Runnable onAction) {
        schedule = ActionSchedule.builder().interval(interval).jitter(jitter).build();
        this.onAction = onAction;
    }

    public FakeScenario measuredPhase(MeasuredPhase phase) {
        measuredPhase = phase;
        return this;
    }

    public FakeScenario idle() {
        idle = true;
        return this;
    }

    public FakeScenario finalState(LoadBotState state) {
        finalState = state;
        return this;
    }

    @Override
    public MeasuredPhase measuredPhase() {
        return measuredPhase;
    }

    @Override
    public LoadBotState finalState() {
        return finalState;
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
        if (idle) {
            return LoadBehaviour.idle();
        }
        return new LoadBehaviour() {
            @Override
            public Optional<ActionSchedule> schedule() {
                return Optional.of(schedule);
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
