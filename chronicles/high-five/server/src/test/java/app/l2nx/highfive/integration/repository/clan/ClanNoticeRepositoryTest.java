/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.model.ClanNotice;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ClanNoticeRepositoryTest extends RepositoryTest {
    private final ClanNoticeRepository repository = new ClanNoticeRepository(JDBC, registry);

    @Nested
    class Find {
        @Test
        void find_shouldReturnNotice_whenStored() {
            final UUID clanId = insertClan();
            JDBC.sql("INSERT INTO clan_notices (clan_id, enabled, notice) VALUES (?, true, 'Siege at 8')")
                    .param(clanId)
                    .update();

            assertThat(repository.find(registry.handleOf(clanId)))
                    .contains(ClanNotice.builder()
                            .enabled(true)
                            .notice("Siege at 8")
                            .build());
        }

        @Test
        void find_shouldReturnEmpty_whenClanNeverStoredNotice() {
            assertThat(repository.find(registry.handleOf(insertClan()))).isEmpty();
        }
    }

    @Nested
    class Upsert {
        @Test
        void upsert_shouldInsertThenReplace_whenCalledTwice() {
            final int clanHandle = registry.handleOf(insertClan());

            repository.upsert(clanHandle, "first", false);
            repository.upsert(clanHandle, "second", true);

            assertThat(repository.find(clanHandle))
                    .contains(
                            ClanNotice.builder().enabled(true).notice("second").build());
        }

        @ParameterizedTest(name = "notice [{0}]")
        @NullAndEmptySource
        void upsert_shouldStoreNull_whenNoticeIsMissing(String notice) {
            final UUID clanId = insertClan();

            repository.upsert(registry.handleOf(clanId), notice, true);

            assertThat(JDBC.sql("SELECT notice IS NULL FROM clan_notices WHERE clan_id = ?")
                            .param(clanId)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }
}
