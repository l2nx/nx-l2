/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Attack;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.intent.ReviveAtRestartPoint;
import app.l2nx.load.bot.intent.UseSkill;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.model.ActionSchedule;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Fights the nearest enemy near {@code home}. {@code Attack} goes out only with no live target: the server repeats the
 * hits itself, so {@code UseSkill} is the steady intent. One action a second stays far under the {@code Action} flood
 * protector, which lets one click through per 100 ms; an {@code Attack} on a new target is two clicks.
 */
final class CombatBehaviour implements LoadBehaviour {
    static final Duration ACTION_INTERVAL = Duration.ofSeconds(1);
    static final Duration ACTION_JITTER = Duration.ofMillis(250);

    /** Above the longest cast and its reuse: Prominence, 4 s and 1.2 s. A skill still cooling down is refused. */
    static final int SKILL_SLOTS = 8;

    static final int ENGAGE_SLOTS = 2;

    static final int MIN_MP = 100;

    /** The approach counts in the latency of an {@code Attack}, so a target is walked up to first: 2.5 s of running. */
    static final int ENGAGE_RANGE = 300;

    static final int HOME_RANGE = 300;
    static final int WANDER_RANGE = 500;

    /** Below the longest move the server accepts, 9,900 units. */
    static final int MAX_WALK = 8_000;

    static final double RUN_SPEED = 120.0;

    private static final int NO_TARGET = 0;
    private static final ActionSchedule SCHEDULE = ActionSchedule.builder()
            .interval(ACTION_INTERVAL)
            .jitter(ACTION_JITTER)
            .build();

    private final Position home;
    private final int skillId;
    private final Predicate<ObjectSeen> enemy;
    private final boolean wanderWithoutEnemy;
    private final Intent revival;
    private int target = NO_TARGET;
    private int refusedTarget = NO_TARGET;
    private long failedActions;
    private long failedRevivals;
    private boolean reviving;
    private boolean returning;
    private int walkSlots;
    private int skillWait;

    CombatBehaviour(
            Position home, int skillId, Predicate<ObjectSeen> enemy, boolean wanderWithoutEnemy, Intent revival) {
        this.home = home;
        this.skillId = skillId;
        this.enemy = enemy;
        this.wanderWithoutEnemy = wanderWithoutEnemy;
        this.revival = revival;
    }

    @Override
    public Optional<ActionSchedule> schedule() {
        return Optional.of(SCHEDULE);
    }

    @Override
    public Intent next(LoadBotView bot, RandomGenerator random) {
        if (bot.dead()) {
            return revive(bot);
        }
        if (reviving) {
            reviving = false;
            returning = revival instanceof ReviveAtRestartPoint;
            target = NO_TARGET;
        }
        if (walkSlots > 0) {
            walkSlots--;
            return null;
        }
        if (returning) {
            if (distance(bot.position(), home) > HOME_RANGE) {
                return walkTo(bot.position(), home);
            }
            returning = false;
        }
        return fight(bot, random);
    }

    private Intent revive(LoadBotView bot) {
        final long failed = bot.failedIntents(revival.kind());
        if (reviving && failed == failedRevivals) {
            return null;
        }
        reviving = true;
        failedRevivals = failed;
        walkSlots = 0;
        return revival;
    }

    private Intent fight(LoadBotView bot, RandomGenerator random) {
        skillWait = Math.max(0, skillWait - 1);
        final long failed = bot.failedIntents(IntentKind.ATTACK) + bot.failedIntents(IntentKind.USE_SKILL);
        if (failed != failedActions) {
            // The server refuses a target without saying why, so the refusal is what ends the fight with it.
            failedActions = failed;
            refusedTarget = target;
            target = NO_TARGET;
        }
        // The server answers an attack during a cast with no more than ActionFailed, so it would only time out.
        if (bot.casting()) {
            return null;
        }
        if (liveTarget(bot)) {
            if (skillWait == 0 && bot.mp() >= MIN_MP) {
                skillWait = SKILL_SLOTS;
                return new UseSkill(skillId);
            }
            return null;
        }
        final ObjectSeen nearest = nearestEnemy(bot);
        if (nearest == null) {
            target = NO_TARGET;
            return wanderWithoutEnemy ? wander(bot, random) : null;
        }
        if (distance(bot.position(), nearest.position()) > ENGAGE_RANGE) {
            target = NO_TARGET;
            return walkTo(bot.position(), nearest.position());
        }
        target = nearest.objectId();
        skillWait = Math.max(skillWait, ENGAGE_SLOTS);
        return new Attack(target);
    }

    private boolean liveTarget(LoadBotView bot) {
        if (target == NO_TARGET) {
            return false;
        }
        final ObjectSeen seen = bot.visibleObject(target);
        return seen != null && !seen.dead();
    }

    private ObjectSeen nearestEnemy(LoadBotView bot) {
        final Position here = bot.position();
        ObjectSeen nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ObjectSeen seen : bot.visibleObjects()) {
            if (seen.dead() || seen.objectId() == refusedTarget || !enemy.test(seen)) {
                continue;
            }
            final double distance = distance(here, seen.position());
            if (distance < nearestDistance) {
                nearest = seen;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private Intent wander(LoadBotView bot, RandomGenerator random) {
        final double distance = WANDER_RANGE * Math.sqrt(random.nextDouble());
        final double angle = random.nextDouble(2 * Math.PI);
        return walkTo(
                bot.position(),
                home.toBuilder()
                        .x(home.x() + (int) Math.round(distance * Math.cos(angle)))
                        .y(home.y() + (int) Math.round(distance * Math.sin(angle)))
                        .build());
    }

    private Intent walkTo(Position from, Position to) {
        final double distance = distance(from, to);
        final double share = distance > MAX_WALK ? MAX_WALK / distance : 1;
        final double walked = distance * share;
        walkSlots = (int) Math.ceil(walked / (RUN_SPEED * ACTION_INTERVAL.toSeconds()));
        return new MoveTo(to.toBuilder()
                .x(from.x() + (int) Math.round((to.x() - from.x()) * share))
                .y(from.y() + (int) Math.round((to.y() - from.y()) * share))
                .build());
    }

    private static double distance(Position from, Position to) {
        return Math.hypot(to.x() - from.x(), to.y() - from.y());
    }
}
