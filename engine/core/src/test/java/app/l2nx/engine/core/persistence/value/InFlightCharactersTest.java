/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.identity.EntityIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InFlightCharactersTest {
    private final InFlightCharacters inFlight = new InFlightCharacters();
    private final UUID characterId = EntityIds.next();

    @Nested
    class WhenIdle {
        @Test
        void whenIdle_shouldRunAtOnce_whenNothingIsInFlight() {
            final List<String> ran = new ArrayList<>();

            inFlight.whenIdle(characterId, () -> ran.add("save"));

            assertThat(ran).containsExactly("save");
        }

        @Test
        void whenIdle_shouldRunInArrivalOrder_afterTheLastTransactionEnded() {
            final List<String> ran = new ArrayList<>();
            inFlight.begin(characterId);
            inFlight.begin(characterId);
            inFlight.whenIdle(characterId, () -> ran.add("save"));
            inFlight.whenIdle(characterId, () -> ran.add("logout"));

            inFlight.end(characterId);
            assertThat(ran).isEmpty();
            inFlight.end(characterId);

            assertThat(ran).containsExactly("save", "logout");
        }

        @Test
        void end_shouldRunTheLaterWork_whenAnEarlierTaskFails() {
            final List<String> ran = new ArrayList<>();
            inFlight.begin(characterId);
            inFlight.whenIdle(characterId, () -> {
                throw new IllegalStateException("broken");
            });
            inFlight.whenIdle(characterId, () -> ran.add("logout save"));

            inFlight.end(characterId);

            assertThat(ran).containsExactly("logout save");
            assertThat(inFlight.isInFlight(characterId)).isFalse();
        }

        @Test
        void whenIdle_shouldKeepAFailureFromTheCaller_whenItRunsAtOnce() {
            inFlight.whenIdle(characterId, () -> {
                throw new IllegalStateException("broken");
            });

            assertThat(inFlight.isInFlight(characterId)).isFalse();
        }
    }

    @Nested
    class Characters {
        @Test
        void characters_shouldListOnlyTheCharactersInFlight() {
            final UUID other = EntityIds.next();
            inFlight.begin(characterId);
            inFlight.begin(other);
            inFlight.end(other);

            assertThat(inFlight.characters()).containsExactly(characterId);
        }
    }
}
