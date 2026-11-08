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
import app.l2nx.load.bot.intent.ResurrectInPlace;
import app.l2nx.load.bot.intent.ReviveAtRestartPoint;
import app.l2nx.load.bot.intent.UseSkill;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.ObjectSeen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

        @ParameterizedTest(name = "run speed {0}")
        @ValueSource(doubles = {0, -120})
        void next_shouldNotWalk_untilTheRunSpeedIsPositive(double runSpeed) {
            bot.runSpeed = runSpeed;
            bot.see(monster(1, CombatBehaviour.ENGAGE_RANGE + 100));
            final CombatBehaviour behaviour = behaviour();

            assertThat(behaviour.next(bot, random)).isNull();
            bot.runSpeed = 120;

            assertThat(behaviour.next(bot, random))
                    .as("no walk was waited out at the unknown speed")
                    .isInstanceOf(MoveTo.class);
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
                    new CombatBehaviour(HOME, SKILL, MONSTERS, true, new ReviveAtRestartPoint());

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
    class Wander {
        private final CombatBehaviour behaviour =
                new CombatBehaviour(HOME, SKILL, MONSTERS, true, new ReviveAtRestartPoint());

        @Test
        void next_shouldWanderAroundTheLoadBotsOwnPosition_whenItStandsAwayFromHome() {
            bot.position = HOME.toBuilder().x(200).z(-90).build();

            final Intent actual = behaviour.next(bot, scripted(0.25, Math.PI / 2));

            assertThat(actual)
                    .isEqualTo(
                            new MoveTo(Position.builder().x(200).y(250).z(-90).build()));
        }

        @Test
        void next_shouldPullTheWanderPointInToTheSpot_whenItFallsOutside() {
            bot.position = HOME.toBuilder().x(400).build();

            final Intent actual = behaviour.next(bot, scripted(0.64, Math.PI / 2));

            assertThat(actual)
                    .as("(400, 400) lies 566 from home and is pulled in on its line from home")
                    .isEqualTo(new MoveTo(HOME.toBuilder().x(353).y(353).build()));
        }

        @Test
        void next_shouldNotWalk_whenTheWanderPointIsTheLoadBotsOwnPosition() {
            bot.position = HOME.toBuilder().x(CombatBehaviour.WANDER_RANGE).build();

            final Intent actual = behaviour.next(bot, scripted(0.04, 0));

            assertThat(actual)
                    .as("(600, 0) is pulled back to the load bot on the edge of the spot")
                    .isNull();
        }

        @Test
        void next_shouldWanderTheOtherWay_afterAMoveWasRefused() {
            assertThat(behaviour.next(bot, scripted(0.25, 0)))
                    .isEqualTo(new MoveTo(HOME.toBuilder().x(250).build()));
            final List<Intent> walk = slots(behaviour, 3);
            bot.position = HOME.toBuilder().x(100).build();
            bot.failed.put(IntentKind.MOVE_TO, 1L);

            final Intent actual = behaviour.next(bot, scripted(0.25));

            assertThat(walk).containsOnlyNulls();
            assertThat(actual).isEqualTo(new MoveTo(HOME.toBuilder().x(-150).build()));
        }

        @Test
        void next_shouldDrawTheDirectionAgain_afterTheTurn() {
            behaviour.next(bot, scripted(0.25, 0));
            slots(behaviour, 3);
            bot.failed.put(IntentKind.MOVE_TO, 1L);
            behaviour.next(bot, scripted(0.25));
            slots(behaviour, 3);
            bot.position = HOME.toBuilder().x(-250).build();

            final Intent actual = behaviour.next(bot, scripted(0.04, Math.PI));

            assertThat(actual).isEqualTo(new MoveTo(HOME.toBuilder().x(-350).build()));
        }
    }

    @Nested
    class Death {
        @Test
        void next_shouldReviveOnce_whenDead() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();

            assertThat(behaviour.next(bot, random)).isEqualTo(new ReviveAtRestartPoint());
            assertThat(behaviour.next(bot, random)).isNull();
        }

        @Test
        void next_shouldAskAgain_whenTheRevivalFailed() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);

            bot.failed.put(IntentKind.REVIVE_AT_RESTART_POINT, 1L);

            assertThat(behaviour.next(bot, random)).isEqualTo(new ReviveAtRestartPoint());
        }

        @Test
        void next_shouldWalkBackHome_thenWaitOutTheWalk_afterTheRevival() {
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);
            bot.dead = false;
            bot.position = HOME.toBuilder().x(1_200).build();
            bot.see(monster(1, 100));

            assertThat(behaviour.next(bot, random)).isEqualTo(new MoveTo(HOME));
            bot.position = HOME;

            assertThat(slots(behaviour, 10)).containsOnlyNulls();
            assertThat(behaviour.next(bot, random)).isEqualTo(new Attack(1));
        }

        @Test
        void next_shouldWaitOutTheWalkAtTheLoadBotsOwnSpeed_whenItRunsSlower() {
            bot.runSpeed = 115;
            bot.dead = true;
            final CombatBehaviour behaviour = behaviour();
            behaviour.next(bot, random);
            bot.dead = false;
            bot.position = HOME.toBuilder().x(1_200).build();
            bot.see(monster(1, 100));

            assertThat(behaviour.next(bot, random)).isEqualTo(new MoveTo(HOME));
            bot.position = HOME;

            assertThat(slots(behaviour, 11)).containsOnlyNulls();
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
        return new CombatBehaviour(HOME, SKILL, MONSTERS, false, new ReviveAtRestartPoint());
    }

    private List<Intent> slots(CombatBehaviour behaviour, int count) {
        final List<Intent> intents = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            intents.add(behaviour.next(bot, random));
        }
        return intents;
    }

    /**
     * Hands out {@code values} in order: {@code nextDouble()} draws the share of the wander range, squared,
     * {@code nextDouble(bound)} the angle in radians.
     */
    private static RandomGenerator scripted(double... values) {
        final Iterator<Double> next = Arrays.stream(values).boxed().toList().iterator();
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException("only doubles are scripted");
            }

            @Override
            public double nextDouble() {
                return next.next();
            }

            @Override
            public double nextDouble(double bound) {
                return next.next();
            }
        };
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
        private double runSpeed = 120;

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
        public double runSpeed() {
            return runSpeed;
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
