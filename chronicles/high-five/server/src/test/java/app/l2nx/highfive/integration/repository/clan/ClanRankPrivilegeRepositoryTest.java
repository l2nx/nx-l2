/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.model.RankPrivilege;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ClanRankPrivilegeRepositoryTest extends RepositoryTest {
    private final ClanRankPrivilegeRepository repository = new ClanRankPrivilegeRepository(JDBC, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnPrivilegesOfClanOnly() {
            final UUID clanId = insertClan();
            final UUID otherClanId = insertClan();
            JDBC.sql("""
                INSERT INTO clan_rank_privileges (clan_id, power_grade, privilege_mask)
                VALUES (?, 1, 8388606), (?, 5, 16), (?, 2, 4)\
                """).param(clanId).param(clanId).param(otherClanId).update();

            assertThat(repository.findAll(registry.handleOf(clanId)))
                    .containsExactlyInAnyOrder(
                            RankPrivilege.builder()
                                    .powerGrade(1)
                                    .privilegeMask(8388606)
                                    .build(),
                            RankPrivilege.builder()
                                    .powerGrade(5)
                                    .privilegeMask(16)
                                    .build());
        }
    }

    @Nested
    class Upsert {
        @Test
        void upsert_shouldReplaceMask_whenRankIsStored() {
            final int clanHandle = registry.handleOf(insertClan());

            repository.upsert(clanHandle, 3, 1);
            repository.upsert(clanHandle, 3, 2);

            assertThat(repository.findAll(clanHandle))
                    .containsExactly(RankPrivilege.builder()
                            .powerGrade(3)
                            .privilegeMask(2)
                            .build());
        }

        @Test
        void upsert_shouldFail_whenRankIsOutsidePowerGrades() {
            final int clanHandle = registry.handleOf(insertClan());

            assertThatThrownBy(() -> repository.upsert(clanHandle, -1, 0))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
