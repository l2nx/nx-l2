/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import static app.l2nx.highfive.repository.clan.ClanSkillRepository.CLAN_WIDE;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.model.ClanSkill;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClanSkillRepositoryTest extends RepositoryTest {
    private static final int SKILL_ID = 370;

    private final ClanSkillRepository repository = new ClanSkillRepository(JDBC, registry);

    private static ClanSkill skill(int level, int pledgeType) {
        return ClanSkill.builder()
                .skillId(SKILL_ID)
                .skillLevel(level)
                .pledgeType(pledgeType)
                .build();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldMapNullPledgeTypeToClanWide() {
            final UUID clanId = insertClan();
            JDBC.sql("""
                INSERT INTO clan_skills (clan_id, skill_id, skill_level, pledge_type)
                VALUES (?, ?, 1, NULL), (?, ?, 2, 0), (?, ?, 3, 100)\
                """)
                    .param(clanId)
                    .param(SKILL_ID)
                    .param(clanId)
                    .param(SKILL_ID)
                    .param(clanId)
                    .param(SKILL_ID)
                    .update();

            assertThat(repository.findAll(registry.handleOf(clanId)))
                    .containsExactlyInAnyOrder(skill(1, CLAN_WIDE), skill(2, 0), skill(3, 100));
        }
    }

    @Nested
    class Upsert {
        @Test
        void upsert_shouldStoreClanWideAsNull() {
            final UUID clanId = insertClan();

            repository.upsert(registry.handleOf(clanId), SKILL_ID, 1, CLAN_WIDE);

            assertThat(JDBC.sql("SELECT pledge_type IS NULL FROM clan_skills WHERE clan_id = ?")
                            .param(clanId)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }

        @Test
        void upsert_shouldRaiseLevelOfThatPledgeOnly_whenSkillIsStoredForSeveralPledges() {
            final int clanHandle = registry.handleOf(insertClan());
            repository.upsert(clanHandle, SKILL_ID, 1, CLAN_WIDE);
            repository.upsert(clanHandle, SKILL_ID, 1, 0);
            repository.upsert(clanHandle, SKILL_ID, 1, 100);

            repository.upsert(clanHandle, SKILL_ID, 2, CLAN_WIDE);
            repository.upsert(clanHandle, SKILL_ID, 3, 100);

            assertThat(repository.findAll(clanHandle))
                    .containsExactlyInAnyOrder(skill(2, CLAN_WIDE), skill(1, 0), skill(3, 100));
        }
    }
}
