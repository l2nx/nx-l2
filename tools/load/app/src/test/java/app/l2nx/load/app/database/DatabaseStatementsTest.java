/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseStatementsTest {
    @Nested
    class Quoted {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"l2nx_load", "_load", "user", "a"})
        void quoted_shouldQuoteTheIdentifier_whenItMatchesThePattern(String identifier) {
            assertThat(DatabaseStatements.quoted(identifier)).isEqualTo('"' + identifier + '"');
        }

        @Test
        void quoted_shouldAcceptSixtyThreeCharacters() {
            final String identifier = "a".repeat(63);

            assertThat(DatabaseStatements.quoted(identifier)).isEqualTo('"' + identifier + '"');
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(
                strings = {
                    "",
                    "L2nx_load",
                    "1load",
                    "load-db",
                    "load\"; DROP DATABASE postgres; --",
                    "load db",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                })
        void quoted_shouldFail_whenTheIdentifierDoesNotMatchThePattern(String identifier) {
            assertThatThrownBy(() -> DatabaseStatements.quoted(identifier))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(DatabaseCommands.IDENTIFIER);
        }
    }

    @Nested
    class Statements {
        @Test
        void createDatabase_shouldCopyTheTemplate_whenGivenOne() {
            assertThat(DatabaseStatements.createDatabase("l2nx_load", "l2nx_load_template"))
                    .isEqualTo("CREATE DATABASE \"l2nx_load\" TEMPLATE \"l2nx_load_template\"");
        }

        @Test
        void createSchema_shouldGiveTheSchemaToItsOwner() {
            assertThat(DatabaseStatements.createSchema("login", "l2nx_login"))
                    .isEqualTo("CREATE SCHEMA \"login\" AUTHORIZATION \"l2nx_login\"");
        }

        @Test
        void dropDatabase_shouldNotForce() {
            assertThat(DatabaseStatements.dropDatabase("l2nx_load")).isEqualTo("DROP DATABASE IF EXISTS \"l2nx_load\"");
        }

        @Test
        void renameDatabase_shouldQuoteBothNames() {
            assertThat(DatabaseStatements.renameDatabase("l2nx_load_template_new", "l2nx_load_template"))
                    .isEqualTo("ALTER DATABASE \"l2nx_load_template_new\" RENAME TO \"l2nx_load_template\"");
        }

        @Test
        void markTemplate_shouldSetTheFlag() {
            assertThat(DatabaseStatements.markTemplate("l2nx_load_template", true))
                    .isEqualTo("ALTER DATABASE \"l2nx_load_template\" IS_TEMPLATE true");
        }
    }
}
