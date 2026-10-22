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

class DatabaseCommandsTest {
    @Nested
    class TemplateNames {
        @Test
        void of_shouldDeriveTheStagingAndTheRetiredName() {
            assertThat(DatabaseCommands.TemplateNames.of("l2nx_load_template"))
                    .isEqualTo(new DatabaseCommands.TemplateNames(
                            "l2nx_load_template", "l2nx_load_template_new", "l2nx_load_template_old"));
        }

        @Test
        void of_shouldAcceptTheLongestTemplate_whoseCopiesStillFitAnIdentifier() {
            final DatabaseCommands.TemplateNames actual = DatabaseCommands.TemplateNames.of("a".repeat(59));

            assertThat(DatabaseStatements.quoted(actual.staging())).isNotNull();
            assertThat(DatabaseStatements.quoted(actual.retired())).isNotNull();
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "L2nx_template"})
        void of_shouldFail_whenTheTemplateDoesNotMatchItsPattern(String template) {
            assertThatThrownBy(() -> DatabaseCommands.TemplateNames.of(template))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(DatabaseCommands.TEMPLATE_IDENTIFIER);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"t", "t_new", "t_old"})
        void contains_shouldNameTheTemplateAndItsCopies(String database) {
            assertThat(DatabaseCommands.TemplateNames.of("t").contains(database))
                    .isTrue();
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"t_tmp", "tt"})
        void contains_shouldRefuseOtherNames(String database) {
            assertThat(DatabaseCommands.TemplateNames.of("t").contains(database))
                    .isFalse();
        }
    }
}
