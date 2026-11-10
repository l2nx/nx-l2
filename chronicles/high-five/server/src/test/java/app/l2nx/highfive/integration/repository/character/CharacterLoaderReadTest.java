/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.highfive.character.CharacterLoader;
import app.l2nx.highfive.character.CharacterLoaderAccess;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.TaskScheduler;

class CharacterLoaderReadTest extends RepositoryTest {
    private final ManualGameClock clock = new ManualGameClock(NOW, ZoneOffset.UTC);
    private final CharacterLoader loader = new CharacterLoader(
            StoredCharacterRepositories.of(JDBC, JDBC_TEMPLATE, registry),
            new BackgroundQueries(Runnable::run, mock(TaskScheduler.class)),
            new DataSourceTransactionManager(DATA_SOURCE),
            clock);

    private static String status(UUID id) {
        return JDBC.sql("SELECT online_status FROM characters WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();
    }

    private static OffsetDateTime lastAccess(UUID id) {
        return JDBC.sql("SELECT last_access_at FROM characters WHERE id = ?")
                .param(id)
                .query(OffsetDateTime.class)
                .single();
    }

    @Nested
    class ReadAndMarkOnline {
        @Test
        void readAndMarkOnline_shouldGiveTheRowsAndCommitTheOnlineMark() {
            final UUID id = insertCharacter();
            clock.advance(Duration.ofSeconds(5));

            assertThat(CharacterLoaderAccess.readAndMarkOnline(loader, id).characterId())
                    .isEqualTo(id);

            assertThat(status(id)).isEqualTo("ONLINE");
            assertThat(lastAccess(id)).isEqualTo(JdbcTimes.utc(NOW.plusSeconds(5)));
        }

        @Test
        void readAndMarkOnline_shouldFail_whenTheCharacterHasNoRow() {
            final UUID missing = UUID.randomUUID();

            assertThatThrownBy(() -> CharacterLoaderAccess.readAndMarkOnline(loader, missing))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(missing.toString());
        }
    }

    @Nested
    class MarkOffline {
        @Test
        void markOffline_shouldCommitTheOfflineMark() {
            final UUID id = insertCharacter();
            CharacterLoaderAccess.readAndMarkOnline(loader, id);

            CharacterLoaderAccess.markOffline(loader, id);

            assertThat(status(id)).isEqualTo("OFFLINE");
        }
    }

    @Nested
    class Read {
        @Test
        void read_shouldGiveTheRowsWithoutTheOnlineMark() {
            final UUID id = insertCharacter();

            assertThat(loader.read(id)).isPresent();
            assertThat(status(id)).isEqualTo("OFFLINE");
        }

        @Test
        void read_shouldBeEmpty_whenTheCharacterHasNoRow() {
            assertThat(loader.read(UUID.randomUUID())).isEmpty();
        }
    }
}
