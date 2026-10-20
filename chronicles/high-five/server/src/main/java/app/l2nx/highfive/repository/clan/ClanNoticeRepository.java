/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.clan.model.ClanNotice;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClanNoticeRepository {
    private static final String SELECT_NOTICE = "SELECT enabled, notice FROM clan_notices WHERE clan_id = ?";
    private static final String UPSERT_NOTICE = """
        INSERT INTO clan_notices (clan_id, enabled, notice)
        VALUES (?, ?, ?)
        ON CONFLICT (clan_id) DO UPDATE SET enabled = EXCLUDED.enabled, notice = EXCLUDED.notice\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return the notice settings; the text is {@code null} when the clan wrote none
     */
    public Optional<ClanNotice> find(int clanId) {
        return registry.findId(clanId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_NOTICE)
                        .param(id)
                        .query((rs, rowNum) -> ClanNotice.builder()
                                .enabled(rs.getBoolean("enabled"))
                                .notice(rs.getString("notice"))
                                .build())
                        .optional());
    }

    /**
     * @param notice {@code null} or empty for none; at most 8192 characters
     */
    public void upsert(int clanId, String notice, boolean enabled) {
        jdbcClient
                .sql(UPSERT_NOTICE)
                .param(References.required(registry, clanId))
                .param(enabled)
                .param(textOrNull(notice))
                .update();
    }
}
