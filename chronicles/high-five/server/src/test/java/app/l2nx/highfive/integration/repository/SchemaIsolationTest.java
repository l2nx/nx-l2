/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.database.PostgresDatabase;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

class SchemaIsolationTest {
    private static final String CHANGELOG = "db/liquibase-changelog.yaml";

    @Nested
    class TwoGameSchemas {
        @Test
        void migrate_shouldKeepRowsApart_whenTwoServersShareDatabase() {
            final JdbcClient first = JdbcClient.create(PostgresDatabase.migratedSchema("game", CHANGELOG));
            final DataSource secondSource = PostgresDatabase.migratedSchema("game", CHANGELOG);
            final JdbcClient second = JdbcClient.create(secondSource);
            first.sql("INSERT INTO global_variables (name, content) VALUES ('isolation', 'first')")
                    .update();

            assertThat(second.sql("SELECT count(*) FROM global_variables WHERE name = 'isolation'")
                            .query(Integer.class)
                            .single())
                    .isZero();
        }

        @Test
        void read_shouldBeDenied_whenRoleReadsAnotherServersSchema() {
            final JdbcClient first = JdbcClient.create(PostgresDatabase.migratedSchema("game", CHANGELOG));
            final String firstSchema =
                    first.sql("SELECT current_schema()").query(String.class).single();
            final JdbcClient second = JdbcClient.create(PostgresDatabase.migratedSchema("game", CHANGELOG));

            assertThatThrownBy(() -> second.sql("SELECT count(*) FROM " + firstSchema + ".global_variables")
                            .query(Integer.class)
                            .single())
                    .isInstanceOf(DataAccessException.class)
                    .rootCause()
                    .hasMessageContaining("permission denied");
        }
    }
}
