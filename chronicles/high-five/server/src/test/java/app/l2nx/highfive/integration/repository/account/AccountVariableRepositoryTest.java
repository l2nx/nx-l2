/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.account.AccountVariableRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class AccountVariableRepositoryTest extends RepositoryTest {
    private final AccountVariableRepository repository = new AccountVariableRepository(JDBC, JDBC_TEMPLATE);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnAccountVariables_whenStored() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "HWID", "abc");
            insertVariable(accountId, "empty", null);
            insertVariable(EntityIds.next(), "HWID", "other");

            final Map<String, String> actual = repository.findAll(accountId);

            assertThat(actual).containsOnly(entry("HWID", "abc"), entry("empty", null));
        }
    }

    @Nested
    class FindByNamePrefix {
        @Test
        void findByNamePrefix_shouldReturnOnlyMatchingNames() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "secauth_pwd", "hash");
            insertVariable(accountId, "secauth_wte", "2");
            insertVariable(accountId, "secauthx", "no");
            insertVariable(accountId, "HWID", "abc");

            final Map<String, String> actual = repository.findByNamePrefix(accountId, "secauth_");

            assertThat(actual).containsOnly(entry("secauth_pwd", "hash"), entry("secauth_wte", "2"));
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreVariable_andEmptyAsNull() {
            final UUID accountId = EntityIds.next();

            repository.insert(accountId, "secauth_pwd", "hash");
            repository.insert(accountId, "blank", "");

            assertThat(variables(accountId)).containsOnly(entry("secauth_pwd", "hash"), entry("blank", null));
        }

        @Test
        void insert_shouldFail_whenVariableExists() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "secauth_pwd", "hash");

            assertThatThrownBy(() -> repository.insert(accountId, "secauth_pwd", "other"))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldChangeContent_whenVariableExists() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "secauth_pwd", "old");

            final boolean actual = repository.update(accountId, "secauth_pwd", "new");

            assertThat(actual).isTrue();
            assertThat(variables(accountId)).containsOnly(entry("secauth_pwd", "new"));
        }

        @Test
        void update_shouldReturnFalse_whenVariableMissing() {
            final UUID accountId = EntityIds.next();

            assertThat(repository.update(accountId, "secauth_pwd", "new")).isFalse();
            assertThat(variables(accountId)).isEmpty();
        }
    }

    @Nested
    class Upsert {
        @Test
        void upsert_shouldInsert_whenVariableMissing() {
            final UUID accountId = EntityIds.next();

            repository.upsert(accountId, "secauth_wte", "1");

            assertThat(variables(accountId)).containsOnly(entry("secauth_wte", "1"));
        }

        @Test
        void upsert_shouldReplaceContent_whenVariableExists() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "secauth_wte", "1");

            repository.upsert(accountId, "secauth_wte", "2");

            assertThat(variables(accountId)).containsOnly(entry("secauth_wte", "2"));
        }
    }

    @Nested
    class SaveChanges {
        @Test
        void saveChanges_shouldDeleteAndUpsertInOneTransaction() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "gone", "1");
            insertVariable(accountId, "changed", "1");
            insertVariable(accountId, "kept", "1");
            final Map<String, String> changed = new HashMap<>();
            changed.put("changed", "2");
            changed.put("added", "3");
            changed.put("blank", "");

            TRANSACTIONS.executeWithoutResult(status -> repository.saveChanges(accountId, List.of("gone"), changed));

            assertThat(variables(accountId))
                    .containsOnly(entry("changed", "2"), entry("added", "3"), entry("blank", null), entry("kept", "1"));
        }

        @Test
        void saveChanges_shouldDoNothing_whenNoChanges() {
            final UUID accountId = EntityIds.next();
            insertVariable(accountId, "kept", "1");

            repository.saveChanges(accountId, List.of(), Map.of());

            assertThat(variables(accountId)).containsOnly(entry("kept", "1"));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldDeleteOnlyThatAccount() {
            final UUID accountId = EntityIds.next();
            final UUID otherId = EntityIds.next();
            insertVariable(accountId, "a", "1");
            insertVariable(otherId, "a", "1");

            repository.deleteAll(accountId);

            assertThat(variables(accountId)).isEmpty();
            assertThat(variables(otherId)).containsOnly(entry("a", "1"));
        }
    }

    @Nested
    class DeleteByName {
        @Test
        void deleteByName_shouldDeleteTheNameFromEveryAccount() {
            final UUID accountId = EntityIds.next();
            final UUID otherId = EntityIds.next();
            final String name = "vip_" + accountId;
            insertVariable(accountId, name, "1");
            insertVariable(otherId, name, "1");
            insertVariable(otherId, "kept", "1");

            repository.deleteByName(name);

            assertThat(variables(accountId)).isEmpty();
            assertThat(variables(otherId)).containsOnly(entry("kept", "1"));
        }
    }

    private static void insertVariable(UUID accountId, String name, String content) {
        JDBC.sql("INSERT INTO account_variables (account_id, name, content) VALUES (?, ?, ?)")
                .param(accountId)
                .param(name)
                .param(content)
                .update();
    }

    private static Map<String, String> variables(UUID accountId) {
        final Map<String, String> values = new HashMap<>();
        JDBC.sql("SELECT name, content FROM account_variables WHERE account_id = ?")
                .param(accountId)
                .query(rs -> {
                    values.put(rs.getString("name"), rs.getString("content"));
                });
        return values;
    }
}
