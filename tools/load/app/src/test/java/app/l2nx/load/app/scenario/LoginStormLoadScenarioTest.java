/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.model.MeasuredPhase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoginStormLoadScenarioTest {
    @Nested
    class Definition {
        @Test
        void measuredPhase_shouldBeTheRamp() {
            assertThat(new LoginStormLoadScenario(LoadBotState.IN_GAME).measuredPhase())
                    .isEqualTo(MeasuredPhase.RAMP);
        }

        @Test
        void requires_shouldBeEmpty() {
            assertThat(new LoginStormLoadScenario(LoadBotState.IN_GAME).requires())
                    .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"SERVER_LIST", "GAME_CONNECT", "IN_GAME"})
        void finalState_shouldBeTheGivenOne(LoadBotState expected) {
            assertThat(new LoginStormLoadScenario(expected).finalState()).isEqualTo(expected);
        }
    }

    @Nested
    class Behaviour {
        @Test
        void schedule_shouldBeEmpty() {
            final LoadBehaviour behaviour = new LoginStormLoadScenario(LoadBotState.IN_GAME).behaviour(null);

            assertThat(behaviour.schedule()).isEmpty();
        }
    }
}
