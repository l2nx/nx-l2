/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.olympiad;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.olympiad.OlympiadFightRepository;
import app.l2nx.highfive.repository.olympiad.model.OlympiadFight;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OlympiadFightRepositoryTest extends RepositoryTest {
    private final OlympiadFightRepository repository = new OlympiadFightRepository(JDBC, registry);

    private OlympiadFight fight(UUID first, UUID second, int winner, Instant startedAt) {
        return OlympiadFight.builder()
                .firstObjectId(registry.handleOf(first))
                .secondObjectId(registry.handleOf(second))
                .firstClassId(88)
                .secondClassId(93)
                .winner(winner)
                .startedAt(startedAt)
                .durationMs(125_000)
                .classed(true)
                .build();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreUuidsAndNullWinner_whenFightIsDraw() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();

            repository.insert(fight(first, second, 0, NOW));

            assertThat(JDBC.sql("""
                SELECT second_character_id
                FROM olympiad_fights
                WHERE first_character_id = ? AND winner IS NULL\
                """).param(first).query(UUID.class).single()).isEqualTo(second);
        }
    }

    @Nested
    class FindByCharacterStartedBefore {
        @Test
        void findByCharacterStartedBefore_shouldReturnFightsOnBothSidesOldestFirst_whenStartedBefore() {
            final UUID hero = insertCharacter();
            final UUID opponent = insertCharacter();
            final OlympiadFight older = fight(opponent, hero, 2, NOW.minusSeconds(7200));
            final OlympiadFight newer = fight(hero, opponent, 0, NOW.minusSeconds(3600));
            repository.insert(newer);
            repository.insert(older);
            repository.insert(fight(hero, opponent, 1, NOW));

            assertThat(repository.findByCharacterStartedBefore(registry.handleOf(hero), NOW))
                    .containsExactly(older, newer);
        }

        @Test
        void findByCharacterStartedBefore_shouldReturnEmpty_whenHandleIsUnknown() {
            assertThat(repository.findByCharacterStartedBefore(registry.allocateTransient(), NOW))
                    .isEmpty();
        }
    }
}
