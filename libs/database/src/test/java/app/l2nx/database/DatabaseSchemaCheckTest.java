/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.database;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseSchemaCheckTest {
    private static final DataSource OWNER = PostgresDatabase.createSchema("schema_check_owner");
    private static final DataSource STRANGER = PostgresDatabase.createSchema("schema_check_stranger");

    @Nested
    class Check {
        @Test
        void check_shouldPass_whenRoleOwnsSchema() {
            assertThatCode(() -> new DatabaseSchemaCheck("schema_check_owner").check(OWNER))
                    .doesNotThrowAnyException();
        }

        @Test
        void check_shouldNameSchemaAndRole_whenSchemaMissing() {
            assertThatThrownBy(() -> new DatabaseSchemaCheck("schema_check_missing").check(OWNER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CREATE SCHEMA schema_check_missing AUTHORIZATION schema_check_owner");
        }

        @Test
        void check_shouldFail_whenRoleCannotCreateInSchema() {
            assertThatThrownBy(() -> new DatabaseSchemaCheck("schema_check_owner").check(STRANGER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ALTER SCHEMA schema_check_owner OWNER TO schema_check_stranger");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Game", "game-1", "1game", "game;drop", ""})
        void check_shouldRejectName_whenNotAPlainIdentifier(String schema) {
            assertThatThrownBy(() -> new DatabaseSchemaCheck(schema).check(OWNER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("l2nx.database.schema");
        }
    }
}
