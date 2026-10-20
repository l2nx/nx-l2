/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.item.model.CursedWeaponRow;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Activated cursed weapons; a weapon that is not activated has no row.
 */
@Repository
@RequiredArgsConstructor
public class CursedWeaponRepository {
    private static final String SELECT_WEAPONS = """
        SELECT item_template_id, character_id, holder_karma, holder_pk_kills, kill_count, ends_at
        FROM cursed_weapons\
        """;
    private static final String UPSERT_WEAPON = """
        INSERT INTO cursed_weapons (item_template_id, character_id, holder_karma, holder_pk_kills, kill_count, ends_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (item_template_id) DO UPDATE
        SET character_id = EXCLUDED.character_id, holder_karma = EXCLUDED.holder_karma,
            holder_pk_kills = EXCLUDED.holder_pk_kills, kill_count = EXCLUDED.kill_count, ends_at = EXCLUDED.ends_at\
        """;
    private static final String DELETE_WEAPON = "DELETE FROM cursed_weapons WHERE item_template_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CursedWeaponRow> findAll() {
        return jdbcClient
                .sql(SELECT_WEAPONS)
                .query((rs, rowNum) -> CursedWeaponRow.builder()
                        .itemId(rs.getInt("item_template_id"))
                        .playerId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .playerKarma(rs.getInt("holder_karma"))
                        .playerPkKills(rs.getInt("holder_pk_kills"))
                        .nbKills(rs.getInt("kill_count"))
                        .endTime(JdbcTimes.instant(rs, "ends_at"))
                        .build())
                .list();
    }

    public void save(CursedWeaponRow weapon) {
        jdbcClient
                .sql(UPSERT_WEAPON)
                .param(weapon.itemId())
                .param(References.required(registry, weapon.playerId()))
                .param(weapon.playerKarma())
                .param(weapon.playerPkKills())
                .param(weapon.nbKills())
                .param(JdbcTimes.utc(weapon.endTime()))
                .update();
    }

    public void delete(int itemId) {
        jdbcClient.sql(DELETE_WEAPON).param(itemId).update();
    }
}
