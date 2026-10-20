/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.crest;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.clan.Crest;
import app.l2nx.highfive.entity.clan.enums.CrestType;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.crest.CrestRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CrestRepositoryTest extends RepositoryTest {
    private static final byte[] IMAGE = {1, 2, 3, 4};

    private final CrestRepository repository = new CrestRepository(JDBC);

    @Nested
    class Create {
        @Test
        void create_shouldStoreCrestUnderNewId() {
            final Crest actual = repository.create(IMAGE, CrestType.ALLY);

            assertThat(repository.findAll())
                    .filteredOn(crest -> crest.getId() == actual.getId())
                    .singleElement()
                    .satisfies(crest -> {
                        assertThat(crest.getData()).containsExactly(IMAGE);
                        assertThat(crest.getType()).isEqualTo(CrestType.ALLY);
                    });
        }

        @Test
        void create_shouldNeverReuseId_whenLatestDeleted() {
            final Crest deleted = repository.create(IMAGE, CrestType.PLEDGE);
            repository.delete(deleted.getId());

            assertThat(repository.create(IMAGE, CrestType.PLEDGE).getId()).isGreaterThan(deleted.getId());
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldClearCrestOfClan() {
            final Crest crest = repository.create(IMAGE, CrestType.PLEDGE_LARGE);
            final UUID clan = insertClan();
            JDBC.sql("UPDATE clans SET crest_large_id = ? WHERE id = ?")
                    .param(crest.getId())
                    .param(clan)
                    .update();

            repository.delete(crest.getId());

            assertThat(repository.findAll()).noneMatch(row -> row.getId() == crest.getId());
            assertThat(JDBC.sql("SELECT crest_large_id IS NULL FROM clans WHERE id = ?")
                            .param(clan)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }
}
