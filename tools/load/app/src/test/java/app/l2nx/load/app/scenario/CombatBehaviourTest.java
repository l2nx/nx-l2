/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.Attack;
import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.MoveTo;
import app.l2nx.load.bot.intent.ResurrectAtRestartPoint;
import app.l2nx.load.bot.intent.ResurrectInPlace;
import app.l2nx.load.bot.intent.UseSkill;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.ObjectSeen;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CombatBehaviourTest {
    private static final int SKILL = 190;
    private static final Position HOME = Position.builder().x(0).y(0).z(-100).build();
    private static final Predicate<ObjectSeen> MONSTERS = seen -> seen.kind() == ObjectSeen.Kind.MONSTER;

    private final FakeBot bot = new FakeBot();
    private final SplittableRandom random = new SplittableRandom(1);

    @Nested
    class Fight {
        @Test
        void next_shouldAttackTheNearestLiveEnemy_whenThereIsNoTarget() {
            bot.see(monster(1, 400));
            bot.see(monster(2, 100));
            bot.see(monster(3, 50).toBuilder().dead(true).build());
            bot.see(npc(4, 10));

            assertThat(behaviour().next(bot, random)).isEqualTo(new Attack(2));
        }

        @Test
        void next_shouldNotAttackAgain_whileTheTargetLives() {
            bot.see(monster(1, 100));
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            final List<Intent> actual = slots(behaviour, CombatBehaviour.SKILL_SLOTS * 2);

            assertThat(actual).noneMatch(Attack.class::isInstance);
        }

        @Test
        void next_shouldCastEverySkillSlots_afterTheFirstOnesOfTheFight() {
            bot.see(monster(1, 100));
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            final List<Intent> actual = slots(behaviour, CombatBehaviour.SKILL_SLOTS * 2);

            assertThat(actual).filteredOn(UseSkill.class::isInstance).hasSize(2).allMatch(new UseSkill(SKILL)::equals);
        }

        @Test
        void next_shouldNotCast_whenTheManaIsTooLow() {
            bot.see(monster(1, 100));
            bot.mp = CombatBehaviour.MIN_MP - 1;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            assertThat(slots(behaviour, CombatBehaviour.SKILL_SLOTS * 2)).noneMatch(UseSkill.class::isInstance);
        }

        @Test
        void next_shouldTakeTheNextEnemy_whenTheTargetDies() {
            bot.see(monster(1, 100));
            bot.see(monster(2, 200));
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            bot.see(monster(1, 100).toBuilder().dead(true).build());

            assertThat(behaviour.next(bot, random)).isEqualTo(new Attack(2));
        }

        @Test
        void next_shouldLeaveTheTarget_whenTheAttackOnItFailed() {
            bot.see(monster(1, 100));
            bot.see(monster(2, 200));
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            bot.failed.put(IntentKind.ATTACK, 1L);

            assertThat(behaviour.next(bot, random))
                    .as("a corpse that came into view looks alive")
                    .isEqualTo(new Attack(2));
        }

        @Test
        void next_shouldLeaveTheTarget_whenACastOnItFailed() {
            bot.see(monster(1, 100));
            bot.see(monster(2, 200));
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            bot.failed.put(IntentKind.USE_SKILL, 1L);

            assertThat(behaviour.next(bot, random))
                    .as("the server refuses without saying why")
                    .isEqualTo(new Attack(2));
        }

        @Test
        void next_shouldWalkUpFirst_whenTheEnemyIsOutOfRange() {
            bot.see(monster(1, CombatBehaviour.ENGAGE_RANGE + 100));

            final Intent actual = behaviour().next(bot, random);

            assertThat(actual).isInstanceOf(MoveTo.class);
        }
    }

    @Nested
    class NoEnemy {
        @Test
        void next_shouldWait_whenTheBehaviourDoesNotWander() {
            assertThat(behaviour().next(bot, random)).isNull();
        }

        @Test
        void next_shouldWalkAroundHome_whenTheBehaviourWanders() {
            final CombatBehaviour behaviour =
                    new CombatBehaviour(HOME, SKILL, MONSTERS, true, new ResurrectAtRestartPoint());

            final Intent actual = behaviour.next(bot, random);

            assertThat(actual)
                    .isInstanceOfSatisfying(
                            MoveTo.class,
                            move -> assertThat(Math.hypot(
                                            move.destination().x(),
                                            move.destination().y()))
                                    .isLessThanOrEqualTo(CombatBehaviour.WANDER_RANGE + 1));
        }
    }

    @Nested
    class Death {
        @Test
        void next_shouldResurrectOnce_whenDead() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();

            assertThat(behaviour.next(bot, random)).isEqualTo(new ResurrectAtRestartPoint());
            assertThat(behaviour.next(bot, random)).isNull();
        }

        @Test
        void next_shouldAskAgain_whenTheResurrectionFailed() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            bot.failed.put(IntentKind.RESURRECT_AT_RESTART_POINT, 1L);

            assertThat(behaviour.next(bot, random)).isEqualTo(new ResurrectAtRestartPoint());
        }

        @Test
        void next_shouldWalkBackHome_thenWaitOutTheWalk_afterTheResurrection() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);
            bot.dead = false;
            bot.position = HOME.toBuilder().x(1_200).build();
            bot.see(monster(1, 100));

            assertThat(behaviour.next(bot, random)).isEqualTo(new MoveTo(HOME));
            bot.position = HOME;

            final int walkSlots = (int) Math.ceil(1_200 / CombatBehaviour.RUN_SPEED);
            assertThat(slots(behaviour, walkSlots)).containsOnlyNulls();
            assertThat(behaviour.next(bot, random)).isEqualTo(new Attack(1));
        }

        @Test
        void next_shouldWait_whileACastRuns() {
            bot.casting = true;
            bot.see(monster(1, 100));

            assertThat(behaviour().next(bot, random)).isNull();
        }

        @Test
        void next_shouldFightOnTheSpot_afterAResurrectionInPlace() {
            bot.dead = true;
            final CombatBehaviour behaviour = new CombatBehaviour(HOME, SKILL, MONSTERS, false, new ResurrectInPlace());
            assertThat(behaviour.next(bot, random)).isEqualTo(new ResurrectInPlace());
            bot.dead = false;
            bot.position = HOME.toBuilder().x(1_200).build();
            bot.see(monster(1, 1_250));

            assertThat(behaviour.next(bot, random)).isEqualTo(new Attack(1));
        }

        @Test
        void next_shouldWalkInStepsTheServerAccepts_whenTheRestartPointIsFar() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);
            bot.dead = false;
            bot.position = HOME.toBuilder().x(CombatBehaviour.MAX_WALK * 2).build();

            final Intent actual = behaviour.next(bot, random);

            assertThat(actual)
                    .isEqualTo(new MoveTo(
                            HOME.toBuilder().x(CombatBehaviour.MAX_WALK).build()));
        }
    }

    private CombatBehaviour behaviour() {
        return new CombatBehaviour(HOME, SKILL, MONSTERS, false, new ResurrectAtRestartPoint());
    }

    private List<Intent> slots(CombatBehaviour behaviour, int count) {
        final List<Intent> intents = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            intents.add(behaviour.next(bot, random));
        }
        return intents;
    }

    private static ObjectSeen monster(int objectId, int x) {
        return ObjectSeen.builder()
                .objectId(objectId)
                .kind(ObjectSeen.Kind.MONSTER)
                .position(HOME.toBuilder().x(x).build())
                .build();
    }

    private static ObjectSeen npc(int objectId, int x) {
        return monster(objectId, x).toBuilder().kind(ObjectSeen.Kind.NPC).build();
    }

    private static final class FakeBot implements LoadBotView {
        private final Map<Integer, ObjectSeen> visible = new LinkedHashMap<>();
        private final Map<IntentKind, Long> failed = new EnumMap<>(IntentKind.class);
        private Position position = HOME;
        private boolean dead;
        private boolean casting;
        private int mp = 1_000;

        private void see(ObjectSeen seen) {
            visible.put(seen.objectId(), seen);
        }

        @Override
        public LoadBotIdentity identity() {
            return LoadBotIdentity.builder()
                    .index(0)
                    .account("load0")
                    .password("secret")
                    .build();
        }

        @Override
        public LoadBotState state() {
            return LoadBotState.IN_GAME;
        }

        @Override
        public int selfObjectId() {
            return 1;
        }

        @Override
        public Position position() {
            return position;
        }

        @Override
        public int hp() {
            return 1_000;
        }

        @Override
        public int mp() {
            return mp;
        }

        @Override
        public boolean dead() {
            return dead;
        }

        @Override
        public Collection<ObjectSeen> visibleObjects() {
            return visible.values();
        }

        @Override
        public ObjectSeen visibleObject(int objectId) {
            return visible.get(objectId);
        }

        @Override
        public boolean casting() {
            return casting;
        }

        @Override
        public long failedIntents(IntentKind kind) {
            return failed.getOrDefault(kind, 0L);
        }
    }
}
