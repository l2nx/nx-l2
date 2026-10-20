/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.boss;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.boss.GrandBossStateRepository;
import app.l2nx.highfive.repository.boss.model.GrandBossState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GrandBossStateRepositoryTest extends RepositoryTest {
    private final GrandBossStateRepository repository = new GrandBossStateRepository(JDBC);

    private static GrandBossState living(int npcId) {
        return GrandBossState.builder()
                .npcId(npcId)
                .status(2)
                .respawnTime(NOW)
                .currentHp(1000.0)
                .currentMp(500.0)
                .x(-21610)
                .y(181594)
                .z(-5734)
                .heading(16384)
                .build();
    }

    private GrandBossState find(int npcId) {
        return repository.findAll().stream()
                .filter(state -> state.npcId() == npcId)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class Save {
        @Test
        void save_shouldStoreFullState_whenBossIsNew() {
            final GrandBossState expected = living(29001);

            repository.save(expected);

            assertThat(find(29001)).isEqualTo(expected);
        }

        @Test
        void save_shouldOverwriteState_whenRowExists() {
            repository.save(living(29002));
            final GrandBossState expected =
                    living(29002).toBuilder().status(3).currentHp(5.0).x(100).build();

            repository.save(expected);

            assertThat(find(29002)).isEqualTo(expected);
        }

        @Test
        void save_shouldStoreNullRespawn_whenRespawnTimeIsNull() {
            repository.save(living(29003).toBuilder().respawnTime(null).build());

            assertThat(JDBC.sql("SELECT respawn_at IS NULL FROM grand_boss_states WHERE npc_id = 29003")
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }

    @Nested
    class SaveStatus {
        @Test
        void saveStatus_shouldInsertStatusOnly_whenBossIsNew() {
            repository.saveStatus(29004, 1);

            assertThat(find(29004))
                    .isEqualTo(GrandBossState.builder().npcId(29004).status(1).build());
        }

        @Test
        void saveStatus_shouldKeepOtherColumns_whenRowExists() {
            repository.save(living(29005));

            repository.saveStatus(29005, 0);

            assertThat(find(29005))
                    .isEqualTo(living(29005).toBuilder().status(0).build());
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnNullPositionAndRespawn_whenOnlyStatusIsStored() {
            JDBC.sql("INSERT INTO grand_boss_states (npc_id, status) VALUES (29006, 3)")
                    .update();

            final GrandBossState actual = find(29006);

            assertThat(actual.respawnTime()).isNull();
            assertThat(actual.currentHp()).isNull();
            assertThat(actual.x()).isNull();
            assertThat(actual.heading()).isNull();
        }
    }
}
