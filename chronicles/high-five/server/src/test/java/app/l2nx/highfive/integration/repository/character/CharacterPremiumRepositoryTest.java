/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterPremiumRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CharacterPremiumRepositoryTest extends RepositoryTest {
    private final CharacterPremiumRepository repository = new CharacterPremiumRepository(JDBC, registry);

    @Nested
    class FindEndsAt {
        @Test
        void findEndsAt_shouldReturnEnd_whenPremium() {
            final UUID character = insertCharacter();
            insertPremium(character, NOW);

            assertThat(repository.findEndsAt(registry.handleOf(character))).contains(NOW);
        }

        @Test
        void findEndsAt_shouldBeEmpty_whenNoPremium() {
            assertThat(repository.findEndsAt(registry.handleOf(insertCharacter())))
                    .isEmpty();
        }

        @Test
        void findEndsAt_shouldBeEmpty_whenHandleNamesNoCharacter() {
            final int handle = registry.allocateTransient();

            assertThat(repository.findEndsAt(handle)).isEmpty();
            assertThat(registry.findId(handle)).isEmpty();
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsert_whenNoPremium() {
            final UUID character = insertCharacter();

            repository.save(registry.handleOf(character), NOW);

            assertThat(endsAt(character)).contains(NOW);
        }

        @Test
        void save_shouldReplaceEnd_whenPremium() {
            final UUID character = insertCharacter();
            insertPremium(character, NOW);
            final Instant extended = NOW.plusSeconds(86_400);

            repository.save(registry.handleOf(character), extended);

            assertThat(endsAt(character)).contains(extended);
        }

        @Test
        void save_shouldThrow_whenHandleNamesNoCharacter() {
            final int handle = registry.allocateTransient();

            assertThatThrownBy(() -> repository.save(handle, NOW)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(registry.findId(handle)).isEmpty();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemovePremium() {
            final UUID character = insertCharacter();
            insertPremium(character, NOW);

            repository.delete(registry.handleOf(character));

            assertThat(endsAt(character)).isEmpty();
        }
    }

    private static void insertPremium(UUID characterId, Instant endsAt) {
        JDBC.sql("INSERT INTO character_premiums (character_id, ends_at) VALUES (?, ?)")
                .param(characterId)
                .param(JdbcTimes.utc(endsAt))
                .update();
    }

    private static Optional<Instant> endsAt(UUID characterId) {
        return JDBC.sql("SELECT ends_at FROM character_premiums WHERE character_id = ?")
                .param(characterId)
                .query((rs, rowNum) -> JdbcTimes.instant(rs, "ends_at"))
                .optional();
    }
}
