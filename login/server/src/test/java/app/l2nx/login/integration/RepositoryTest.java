/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import app.l2nx.database.PostgresDatabase;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

abstract class RepositoryTest {
    protected static final JdbcClient JDBC =
            JdbcClient.create(PostgresDatabase.migratedSchema("login", "db/liquibase-changelog.yaml"));

    protected static String uniqueLogin() {
        return "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    protected static UUID insertAccount(String login, String password, int accessLevel) {
        final UUID id = UuidCreator.getTimeOrderedEpoch();
        JDBC.sql("INSERT INTO accounts (id, login, password, access_level) VALUES (?, ?, ?, ?)")
                .param(id)
                .param(login)
                .param(password)
                .param(accessLevel)
                .update();
        return id;
    }
}
