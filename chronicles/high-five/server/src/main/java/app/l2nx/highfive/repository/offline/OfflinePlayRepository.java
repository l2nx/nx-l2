/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.entity.groups.PartyDistributionType;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.offline.model.OfflinePlayEntry;
import app.l2nx.highfive.repository.offline.model.OfflinePlayEntryType;
import app.l2nx.highfive.repository.offline.model.OfflinePlayPartyMember;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class OfflinePlayRepository {
    private static final String SELECT_CHARACTER_IDS =
            "SELECT DISTINCT character_id FROM character_offline_play_settings";
    private static final String SELECT_ENTRIES = """
        SELECT entry_type, target_id
        FROM character_offline_play_settings
        WHERE character_id = ?
        ORDER BY id\
        """;
    // Legacy data may repeat an entry: drop it instead of failing the batch.
    private static final String INSERT_ENTRY = """
        INSERT INTO character_offline_play_settings (character_id, entry_type, target_id)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id, entry_type, target_id) DO NOTHING\
        """;
    private static final String DELETE_ENTRIES = "DELETE FROM character_offline_play_settings WHERE character_id = ?";
    private static final String SELECT_LEADER_IDS =
            "SELECT DISTINCT leader_character_id FROM character_offline_play_party_members";
    private static final String SELECT_PARTY_MEMBERS = """
        SELECT character_id, distribution_type
        FROM character_offline_play_party_members
        WHERE leader_character_id = ?\
        """;
    private static final String INSERT_PARTY_MEMBER = """
        INSERT INTO character_offline_play_party_members (leader_character_id, character_id, distribution_type)
        VALUES (?, ?, ?)\
        """;
    private static final String DELETE_PARTY_MEMBERS = "TRUNCATE TABLE character_offline_play_party_members";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<Integer> findCharacterIds() {
        return jdbcClient
                .sql(SELECT_CHARACTER_IDS)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("character_id", UUID.class)))
                .list();
    }

    public List<OfflinePlayEntry> findEntries(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_ENTRIES)
                        .param(id)
                        .query((rs, rowNum) -> OfflinePlayEntry.builder()
                                .type(OfflinePlayEntryType.valueOf(rs.getString("entry_type")))
                                .targetId(rs.getInt("target_id"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    @Transactional
    public void replaceEntries(int characterId, Collection<OfflinePlayEntry> entries) {
        final UUID id = References.required(registry, characterId);
        jdbcClient.sql(DELETE_ENTRIES).param(id).update();
        if (entries.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_ENTRY,
                entries.stream()
                        .map(entry -> new Object[] {id, entry.type().name(), targetId(entry)})
                        .toList());
    }

    public void deleteEntries(int characterId) {
        registry.findId(characterId)
                .ifPresent(id -> jdbcClient.sql(DELETE_ENTRIES).param(id).update());
    }

    public List<Integer> findPartyLeaderIds() {
        return jdbcClient
                .sql(SELECT_LEADER_IDS)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("leader_character_id", UUID.class)))
                .list();
    }

    public List<OfflinePlayPartyMember> findPartyMembers(int leaderId) {
        return registry.findId(leaderId)
                .map(id -> jdbcClient
                        .sql(SELECT_PARTY_MEMBERS)
                        .param(id)
                        .query((rs, rowNum) -> OfflinePlayPartyMember.builder()
                                .leaderId(leaderId)
                                .characterId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                                .distributionType(PartyDistributionType.valueOf(rs.getString("distribution_type")))
                                .build())
                        .list())
                .orElse(List.of());
    }

    public void insertPartyMembers(Collection<OfflinePlayPartyMember> members) {
        if (members.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_PARTY_MEMBER,
                members.stream()
                        .map(member -> new Object[] {
                            References.required(registry, member.leaderId()),
                            References.required(registry, member.characterId()),
                            member.distributionType().name()
                        })
                        .toList());
    }

    public void deleteAllPartyMembers() {
        jdbcClient.sql(DELETE_PARTY_MEMBERS).update();
    }

    private static Integer targetId(OfflinePlayEntry entry) {
        return entry.type() == OfflinePlayEntryType.CUSTOM_AUTO_POTION_SYSTEM ? null : entry.targetId();
    }
}
