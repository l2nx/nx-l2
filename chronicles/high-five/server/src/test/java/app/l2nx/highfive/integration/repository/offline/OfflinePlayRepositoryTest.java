/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.highfive.entity.groups.PartyDistributionType;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.offline.OfflinePlayRepository;
import app.l2nx.highfive.repository.offline.model.OfflinePlayEntry;
import app.l2nx.highfive.repository.offline.model.OfflinePlayEntryType;
import app.l2nx.highfive.repository.offline.model.OfflinePlayPartyMember;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

// Several methods read or clear the whole tables, so the tests must not interleave.
@ResourceLock("character_offline_play")
class OfflinePlayRepositoryTest extends RepositoryTest {
    private final OfflinePlayRepository repository = new OfflinePlayRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindCharacterIds {
        @Test
        void findCharacterIds_shouldReturnEachCharacterOnce() {
            JDBC.sql("DELETE FROM character_offline_play_settings").update();
            final UUID character = insertCharacter();
            insertEntry(character, "AUTO_BUFF", 1204);
            insertEntry(character, "AUTO_SKILL", 3);

            final List<Integer> actual = repository.findCharacterIds();

            assertThat(actual).containsExactly(registry.handleOf(character));
        }
    }

    @Nested
    class FindEntries {
        @Test
        void findEntries_shouldMapNullTargetToZero() {
            final UUID character = insertCharacter();
            insertEntry(character, "ACTIVE_SOULSHOT", 1835);
            insertEntry(character, "CUSTOM_AUTO_POTION_SYSTEM", null);

            final List<OfflinePlayEntry> actual = repository.findEntries(registry.handleOf(character));

            assertThat(actual)
                    .containsExactly(
                            new OfflinePlayEntry(OfflinePlayEntryType.ACTIVE_SOULSHOT, 1835),
                            new OfflinePlayEntry(OfflinePlayEntryType.CUSTOM_AUTO_POTION_SYSTEM, 0));
        }
    }

    @Nested
    class ReplaceEntries {
        @Test
        void replaceEntries_shouldReplaceStoredEntries_andStoreCustomPotionWithoutTarget() {
            final UUID character = insertCharacter();
            insertEntry(character, "AUTO_ACTION", 2);
            final List<OfflinePlayEntry> entries = List.of(
                    new OfflinePlayEntry(OfflinePlayEntryType.AUTO_SUPPLY_ITEM, 728),
                    new OfflinePlayEntry(OfflinePlayEntryType.AUTO_SUPPLY_ITEM, 728),
                    new OfflinePlayEntry(OfflinePlayEntryType.CUSTOM_AUTO_POTION_SYSTEM, 0));

            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceEntries(registry.handleOf(character), entries));

            assertThat(entries(character))
                    .containsExactly(tuple("AUTO_SUPPLY_ITEM", 728), tuple("CUSTOM_AUTO_POTION_SYSTEM", null));
        }

        @Test
        void replaceEntries_shouldClearEntries_whenNoneGiven() {
            final UUID character = insertCharacter();
            insertEntry(character, "AUTO_ACTION", 2);

            repository.replaceEntries(registry.handleOf(character), List.of());

            assertThat(entries(character)).isEmpty();
        }
    }

    @Nested
    class DeleteEntries {
        @Test
        void deleteEntries_shouldDeleteOnlyThatCharacter() {
            final UUID character = insertCharacter();
            final UUID other = insertCharacter();
            insertEntry(character, "AUTO_ACTION", 2);
            insertEntry(other, "AUTO_ACTION", 2);

            repository.deleteEntries(registry.handleOf(character));

            assertThat(entries(character)).isEmpty();
            assertThat(entries(other)).hasSize(1);
        }
    }

    @Nested
    class FindPartyLeaderIds {
        @Test
        void findPartyLeaderIds_shouldReturnEachLeaderOnce() {
            JDBC.sql("DELETE FROM character_offline_play_party_members").update();
            final UUID leader = insertCharacter();
            insertPartyMember(leader, insertCharacter(), "RANDOM");
            insertPartyMember(leader, insertCharacter(), "RANDOM");

            final List<Integer> actual = repository.findPartyLeaderIds();

            assertThat(actual).containsExactly(registry.handleOf(leader));
        }
    }

    @Nested
    class FindPartyMembers {
        @Test
        void findPartyMembers_shouldReturnMembersOfThatLeader() {
            final UUID leader = insertCharacter();
            final UUID member = insertCharacter();
            insertPartyMember(leader, member, "BY_TURN_INCLUDING_SPOIL");
            insertPartyMember(insertCharacter(), insertCharacter(), "RANDOM");

            final List<OfflinePlayPartyMember> actual = repository.findPartyMembers(registry.handleOf(leader));

            assertThat(actual)
                    .containsExactly(new OfflinePlayPartyMember(
                            registry.handleOf(leader),
                            registry.handleOf(member),
                            PartyDistributionType.BY_TURN_INCLUDING_SPOIL));
        }
    }

    @Nested
    class InsertPartyMembers {
        @Test
        void insertPartyMembers_shouldStoreUuidsAndDistributionName() {
            final UUID leader = insertCharacter();
            final UUID member = insertCharacter();

            repository.insertPartyMembers(List.of(new OfflinePlayPartyMember(
                    registry.handleOf(leader), registry.handleOf(member), PartyDistributionType.FINDERS_KEEPERS)));

            assertThat(partyMembers(leader)).containsExactly(tuple(member, "FINDERS_KEEPERS"));
        }

        @Test
        void insertPartyMembers_shouldDoNothing_whenEmpty() {
            JDBC.sql("DELETE FROM character_offline_play_party_members").update();

            repository.insertPartyMembers(List.of());

            assertThat(partyMemberCount()).isZero();
        }
    }

    @Nested
    class DeleteAllPartyMembers {
        @Test
        void deleteAllPartyMembers_shouldEmptyTheTable() {
            insertPartyMember(insertCharacter(), insertCharacter(), "RANDOM");

            repository.deleteAllPartyMembers();

            assertThat(partyMemberCount()).isZero();
        }
    }

    private static void insertEntry(UUID character, String type, Integer targetId) {
        JDBC.sql("INSERT INTO character_offline_play_settings (character_id, entry_type, target_id) VALUES (?, ?, ?)")
                .param(character)
                .param(type)
                .param(targetId)
                .update();
    }

    private static void insertPartyMember(UUID leader, UUID member, String distributionType) {
        JDBC.sql("""
            INSERT INTO character_offline_play_party_members (leader_character_id, character_id, distribution_type)
            VALUES (?, ?, ?)\
            """).param(leader).param(member).param(distributionType).update();
    }

    private static List<Tuple> entries(UUID character) {
        return JDBC.sql("""
            SELECT entry_type, target_id FROM character_offline_play_settings WHERE character_id = ? ORDER BY id\
            """)
                .param(character)
                .query((rs, rowNum) -> tuple(rs.getString("entry_type"), rs.getObject("target_id", Integer.class)))
                .list();
    }

    private static List<Tuple> partyMembers(UUID leader) {
        return JDBC.sql("""
            SELECT character_id, distribution_type
            FROM character_offline_play_party_members
            WHERE leader_character_id = ?\
            """)
                .param(leader)
                .query((rs, rowNum) ->
                        tuple(rs.getObject("character_id", UUID.class), rs.getString("distribution_type")))
                .list();
    }

    private static int partyMemberCount() {
        return JDBC.sql("SELECT count(*) FROM character_offline_play_party_members")
                .query(Integer.class)
                .single();
    }
}
