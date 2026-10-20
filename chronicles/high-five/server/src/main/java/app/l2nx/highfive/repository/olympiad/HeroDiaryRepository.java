/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.olympiad.model.HeroDiaryEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HeroDiaryRepository {
    // Values of the Hero.ACTION_* constants.
    private static final int RAID_KILLED = 1;
    private static final int HERO_GAINED = 2;
    private static final int CASTLE_TAKEN = 3;

    private static final String SELECT_BY_CHARACTER = """
        SELECT recorded_at, action, npc_id, castle_id
        FROM hero_diary_entries
        WHERE character_id = ?
        ORDER BY recorded_at, id\
        """;
    private static final String INSERT = """
        INSERT INTO hero_diary_entries (character_id, recorded_at, action, npc_id, castle_id)
        VALUES (?, ?, ?, ?, ?)\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return the hero's diary, oldest first
     */
    public List<HeroDiaryEntry> findByCharacter(int objectId) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_BY_CHARACTER)
                        .param(id)
                        .query((rs, rowNum) -> entry(rs))
                        .list())
                .orElseGet(List::of);
    }

    /**
     * @param action one of the {@code Hero.ACTION_*} constants
     * @param param the raid boss npc id or the castle id, by action; ignored for a gained hero status
     */
    public void insert(int objectId, Instant recordedAt, int action, int param) {
        jdbcClient
                .sql(INSERT)
                .param(References.required(registry, objectId))
                .param(JdbcTimes.utc(recordedAt))
                .param(actionName(action))
                .param(action == RAID_KILLED ? param : null)
                .param(action == CASTLE_TAKEN ? param : null)
                .update();
    }

    private static HeroDiaryEntry entry(ResultSet rs) throws SQLException {
        final int action = actionId(rs.getString("action"));
        final Integer param = switch (action) {
            case RAID_KILLED -> rs.getObject("npc_id", Integer.class);
            case CASTLE_TAKEN -> rs.getObject("castle_id", Integer.class);
            default -> 0;
        };
        return HeroDiaryEntry.builder()
                .recordedAt(JdbcTimes.instant(rs, "recorded_at"))
                .action(action)
                .param(param)
                .build();
    }

    private static String actionName(int action) {
        return switch (action) {
            case RAID_KILLED -> "RAID_KILLED";
            case HERO_GAINED -> "HERO_GAINED";
            case CASTLE_TAKEN -> "CASTLE_TAKEN";
            default -> throw new IllegalArgumentException("Unknown hero diary action " + action);
        };
    }

    private static int actionId(String action) {
        return switch (action) {
            case "RAID_KILLED" -> RAID_KILLED;
            case "HERO_GAINED" -> HERO_GAINED;
            case "CASTLE_TAKEN" -> CASTLE_TAKEN;
            default -> throw new IllegalStateException("Unknown hero diary action " + action);
        };
    }
}
