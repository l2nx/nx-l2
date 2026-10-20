/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.olympiad;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.olympiad.HeroDiaryRepository;
import app.l2nx.highfive.repository.olympiad.model.HeroDiaryEntry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeroDiaryRepositoryTest extends RepositoryTest {
    private final HeroDiaryRepository repository = new HeroDiaryRepository(JDBC, registry);

    @Nested
    class Insert {
        @ParameterizedTest(name = "action {0} param {1}")
        @CsvSource({"1, 25001, RAID_KILLED, 25001,", "2, 0, HERO_GAINED, ,", "3, 5, CASTLE_TAKEN, , 5"})
        void insert_shouldSplitParamByAction_whenStored(
                int action, int param, String expectedAction, Integer expectedNpcId, Integer expectedCastleId) {
            final UUID character = insertCharacter();

            repository.insert(registry.handleOf(character), NOW, action, param);

            assertThat(JDBC.sql("""
                SELECT action, npc_id, castle_id FROM hero_diary_entries WHERE character_id = ?\
                """)
                            .param(character)
                            .query((rs, rowNum) -> new Object[] {
                                rs.getString("action"),
                                rs.getObject("npc_id", Integer.class),
                                rs.getObject("castle_id", Integer.class)
                            })
                            .single())
                    .containsExactly(expectedAction, expectedNpcId, expectedCastleId);
        }
    }

    @Nested
    class FindByCharacter {
        @Test
        void findByCharacter_shouldReturnEntriesOldestFirstWithLegacyParams_whenStored() {
            final int hero = registry.handleOf(insertCharacter());
            final Instant first = NOW.minusSeconds(60);
            final Instant second = NOW;
            repository.insert(hero, second, 3, 7);
            repository.insert(hero, first, 2, 0);
            repository.insert(registry.handleOf(insertCharacter()), first, 1, 25001);

            assertThat(repository.findByCharacter(hero))
                    .containsExactly(
                            HeroDiaryEntry.builder()
                                    .recordedAt(first)
                                    .action(2)
                                    .param(0)
                                    .build(),
                            HeroDiaryEntry.builder()
                                    .recordedAt(second)
                                    .action(3)
                                    .param(7)
                                    .build());
        }

        @Test
        void findByCharacter_shouldReturnEmpty_whenHandleIsUnknown() {
            assertThat(repository.findByCharacter(registry.allocateTransient())).isEmpty();
        }
    }
}
