/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeedLoadScenarioTest {
    private static final int BOTS = 10;
    private static final LoadBotIdentity IDENTITY = LoadBotIdentity.builder()
            .index(2)
            .account("load2")
            .password("secret")
            .build();

    private final SeedLoadScenario scenario = new SeedLoadScenario(CharacterPlan.HUNTING, "load", BOTS);
    private final SplittableRandom random = new SplittableRandom(1);

    @Nested
    class Kit {
        @ParameterizedTest(name = "load bot {0}")
        @CsvSource({"0, 0", "1, 0", "2, 10", "3, 10", "4, 0"})
        void of_shouldGiveEachTeamBothKits(int index, int expectedBaseClass) {
            assertThat(CharacterKit.of(index).baseClassId()).isEqualTo(expectedBaseClass);
        }
    }

    @Nested
    class Character {
        @Test
        void characterToCreate_shouldNameTheCharacterOfThePlan() {
            final CreateCharacter actual = scenario.characterToCreate(IDENTITY);

            assertThat(actual)
                    .isEqualTo(CreateCharacter.builder()
                            .name("loadh2")
                            .race(0)
                            .classId(10)
                            .build());
        }

        @Test
        void characterSlot_shouldBeTheSlotOfThePlan() {
            assertThat(scenario.characterSlot()).isEqualTo(CharacterPlan.HUNTING.slot());
        }
    }

    @Nested
    class Behaviour {
        @Test
        void next_shouldTargetTheCharacterFirst_andTeleportLast() {
            final LoadBehaviour behaviour = scenario.behaviour(bot(null));
            final Position destination = CharacterPlan.HUNTING.position(2, BOTS);

            final List<Intent> actual = new ArrayList<>();
            for (int slot = 0; slot < 7; slot++) {
                actual.add(behaviour.next(bot(null), random));
            }

            assertThat(actual)
                    .containsExactly(
                            new AdminCommand("target loadh2"),
                            new AdminCommand("set_level 75"),
                            new AdminCommand("setclass 12"),
                            new AdminCommand("give_all_skills"),
                            new AdminCommand("create_item 2509 1000"),
                            new AdminCommand("create_item 10649 1000"),
                            new AdminTeleport(destination));
        }

        @Test
        void next_shouldLogOutAndCount_onceTheTeleportLanded() {
            final LoadBehaviour behaviour = afterTheTeleport();
            final Position destination = CharacterPlan.HUNTING.position(2, BOTS);

            final Intent actual =
                    behaviour.next(bot(destination.toBuilder().z(-3_000).build()), random);

            assertThat(actual).isEqualTo(new Logout());
            assertThat(scenario.seeded()).isEqualTo(1);
        }

        @Test
        void next_shouldWait_whileTheTeleportHasNoAnswer() {
            final LoadBehaviour behaviour = afterTheTeleport();

            assertThat(behaviour.next(bot(Position.builder().x(1).y(2).z(3).build()), random))
                    .isNull();
        }

        @Test
        void next_shouldLogOutWithoutCounting_whenTheTeleportFailed() {
            final LoadBehaviour behaviour = afterTheTeleport();
            final LoadBotView bot = bot(Position.builder().x(1).y(2).z(3).build());
            when(bot.failedIntents(IntentKind.ADMIN_TELEPORT)).thenReturn(1L);

            assertThat(behaviour.next(bot, random)).isEqualTo(new Logout());
            assertThat(scenario.seeded()).isZero();
        }

        private LoadBehaviour afterTheTeleport() {
            final LoadBehaviour behaviour = scenario.behaviour(bot(null));
            for (int slot = 0; slot < 7; slot++) {
                behaviour.next(bot(null), random);
            }
            return behaviour;
        }
    }

    private static LoadBotView bot(Position position) {
        final LoadBotView bot = mock(LoadBotView.class);
        when(bot.identity()).thenReturn(IDENTITY);
        when(bot.position()).thenReturn(position);
        return bot;
    }
}
