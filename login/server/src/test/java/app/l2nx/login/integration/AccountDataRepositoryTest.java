/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.login.repository.AccountDataRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountDataRepositoryTest extends RepositoryTest {
    private static final String VAR = "ban_temp";

    private final AccountDataRepository repository = new AccountDataRepository(JDBC);

    @Nested
    class Save {
        @Test
        void save_shouldInsertValue_whenVariableMissing() {
            final String login = uniqueLogin();

            repository.save(login, VAR, "100");

            assertThat(value(login)).isEqualTo("100");
        }

        @Test
        void save_shouldOverwriteValue_whenVariableExists() {
            final String login = uniqueLogin();
            insertAccountData(login, VAR, "100");

            repository.save(login, VAR, "200");

            assertThat(value(login)).isEqualTo("200");
        }
    }

    private static String value(String login) {
        return JDBC.sql("SELECT value FROM account_data WHERE account_name = ? AND var = ?")
                .param(login)
                .param(VAR)
                .query(String.class)
                .single();
    }
}
