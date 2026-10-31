/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.account;

import static app.l2nx.database.JdbcTimes.utc;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountRepository {
    // An account inside a temporary ban reads with access level -1.
    private static final String SELECT_ACCOUNT_INFO = """
        SELECT id, login, password, last_server_id,
               CASE WHEN banned_until > ? THEN -1 ELSE access_level END AS access_level
        FROM accounts
        WHERE login = ?\
        """;
    private static final String INSERT_ACCOUNT = """
        INSERT INTO accounts (id, login, password, last_active_at, last_ip)
        VALUES (?, ?, ?, ?, ?::inet)\
        """;
    private static final String UPDATE_LAST_ACTIVE =
            "UPDATE accounts SET last_active_at = ?, last_ip = ?::inet WHERE id = ?";
    private static final String UPDATE_LAST_SERVER = "UPDATE accounts SET last_server_id = ? WHERE id = ?";
    private static final String UPDATE_ACCESS_LEVEL = "UPDATE accounts SET access_level = ? WHERE id = ?";
    private static final String UPDATE_BANNED_UNTIL = "UPDATE accounts SET banned_until = ? WHERE id = ?";
    private static final String UPDATE_TRACERT = """
        UPDATE accounts
        SET pc_ip = ?::inet, hop1 = ?::inet, hop2 = ?::inet, hop3 = ?::inet, hop4 = ?::inet
        WHERE id = ?\
        """;
    private static final String SELECT_PASSWORD = "SELECT password FROM accounts WHERE id = ?";
    private static final String REPLACE_PASSWORD = "UPDATE accounts SET password = ? WHERE id = ? AND password = ?";
    private static final String SELECT_IP_RULES =
            "SELECT host(ip) AS ip, rule FROM account_ip_rules WHERE account_id = ?";

    private final JdbcClient jdbcClient;

    public Optional<AccountInfo> findAccountInfo(String login, Instant now) {
        return jdbcClient
                .sql(SELECT_ACCOUNT_INFO)
                .param(utc(now))
                .param(login)
                .query((rs, rowNum) -> AccountInfo.builder()
                        .id(rs.getObject("id", UUID.class))
                        .login(rs.getString("login"))
                        .passwordHash(rs.getString("password"))
                        .accessLevel(rs.getInt("access_level"))
                        .lastServer(rs.getInt("last_server_id"))
                        .build())
                .optional();
    }

    public UUID create(String login, String passwordHash, Instant lastActive, String lastIp) {
        final UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcClient
                .sql(INSERT_ACCOUNT)
                .param(id)
                .param(login)
                .param(passwordHash)
                .param(utc(lastActive))
                .param(lastIp)
                .update();
        return id;
    }

    public void updateLastActive(UUID id, Instant lastActive, String lastIp) {
        jdbcClient
                .sql(UPDATE_LAST_ACTIVE)
                .param(utc(lastActive))
                .param(lastIp)
                .param(id)
                .update();
    }

    public void updateLastServer(UUID id, int serverId) {
        jdbcClient.sql(UPDATE_LAST_SERVER).param(serverId).param(id).update();
    }

    /** @return {@code false} when the account does not exist */
    public boolean updateAccessLevel(UUID id, int accessLevel) {
        return jdbcClient.sql(UPDATE_ACCESS_LEVEL).param(accessLevel).param(id).update() > 0;
    }

    /** @return {@code false} when the account does not exist */
    public boolean updateBannedUntil(UUID id, Instant bannedUntil) {
        return jdbcClient
                        .sql(UPDATE_BANNED_UNTIL)
                        .param(utc(bannedUntil))
                        .param(id)
                        .update()
                > 0;
    }

    public void updateTracert(UUID id, String pcIp, String hop1, String hop2, String hop3, String hop4) {
        jdbcClient
                .sql(UPDATE_TRACERT)
                .param(pcIp)
                .param(hop1)
                .param(hop2)
                .param(hop3)
                .param(hop4)
                .param(id)
                .update();
    }

    public Optional<String> findPassword(UUID id) {
        return jdbcClient.sql(SELECT_PASSWORD).param(id).query(String.class).optional();
    }

    /** @return whether the stored hash was still {@code readHash} and is now {@code newHash} */
    public boolean replacePassword(UUID id, String readHash, String newHash) {
        return jdbcClient
                        .sql(REPLACE_PASSWORD)
                        .param(newHash)
                        .param(id)
                        .param(readHash)
                        .update()
                > 0;
    }

    public List<IpRule> findIpRules(UUID accountId) {
        return jdbcClient
                .sql(SELECT_IP_RULES)
                .param(accountId)
                .query((rs, rowNum) -> IpRule.builder()
                        .ip(rs.getString("ip"))
                        .allow("ALLOW".equals(rs.getString("rule")))
                        .build())
                .list();
    }

    @Builder(toBuilder = true)
    public record IpRule(String ip, boolean allow) {}
}
