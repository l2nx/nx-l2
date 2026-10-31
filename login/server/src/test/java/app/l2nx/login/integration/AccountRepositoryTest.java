/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.login.account.AccountInfo;
import app.l2nx.login.account.AccountRepository;
import app.l2nx.login.account.AccountRepository.IpRule;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class AccountRepositoryTest extends RepositoryTest {
    private static final Instant NOW = Instant.parse("2027-01-15T08:00:00Z");
    private static final String PASSWORD = "{argon2}hash";
    private static final String NEW_PASSWORD = "{argon2}new-hash";

    private final AccountRepository repository = new AccountRepository(JDBC);

    @Nested
    class FindAccountInfo {
        @Test
        void findAccountInfo_shouldReturnAccount_whenNotBanned() {
            final String login = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 3);

            final Optional<AccountInfo> actual = repository.findAccountInfo(login, NOW);

            assertThat(actual)
                    .get()
                    .extracting(AccountInfo::id, AccountInfo::login, AccountInfo::accessLevel)
                    .containsExactly(id, login, 3);
        }

        @Test
        void findAccountInfo_shouldMatchLoginIgnoringCase() {
            final String login = uniqueLogin();
            insertAccount(login, PASSWORD, 0);

            assertThat(repository.findAccountInfo(login.toUpperCase(), NOW)).isPresent();
        }

        @ParameterizedTest(name = "ban ends {0} s from now -> access level {1}")
        @CsvSource({"1, -1", "0, 3", "-1, 3"})
        void findAccountInfo_shouldApplyTempBan(long banEndOffset, int expected) {
            final String login = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 3);
            repository.updateBannedUntil(id, NOW.plusSeconds(banEndOffset));

            final Optional<AccountInfo> actual = repository.findAccountInfo(login, NOW);

            assertThat(actual).get().extracting(AccountInfo::accessLevel).isEqualTo(expected);
        }

        @Test
        void findAccountInfo_shouldBeEmpty_whenAccountMissing() {
            assertThat(repository.findAccountInfo(uniqueLogin(), NOW)).isEmpty();
        }
    }

    @Nested
    class Create {
        @Test
        void create_shouldStoreAccountWithAccessLevelZero() {
            final String login = uniqueLogin();

            final UUID id = repository.create(login, PASSWORD, NOW, "10.0.0.1");

            assertThat(account(login))
                    .containsEntry("id", id)
                    .containsEntry("password", PASSWORD)
                    .containsEntry("last_active_at", NOW)
                    .containsEntry("access_level", 0)
                    .containsEntry("last_ip", "10.0.0.1");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"plain", "{argon2}"})
        void create_shouldFail_whenThePasswordHasNoEncodingIdOrNoHash(String password) {
            assertThatThrownBy(() -> repository.create(uniqueLogin(), password, NOW, "10.0.0.1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Update {
        @Test
        void updateLastActive_shouldSetTimeAndAddress() {
            final String login = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 0);

            repository.updateLastActive(id, NOW, "10.0.0.2");

            assertThat(account(login)).containsEntry("last_active_at", NOW).containsEntry("last_ip", "10.0.0.2");
        }

        @Test
        void updateLastServer_shouldSetServer() {
            final String login = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 0);

            repository.updateLastServer(id, 7);

            assertThat(account(login)).containsEntry("last_server_id", 7);
        }

        @Test
        void updateAccessLevel_shouldSetAccessLevelOfAddressedAccountOnly() {
            final String login = uniqueLogin();
            final String other = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 0);
            insertAccount(other, PASSWORD, 0);

            final boolean actual = repository.updateAccessLevel(id, -1);

            assertThat(actual).isTrue();
            assertThat(account(login)).containsEntry("access_level", -1);
            assertThat(account(other)).containsEntry("access_level", 0);
        }

        @Test
        void updateBannedUntil_shouldSetBanEnd() {
            final String login = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 0);

            final boolean actual = repository.updateBannedUntil(id, NOW);

            assertThat(actual).isTrue();
            assertThat(account(login)).containsEntry("banned_until", NOW);
        }

        @Test
        void updateAccessLevel_shouldReturnFalse_whenTheAccountDoesNotExist() {
            assertThat(repository.updateAccessLevel(UUID.randomUUID(), -1)).isFalse();
        }

        @Test
        void updateBannedUntil_shouldReturnFalse_whenTheAccountDoesNotExist() {
            assertThat(repository.updateBannedUntil(UUID.randomUUID(), NOW)).isFalse();
        }

        @Test
        void updateTracert_shouldSetAddressAndHops() {
            final String login = uniqueLogin();
            final UUID id = insertAccount(login, PASSWORD, 0);

            repository.updateTracert(id, "10.0.0.1", "10.0.0.2", "10.0.0.3", null, "10.0.0.5");

            assertThat(account(login))
                    .containsEntry("pc_ip", "10.0.0.1")
                    .containsEntry("hop1", "10.0.0.2")
                    .containsEntry("hop2", "10.0.0.3")
                    .containsEntry("hop3", null)
                    .containsEntry("hop4", "10.0.0.5");
        }
    }

    @Nested
    class ReplacePassword {
        @Test
        void replacePassword_shouldChange_whenTheStoredHashIsTheReadOne() {
            final UUID id = insertAccount(uniqueLogin(), PASSWORD, 0);

            final boolean actual = repository.replacePassword(id, PASSWORD, NEW_PASSWORD);

            assertThat(actual).isTrue();
            assertThat(repository.findPassword(id)).contains(NEW_PASSWORD);
        }

        @Test
        void replacePassword_shouldKeep_whenTheStoredHashChanged() {
            final UUID id = insertAccount(uniqueLogin(), PASSWORD, 0);

            final boolean actual = repository.replacePassword(id, "{argon2}read-before-a-change", NEW_PASSWORD);

            assertThat(actual).isFalse();
            assertThat(repository.findPassword(id)).contains(PASSWORD);
        }

        @Test
        void replacePassword_shouldReturnFalse_whenAccountMissing() {
            assertThat(repository.replacePassword(UUID.randomUUID(), PASSWORD, NEW_PASSWORD))
                    .isFalse();
        }
    }

    @Nested
    class FindPassword {
        @Test
        void findPassword_shouldReturnStoredHash() {
            final UUID id = insertAccount(uniqueLogin(), PASSWORD, 0);

            assertThat(repository.findPassword(id)).contains(PASSWORD);
        }

        @Test
        void findPassword_shouldBeEmpty_whenAccountMissing() {
            assertThat(repository.findPassword(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    class FindIpRules {
        @Test
        void findIpRules_shouldReturnRulesOfAccount() {
            final UUID id = insertAccount(uniqueLogin(), PASSWORD, 0);
            insertIpRule(id, "10.0.0.1", "ALLOW");
            insertIpRule(id, "10.0.0.2", "DENY");
            insertIpRule(insertAccount(uniqueLogin(), PASSWORD, 0), "10.0.0.3", "DENY");

            final List<IpRule> actual = repository.findIpRules(id);

            assertThat(actual)
                    .extracting(IpRule::ip, IpRule::allow)
                    .containsExactlyInAnyOrder(tuple("10.0.0.1", true), tuple("10.0.0.2", false));
        }
    }

    private static void insertIpRule(UUID accountId, String ip, String rule) {
        JDBC.sql("INSERT INTO account_ip_rules (account_id, ip, rule) VALUES (?, ?::inet, ?)")
                .param(accountId)
                .param(ip)
                .param(rule)
                .update();
    }

    private static Map<String, Object> account(String login) {
        final Map<String, Object> row =
                new HashMap<>(JDBC.sql("""
                    SELECT id, password, last_active_at, banned_until, access_level, last_server_id,
                           host(last_ip) AS last_ip, host(pc_ip) AS pc_ip, host(hop1) AS hop1,
                           host(hop2) AS hop2, host(hop3) AS hop3, host(hop4) AS hop4
                    FROM accounts
                    WHERE login = ?\
                    """).param(login).query().singleRow());
        for (String column : List.of("last_active_at", "banned_until")) {
            if (row.get(column) instanceof Timestamp time) {
                row.put(column, time.toInstant());
            }
        }
        return row;
    }
}
