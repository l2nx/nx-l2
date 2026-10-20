/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.olympiad;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.olympiad.HeroRepository;
import app.l2nx.highfive.repository.olympiad.model.HeroRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock("heroes")
class HeroRepositoryTest extends RepositoryTest {
    private final HeroRepository repository = new HeroRepository(JDBC, JDBC_TEMPLATE, registry);

    @BeforeEach
    void clear() {
        JDBC.sql("DELETE FROM heroes").update();
    }

    private static void insertHero(UUID characterId, boolean played, String message) {
        JDBC.sql("""
            INSERT INTO heroes (character_id, class_id, hero_count, played, claimed, message)
            VALUES (?, 88, 1, ?, true, ?)\
            """).param(characterId).param(played).param(message).update();
    }

    private static String nameOf(UUID characterId) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    private HeroRow hero(UUID characterId, boolean played) {
        return HeroRow.builder()
                .objectId(registry.handleOf(characterId))
                .characterName(nameOf(characterId))
                .classId(88)
                .count(1)
                .played(played)
                .claimed(true)
                .build();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnPastAndCurrentHeroes_whenStored() {
            final UUID current = insertCharacter();
            final UUID past = insertCharacter();
            insertHero(current, true, null);
            insertHero(past, false, null);

            assertThat(repository.findAll()).containsExactlyInAnyOrder(hero(current, true), hero(past, false));
        }
    }

    @Nested
    class FindPlayed {
        @Test
        void findPlayed_shouldReturnCurrentHeroesOnly_whenPastHeroesExist() {
            final UUID current = insertCharacter();
            insertHero(current, true, null);
            insertHero(insertCharacter(), false, null);

            assertThat(repository.findPlayed()).containsExactly(hero(current, true));
        }
    }

    @Nested
    class ResetPlayed {
        @Test
        void resetPlayed_shouldClearPlayedOfEveryHero_whenCalled() {
            insertHero(insertCharacter(), true, null);

            repository.resetPlayed();

            assertThat(repository.findPlayed()).isEmpty();
        }
    }

    @Nested
    class SaveAll {
        @Test
        void saveAll_shouldInsertHero_whenNew() {
            final HeroRow expected = hero(insertCharacter(), true);

            repository.saveAll(List.of(expected));

            assertThat(repository.findAll()).containsExactly(expected);
        }

        @Test
        void saveAll_shouldUpdateHeroAndKeepMessage_whenStored() {
            final UUID character = insertCharacter();
            insertHero(character, false, "For Aden!");
            final HeroRow expected =
                    hero(character, true).toBuilder().count(2).claimed(false).build();

            repository.saveAll(List.of(expected));

            assertThat(repository.findAll()).containsExactly(expected);
            assertThat(repository.findMessage(registry.handleOf(character))).contains("For Aden!");
        }
    }

    @Nested
    class FindMessage {
        @Test
        void findMessage_shouldReturnEmpty_whenMessageIsNull() {
            final UUID character = insertCharacter();
            insertHero(character, true, null);

            assertThat(repository.findMessage(registry.handleOf(character))).isEmpty();
        }

        @Test
        void findMessage_shouldReturnEmpty_whenCharacterIsNoHero() {
            assertThat(repository.findMessage(registry.handleOf(insertCharacter())))
                    .isEmpty();
        }
    }

    @Nested
    class UpdateMessage {
        @Test
        void updateMessage_shouldStoreMessage_whenHeroExists() {
            final UUID character = insertCharacter();
            insertHero(character, true, null);

            repository.updateMessage(registry.handleOf(character), "Glory");

            assertThat(repository.findMessage(registry.handleOf(character))).contains("Glory");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void updateMessage_shouldStoreNull_whenMessageIsBlank(String message) {
            final UUID character = insertCharacter();
            insertHero(character, true, "Old");

            repository.updateMessage(registry.handleOf(character), message);

            assertThat(JDBC.sql("SELECT message IS NULL FROM heroes WHERE character_id = ?")
                            .param(character)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }
}
