/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.CastleMercenaryRepository;
import app.l2nx.highfive.repository.residence.model.CastleMercenary;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CastleMercenaryRepositoryTest extends RepositoryTest {
    private static final AtomicInteger IDS = new AtomicInteger(7000);

    private final CastleMercenaryRepository repository = new CastleMercenaryRepository(JDBC);

    private static CastleMercenary mercenary(int castleId, int npcId, int x) {
        return CastleMercenary.builder()
                .castleId(castleId)
                .npcId(npcId)
                .x(x)
                .y(-20)
                .z(-300)
                .heading(16000)
                .build();
    }

    private static void insertMercenary(CastleMercenary mercenary) {
        JDBC.sql("INSERT INTO castle_mercenaries (castle_id, npc_id, x, y, z, heading) VALUES (?, ?, ?, ?, ?, ?)")
                .param(mercenary.castleId())
                .param(mercenary.npcId())
                .param(mercenary.x())
                .param(mercenary.y())
                .param(mercenary.z())
                .param(mercenary.heading())
                .update();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnMercenariesOfEveryCastle() {
            final CastleMercenary first = mercenary(IDS.incrementAndGet(), 35010, 100);
            final CastleMercenary second = mercenary(IDS.incrementAndGet(), 35011, 200);
            insertMercenary(first);
            insertMercenary(second);

            assertThat(repository.findAll()).contains(first, second);
        }
    }

    @Nested
    class FindByCastle {
        @Test
        void findByCastle_shouldReturnMercenariesInPlacementOrder() {
            final int castleId = IDS.incrementAndGet();
            final CastleMercenary first = mercenary(castleId, 35012, 300);
            final CastleMercenary second = mercenary(castleId, 35013, 100);
            insertMercenary(first);
            insertMercenary(second);
            insertMercenary(mercenary(IDS.incrementAndGet(), 35012, 300));

            assertThat(repository.findByCastle(castleId)).containsExactly(first, second);
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreMercenary() {
            final CastleMercenary expected = mercenary(IDS.incrementAndGet(), 35014, 400);

            repository.insert(expected);

            assertThat(repository.findByCastle(expected.castleId())).containsExactly(expected);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveMercenaryAtSpot_whenNpcMatches() {
            final int castleId = IDS.incrementAndGet();
            final int x = IDS.incrementAndGet();
            final CastleMercenary kept = mercenary(castleId, 35016, x);
            insertMercenary(mercenary(castleId, 35015, x));
            insertMercenary(kept);

            repository.delete(35015, x, -20, -300);

            assertThat(repository.findByCastle(castleId)).containsExactly(kept);
        }
    }

    @Nested
    class DeleteByCastle {
        @Test
        void deleteByCastle_shouldRemoveMercenariesOfCastleOnly() {
            final int castleId = IDS.incrementAndGet();
            final int otherCastleId = IDS.incrementAndGet();
            insertMercenary(mercenary(castleId, 35017, 500));
            insertMercenary(mercenary(otherCastleId, 35017, 600));

            repository.deleteByCastle(castleId);

            assertThat(repository.findByCastle(castleId)).isEmpty();
            assertThat(repository.findByCastle(otherCastleId)).hasSize(1);
        }
    }
}
