/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.forum.BbsFavoriteRepository;
import app.l2nx.highfive.repository.forum.model.BbsFavorite;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BbsFavoriteRepositoryTest extends RepositoryTest {
    private final BbsFavoriteRepository repository = new BbsFavoriteRepository(JDBC, registry);

    @Nested
    class FindByCharacter {
        @Test
        void findByCharacter_shouldReturnNewestFirst() {
            final UUID character = insertCharacter();
            final int older = insertFavorite(character, "Old", "_bbshome", NOW.minusSeconds(60));
            final int newer = insertFavorite(character, "New", "_bbstop", NOW);
            insertFavorite(insertCharacter(), "Other", "_bbshome", NOW);

            final List<BbsFavorite> actual = repository.findByCharacter(registry.handleOf(character));

            assertThat(actual)
                    .containsExactly(
                            new BbsFavorite(newer, "New", "_bbstop", NOW),
                            new BbsFavorite(older, "Old", "_bbshome", NOW.minusSeconds(60)));
        }
    }

    @Nested
    class CountByCharacter {
        @Test
        void countByCharacter_shouldCountOnlyThatCharacter() {
            final UUID character = insertCharacter();
            insertFavorite(character, "A", "_bbshome", NOW);
            insertFavorite(character, "B", "_bbstop", NOW);
            insertFavorite(insertCharacter(), "C", "_bbshome", NOW);

            assertThat(repository.countByCharacter(registry.handleOf(character)))
                    .isEqualTo(2);
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsertWithGivenTime() {
            final UUID character = insertCharacter();

            repository.save(registry.handleOf(character), "Home", "_bbshome", NOW);

            assertThat(favorites(character)).containsExactly(tuple("Home", "_bbshome", NOW));
        }

        @Test
        void save_shouldUpdateTitleAndTime_whenBypassExists() {
            final UUID character = insertCharacter();
            insertFavorite(character, "Old", "_bbshome", NOW.minusSeconds(60));

            repository.save(registry.handleOf(character), "New", "_bbshome", NOW);

            assertThat(favorites(character)).containsExactly(tuple("New", "_bbshome", NOW));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveFavorite_whenOwnedByCharacter() {
            final UUID character = insertCharacter();
            final int id = insertFavorite(character, "Home", "_bbshome", NOW);

            final boolean actual = repository.delete(registry.handleOf(character), id);

            assertThat(actual).isTrue();
            assertThat(favorites(character)).isEmpty();
        }

        @Test
        void delete_shouldKeepFavorite_whenOwnedByAnotherCharacter() {
            final UUID owner = insertCharacter();
            final int id = insertFavorite(owner, "Home", "_bbshome", NOW);

            final boolean actual = repository.delete(registry.handleOf(insertCharacter()), id);

            assertThat(actual).isFalse();
            assertThat(favorites(owner)).hasSize(1);
        }
    }

    private static int insertFavorite(UUID character, String title, String bypass, Instant addedAt) {
        return JDBC.sql("""
            INSERT INTO bbs_favorites (character_id, title, bypass, added_at)
            VALUES (?, ?, ?, ?)
            RETURNING id\
            """)
                .param(character)
                .param(title)
                .param(bypass)
                .param(JdbcTimes.utc(addedAt))
                .query(Integer.class)
                .single();
    }

    private static List<Tuple> favorites(UUID character) {
        return JDBC.sql("SELECT title, bypass, added_at FROM bbs_favorites WHERE character_id = ?")
                .param(character)
                .query((rs, rowNum) ->
                        tuple(rs.getString("title"), rs.getString("bypass"), JdbcTimes.instant(rs, "added_at")))
                .list();
    }
}
