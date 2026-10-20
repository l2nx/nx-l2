/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.boss;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.boss.RaidBossStateRepository;
import app.l2nx.highfive.repository.boss.model.RaidBossState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RaidBossStateRepositoryTest extends RepositoryTest {
    private final RaidBossStateRepository repository = new RaidBossStateRepository(JDBC, JDBC_TEMPLATE);

    private static RaidBossState state(int npcId, Instant respawnTime) {
        return RaidBossState.builder()
                .npcId(npcId)
                .respawnTime(respawnTime)
                .currentHp(1500.5)
                .currentMp(300)
                .build();
    }

    @Nested
    class SaveAll {
        @Test
        void saveAll_shouldStoreNullRespawn_whenBossIsAlive() {
            repository.saveAll(List.of(state(25001, null)));

            assertThat(JDBC.sql("SELECT respawn_at IS NULL FROM raid_boss_states WHERE npc_id = 25001")
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }

        @Test
        void saveAll_shouldOverwriteState_whenRowExists() {
            repository.saveAll(List.of(state(25002, null)));
            final RaidBossState expected =
                    state(25002, NOW).toBuilder().currentHp(10).currentMp(20).build();

            repository.saveAll(List.of(expected));

            assertThat(repository.findAll()).contains(expected);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnNullRespawn_whenRespawnIsNull() {
            final RaidBossState expected = state(25003, null);
            repository.saveAll(List.of(expected, state(25004, NOW)));

            final List<RaidBossState> actual = repository.findAll();

            assertThat(actual).contains(expected, state(25004, NOW));
        }
    }
}
