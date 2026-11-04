/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.seed;

import app.l2nx.load.app.seed.model.CharacterKit;
import app.l2nx.load.app.seed.model.CharacterPlan;
import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.AdminCommand;
import app.l2nx.load.bot.intent.AdminTeleport;
import app.l2nx.load.bot.intent.CreateCharacter;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.Logout;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import app.l2nx.load.scenario.model.ActionSchedule;
import app.l2nx.load.scenario.model.MeasuredPhase;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

/**
 * Sets the character up with admin commands, which the game code stores on logout. {@code create_item} and
 * {@code move_to} act on the admin, the rest on the target, so the load bot targets itself first. The teleport, last
 * and the only command with an answer, proves the others ran (the world loop runs them in order).
 */
public final class SeedLoadScenario implements LoadScenario {
    static final Duration STEP_INTERVAL = Duration.ofMillis(200);

    private static final Set<IntentKind> REQUIRES = Collections.unmodifiableSet(
            EnumSet.of(IntentKind.CREATE_CHARACTER, IntentKind.ADMIN_COMMAND, IntentKind.ADMIN_TELEPORT));
    private static final ActionSchedule SCHEDULE = ActionSchedule.builder()
            .interval(STEP_INTERVAL)
            .jitter(Duration.ZERO)
            .build();

    private final CharacterPlan plan;
    private final String accountPrefix;
    private final int bots;
    private final AtomicInteger seeded = new AtomicInteger();

    public SeedLoadScenario(CharacterPlan plan, String accountPrefix, int bots) {
        this.plan = plan;
        this.accountPrefix = accountPrefix;
        this.bots = bots;
    }

    public int seeded() {
        return seeded.get();
    }

    @Override
    public String id() {
        return "load-seed-" + plan.id();
    }

    @Override
    public Set<IntentKind> requires() {
        return REQUIRES;
    }

    /** Not measured: the ramp only keeps a seed from counting action samples. */
    @Override
    public MeasuredPhase measuredPhase() {
        return MeasuredPhase.RAMP;
    }

    @Override
    public int characterSlot() {
        return plan.slot();
    }

    @Override
    public CreateCharacter characterToCreate(LoadBotIdentity identity) {
        final CharacterKit kit = CharacterKit.of(identity.index());
        return CreateCharacter.builder()
                .name(plan.characterName(accountPrefix, identity.index()))
                .race(kit.race())
                .classId(kit.baseClassId())
                .build();
    }

    @Override
    public LoadBehaviour behaviour(LoadBotView bot) {
        final int index = bot.identity().index();
        return new Seeding(
                commands(plan.characterName(accountPrefix, index), CharacterKit.of(index)), plan.position(index, bots));
    }

    static List<AdminCommand> commands(String characterName, CharacterKit kit) {
        return List.of(
                new AdminCommand("target " + characterName),
                new AdminCommand("set_level " + CharacterKit.LEVEL),
                new AdminCommand("setclass " + kit.classId()),
                new AdminCommand("give_all_skills"),
                new AdminCommand("create_item " + kit.itemId() + " " + kit.itemCount()),
                new AdminCommand("create_item " + CharacterKit.FEATHER_OF_BLESSING + " " + CharacterKit.FEATHERS));
    }

    private final class Seeding implements LoadBehaviour {
        private final List<AdminCommand> commands;
        private final Position destination;
        private int next;
        private boolean teleportSent;

        private Seeding(List<AdminCommand> commands, Position destination) {
            this.commands = commands;
            this.destination = destination;
        }

        @Override
        public Optional<ActionSchedule> schedule() {
            return Optional.of(SCHEDULE);
        }

        @Override
        public Intent next(LoadBotView bot, RandomGenerator random) {
            if (next < commands.size()) {
                return commands.get(next++);
            }
            if (!teleportSent) {
                teleportSent = true;
                return new AdminTeleport(destination);
            }
            final Position position = bot.position();
            if (position.x() == destination.x() && position.y() == destination.y()) {
                seeded.incrementAndGet();
                return new Logout();
            }
            return bot.failedIntents(IntentKind.ADMIN_TELEPORT) > 0 ? new Logout() : null;
        }
    }
}
