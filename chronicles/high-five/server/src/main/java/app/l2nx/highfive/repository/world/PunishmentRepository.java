/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.world.model.Punishment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PunishmentRepository {
    private static final String SELECT_ALL = """
        SELECT id, affect, account_id, character_id, host(ip) AS ip, hwid, punishment_type, expires_at, reason,
               punished_by
        FROM punishments
        ORDER BY id\
        """;
    private static final String INSERT = """
        INSERT INTO punishments (
            affect, account_id, character_id, ip, hwid, punishment_type, expires_at, reason, punished_by)
        VALUES (?, ?, ?, ?::inet, ?, ?, ?, ?, ?)
        RETURNING id\
        """;
    private static final String UPDATE_EXPIRATION = "UPDATE punishments SET expires_at = ? WHERE id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return every stored punishment, expired ones included
     */
    public List<Punishment> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> {
                    final UUID characterId = rs.getObject("character_id", UUID.class);
                    return Punishment.builder()
                            .id(rs.getLong("id"))
                            .affect(PunishmentAffect.valueOf(rs.getString("affect")))
                            .accountId(rs.getObject("account_id", UUID.class))
                            .characterId(characterId == null ? null : registry.handleOf(characterId))
                            .ip(rs.getString("ip"))
                            .hwid(rs.getString("hwid"))
                            .type(PunishmentType.valueOf(rs.getString("punishment_type")))
                            .expiration(JdbcTimes.instant(rs, "expires_at"))
                            .reason(rs.getString("reason"))
                            .punishedBy(rs.getString("punished_by"))
                            .build();
                })
                .list();
    }

    /**
     * @return the id of the new punishment; {@code punishment.id()} is ignored, a blank reason is stored as none
     */
    public long insert(Punishment punishment) {
        return jdbcClient
                .sql(INSERT)
                .param(punishment.affect().name())
                .param(punishment.accountId())
                .param(
                        punishment.characterId() == null
                                ? null
                                : References.required(registry, punishment.characterId()))
                .param(punishment.ip())
                .param(punishment.hwid())
                .param(punishment.type().name())
                .param(JdbcTimes.utc(punishment.expiration()))
                .param(StringUtils.isBlank(punishment.reason()) ? null : punishment.reason())
                .param(punishment.punishedBy())
                .query(Long.class)
                .single();
    }

    public void updateExpiration(long id, Instant expiration) {
        jdbcClient
                .sql(UPDATE_EXPIRATION)
                .param(JdbcTimes.utc(expiration))
                .param(id)
                .update();
    }
}
