/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.mechanics.announce.AnnouncementType;
import app.l2nx.highfive.repository.world.AnnouncementRepository;
import app.l2nx.highfive.repository.world.model.AnnouncementRow;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnnouncementRepositoryTest extends RepositoryTest {
    private final AnnouncementRepository repository = new AnnouncementRepository(JDBC);

    private static AnnouncementRow normal() {
        return AnnouncementRow.builder()
                .type(AnnouncementType.NORMAL)
                .content("Welcome")
                .author("GM")
                .repeat(-1)
                .build();
    }

    private static AnnouncementRow auto(int repeat) {
        return AnnouncementRow.builder()
                .type(AnnouncementType.AUTO_CRITICAL)
                .content("Raid soon")
                .author("GM")
                .initialDelayMs(5_000)
                .intervalMs(60_000)
                .repeat(repeat)
                .build();
    }

    private Optional<AnnouncementRow> find(int id) {
        return repository.findAll().stream().filter(row -> row.id() == id).findFirst();
    }

    private static String schedule(int id) {
        return JDBC.sql("""
            SELECT concat_ws(',', announcement_type, initial_delay_ms, interval_ms, repeat_count)
            FROM announcements
            WHERE id = ?\
            """).param(id).query(String.class).single();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreNoSchedule_whenTypeIsNotAuto() {
            final int id = repository.insert(
                    normal().toBuilder().initialDelayMs(1).intervalMs(2).build());

            assertThat(schedule(id)).isEqualTo("NORMAL");
        }

        @Test
        void insert_shouldStoreNullRepeat_whenRepeatingForever() {
            final int id = repository.insert(auto(-1));

            assertThat(schedule(id)).isEqualTo("AUTO_CRITICAL,5000,60000");
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnLegacyDefaults_whenColumnsAreNull() {
            final int normalId = repository.insert(normal());
            final int autoId = repository.insert(auto(3));

            assertThat(find(normalId))
                    .contains(normal().toBuilder().id(normalId).build());
            assertThat(find(autoId)).contains(auto(3).toBuilder().id(autoId).build());
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldOverwriteEveryColumn_whenAnnouncementExists() {
            final int id = repository.insert(auto(3));
            final AnnouncementRow expected = auto(-1).toBuilder()
                    .id(id)
                    .type(AnnouncementType.AUTO_NORMAL)
                    .content("Changed")
                    .intervalMs(30_000)
                    .build();

            repository.update(expected);

            assertThat(find(id)).contains(expected);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveAnnouncement_whenExists() {
            final int id = repository.insert(normal());

            repository.delete(id);

            assertThat(find(id)).isEmpty();
        }
    }
}
