/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.model.LoadBotState;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoadPropertiesTest {
    @Nested
    class LoginStorm {
        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"LOGIN_CONNECT", "CLOSING", "STOPPED"},
                mode = EnumSource.Mode.EXCLUDE)
        void isFinalStateValid_shouldAccept_whenTheStateLiesBetweenConnectAndTheGame(LoadBotState state) {
            assertThat(new LoadProperties.LoginStorm(state).isFinalStateValid()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = LoadBotState.class,
                names = {"LOGIN_CONNECT", "CLOSING", "STOPPED"})
        void isFinalStateValid_shouldRefuse_whenTheStateMeasuresNothingOrEndsTheSession(LoadBotState state) {
            assertThat(new LoadProperties.LoginStorm(state).isFinalStateValid()).isFalse();
        }
    }

    @Nested
    class Accounts {
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", "   "})
        void create_shouldTakeABlankPasswordAsNone(String password) {
            final LoadProperties.Accounts actual = LoadProperties.Accounts.builder()
                    .prefix("load")
                    .password(password)
                    .build();

            assertThat(actual.password()).isNull();
        }

        @Test
        void create_shouldTrimThePrefixAndKeepThePasswordAsGiven() {
            final LoadProperties.Accounts actual = LoadProperties.Accounts.builder()
                    .prefix(" load ")
                    .password(" secret ")
                    .build();

            assertThat(actual).isEqualTo(new LoadProperties.Accounts("load", " secret "));
        }
    }

    @Nested
    class Database {
        @ParameterizedTest(name = "{0} from {1} -> {2}")
        @CsvSource({
            "l2nx_load, l2nx_load_template, true",
            "l2nx_load, l2nx_load, false",
            "l2nx_load_new, l2nx_load, false",
            "l2nx_load_old, l2nx_load, false",
            "l2nx_load_template_new, l2nx_load, true",
            "l2nx_load, L2NX-template, true"
        })
        void isNameDistinct_shouldRefuseTheTemplateAndItsSnapshotCopies(
                String name, String template, boolean expected) {
            assertThat(database(name, template, List.of("login", "game")).isNameDistinct())
                    .isEqualTo(expected);
        }

        @Test
        void isSchemasDistinct_shouldRefuse_whenASchemaRepeats() {
            assertThat(database("l2nx_load", "l2nx_load_template", List.of("login", "login"))
                            .isSchemasDistinct())
                    .isFalse();
        }

        @Test
        void create_shouldKeepThePasswordAsGiven_whenItIsNotBlank() {
            final LoadProperties.Database actual =
                    database("l2nx_load", "l2nx_load_template", List.of("login")).toBuilder()
                            .password(" pass ")
                            .build();

            assertThat(actual.password()).isEqualTo(" pass ");
        }

        private static LoadProperties.Database database(String name, String template, List<String> schemas) {
            return LoadProperties.Database.builder()
                    .adminUrl("jdbc:postgresql://localhost:5432/postgres")
                    .name(name)
                    .template(template)
                    .schemas(schemas.stream()
                            .map(schema -> LoadProperties.Schema.builder()
                                    .name(schema)
                                    .owner("l2nx_" + schema)
                                    .build())
                            .toList())
                    .build();
        }
    }
}
