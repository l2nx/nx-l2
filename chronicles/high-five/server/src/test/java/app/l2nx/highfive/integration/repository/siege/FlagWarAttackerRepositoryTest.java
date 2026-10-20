/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.siege;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.siege.FlagWarAttackerRepository;
import app.l2nx.highfive.repository.siege.model.FlagWarAttacker;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FlagWarAttackerRepositoryTest extends RepositoryTest {
    private static final AtomicInteger HALL_IDS = new AtomicInteger(16000);
    private static final int ROYAL_FLAG = 35422;

    private final FlagWarAttackerRepository repository = new FlagWarAttackerRepository(JDBC, registry);

    private static void insertAttacker(int clanHallId, UUID clanId, int flagNpcId, Integer allyNpcId) {
        JDBC.sql("""
            INSERT INTO siegable_hall_flag_war_attackers (clan_hall_id, clan_id, flag_npc_id, ally_npc_id)
            VALUES (?, ?, ?, ?)\
            """)
                .param(clanHallId)
                .param(clanId)
                .param(flagNpcId)
                .param(allyNpcId)
                .update();
    }

    private static void insertMember(int clanHallId, UUID clanId, UUID characterId) {
        JDBC.sql("""
            INSERT INTO siegable_hall_flag_war_attacker_members (clan_hall_id, clan_id, character_id)
            VALUES (?, ?, ?)\
            """).param(clanHallId).param(clanId).param(characterId).update();
    }

    private static long memberCount(int clanHallId) {
        return JDBC.sql("SELECT count(*) FROM siegable_hall_flag_war_attacker_members WHERE clan_hall_id = ?")
                .param(clanHallId)
                .query(Long.class)
                .single();
    }

    @Nested
    class FindByHall {
        @Test
        void findByHall_shouldReturnZeroAllyNpc_whenNotChosen() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            final UUID otherClanId = insertClan();
            insertAttacker(clanHallId, clanId, ROYAL_FLAG + 1, null);
            insertAttacker(clanHallId, otherClanId, ROYAL_FLAG + 2, 35428);

            assertThat(repository.findByHall(clanHallId))
                    .containsExactly(
                            FlagWarAttacker.builder()
                                    .clanId(registry.handleOf(clanId))
                                    .flagNpcId(ROYAL_FLAG + 1)
                                    .build(),
                            FlagWarAttacker.builder()
                                    .clanId(registry.handleOf(otherClanId))
                                    .flagNpcId(ROYAL_FLAG + 2)
                                    .allyNpcId(35428)
                                    .build());
        }
    }

    @Nested
    class FindMemberIds {
        @Test
        void findMemberIds_shouldReturnMembersOfHallOnly() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final int otherHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            final UUID characterId = insertCharacter();
            insertAttacker(clanHallId, clanId, ROYAL_FLAG + 1, null);
            insertAttacker(otherHallId, clanId, ROYAL_FLAG + 1, null);
            insertMember(clanHallId, clanId, characterId);
            insertMember(otherHallId, clanId, insertCharacter());

            assertThat(repository.findMemberIds(clanHallId, registry.handleOf(clanId)))
                    .containsExactly(registry.handleOf(characterId));
        }

        @Test
        void findMemberIds_shouldReturnEmpty_whenHandleIsNoStoredEntity() {
            assertThat(repository.findMemberIds(HALL_IDS.incrementAndGet(), registry.allocateTransient()))
                    .isEmpty();
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreNullAllyNpc() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();

            repository.insert(clanHallId, registry.handleOf(clanId), ROYAL_FLAG + 3);

            assertThat(JDBC.sql("""
                SELECT clan_id, flag_npc_id, ally_npc_id
                FROM siegable_hall_flag_war_attackers
                WHERE clan_hall_id = ?\
                """).param(clanHallId).query().singleRow())
                    .containsEntry("clan_id", clanId)
                    .containsEntry("flag_npc_id", ROYAL_FLAG + 3)
                    .containsEntry("ally_npc_id", null);
        }
    }

    @Nested
    class UpdateAllyNpc {
        @Test
        void updateAllyNpc_shouldChangeOnlyGivenHall_whenClanIsRegisteredAtTwoHalls() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final int otherHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertAttacker(clanHallId, clanId, ROYAL_FLAG + 1, null);
            insertAttacker(otherHallId, clanId, ROYAL_FLAG + 1, null);

            repository.updateAllyNpc(clanHallId, registry.handleOf(clanId), 35428);

            assertThat(repository.findByHall(clanHallId))
                    .extracting(FlagWarAttacker::allyNpcId)
                    .containsExactly(35428);
            assertThat(repository.findByHall(otherHallId))
                    .extracting(FlagWarAttacker::allyNpcId)
                    .containsExactly(0);
        }

        @Test
        void updateAllyNpc_shouldStoreNull_whenNpcIsZero() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertAttacker(clanHallId, clanId, ROYAL_FLAG + 1, 35428);

            repository.updateAllyNpc(clanHallId, registry.handleOf(clanId), 0);

            assertThat(JDBC.sql("SELECT ally_npc_id IS NULL FROM siegable_hall_flag_war_attackers WHERE"
                                    + " clan_hall_id = ?")
                            .param(clanHallId)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }

    @Nested
    class InsertMember {
        @Test
        void insertMember_shouldIgnoreDuplicate_whenPlayerIsListedTwice() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            final UUID characterId = insertCharacter();
            insertAttacker(clanHallId, clanId, ROYAL_FLAG + 1, null);

            repository.insertMember(clanHallId, registry.handleOf(clanId), registry.handleOf(characterId));
            repository.insertMember(clanHallId, registry.handleOf(clanId), registry.handleOf(characterId));

            assertThat(repository.findMemberIds(clanHallId, registry.handleOf(clanId)))
                    .containsExactly(registry.handleOf(characterId));
        }
    }

    @Nested
    class DeleteByHall {
        @Test
        void deleteByHall_shouldRemoveAttackersAndMembersOfHallOnly() {
            final int clanHallId = HALL_IDS.incrementAndGet();
            final int otherHallId = HALL_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertAttacker(clanHallId, clanId, ROYAL_FLAG + 1, null);
            insertAttacker(otherHallId, clanId, ROYAL_FLAG + 1, null);
            insertMember(clanHallId, clanId, insertCharacter());
            insertMember(otherHallId, clanId, insertCharacter());

            repository.deleteByHall(clanHallId);

            assertThat(repository.findByHall(clanHallId)).isEmpty();
            assertThat(memberCount(clanHallId)).isZero();
            assertThat(repository.findByHall(otherHallId)).hasSize(1);
            assertThat(memberCount(otherHallId)).isEqualTo(1);
        }
    }
}
