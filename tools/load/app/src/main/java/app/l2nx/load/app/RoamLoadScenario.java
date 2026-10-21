/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.intent.Say;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.scenario.ActionSchedule;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public final class RoamLoadScenario implements LoadScenario {
    public static final String ID = "load-roam";

    static final Duration ACTION_INTERVAL = Duration.ofSeconds(2);
    static final Duration ACTION_JITTER = Duration.ofMillis(500);
    static final double SAY_SHARE = 0.25;
    static final int ROAM_RADIUS = 500;
    static final String SAY_TEXT = "Roaming around";

    private static final Set<IntentKind> REQUIRES =
            Collections.unmodifiableSet(EnumSet.of(IntentKind.MOVE_TO, IntentKind.SAY));
    private static final ActionSchedule SCHEDULE = ActionSchedule.builder()
            .interval(ACTION_INTERVAL)
            .jitter(ACTION_JITTER)
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<IntentKind> requires() {
        return REQUIRES;
    }

    @Override
    public LoadBehaviour behaviour(LoadBotView bot) {
        return new Roam(bot.position());
    }

    private record Roam(Position home) implements LoadBehaviour {
        @Override
        public ActionSchedule schedule() {
            return SCHEDULE;
        }

        @Override
        public Intent next(LoadBotView bot, RandomGenerator random) {
            if (random.nextDouble() < SAY_SHARE) {
                return new Say(SAY_TEXT);
            }
            // The square root spreads the points evenly over the disc instead of crowding its centre.
            final double distance = ROAM_RADIUS * Math.sqrt(random.nextDouble());
            final double angle = random.nextDouble(2 * Math.PI);
            return new MoveTo(Position.builder()
                    .x(home.x() + (int) Math.round(distance * Math.cos(angle)))
                    .y(home.y() + (int) Math.round(distance * Math.sin(angle)))
                    .z(home.z())
                    .build());
        }
    }
}
