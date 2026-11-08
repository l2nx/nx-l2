/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.model.LoadBotState;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoadPropertiesTest {
    @Nested
    class Levels {
        @ParameterizedTest(name = "increment {0}, count {1}, duration {2} s -> {3}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "null, null, null, true",
                    "250, 6, 90, true",
                    "250, null, 90, false",
                    "null, 6, null, false",
                    "250, 6, null, false"
                })
        void isComplete_shouldRequireIncrementCountAndDurationTogether(
                Integer increment, Integer count, Integer durationSeconds, boolean expected) {
            final LoadProperties.Levels actual = LoadProperties.Levels.builder()
                    .increment(increment)
                    .count(count)
                    .duration(durationSeconds == null ? null : Duration.ofSeconds(durationSeconds))
                    .ramp(Duration.ZERO)
                    .build();

            assertThat(actual.isComplete()).isEqualTo(expected);
        }
    }

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
    class Prometheus {
        private static final URI METRICS = URI.create("http://127.0.0.1:8777/actuator/prometheus");

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"game login, true", "game game, false", "game, true"})
        void isJobsDistinct_shouldRefuse_whenAJobRepeats(String jobs, boolean expected) {
            final LoadProperties.Prometheus actual = LoadProperties.Prometheus.builder()
                    .url(URI.create("http://127.0.0.1:9090"))
                    .targets(Stream.of(jobs.split(" "))
                            .map(job -> new LoadProperties.Target(job, METRICS))
                            .toList())
                    .build();

            assertThat(actual.isJobsDistinct()).isEqualTo(expected);
        }

        @Test
        void isJobsDistinct_shouldAccept_whenTargetsOrJobsAreMissing() {
            final LoadProperties.Prometheus actual = LoadProperties.Prometheus.builder()
                    .url(URI.create("http://127.0.0.1:9090"))
                    .targets(Arrays.asList(
                            new LoadProperties.Target("game", METRICS),
                            null,
                            new LoadProperties.Target(" ", METRICS),
                            new LoadProperties.Target(null, METRICS)))
                    .build();

            assertThat(actual.isJobsDistinct())
                    .as("a missing target or job is reported by its own constraint")
                    .isTrue();
        }

        @ParameterizedTest(name = "[{0}] -> {1}")
        @CsvSource({"game, true", "game_2, true", "Game-1, false", "1game, false"})
        void validate_shouldTakeOnlyAPlainIdentifierAsJob(String job, boolean expected) {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                final boolean actual = factory.getValidator()
                        .validate(new LoadProperties.Target(job, METRICS))
                        .isEmpty();

                assertThat(actual).as("a PromQL label value").isEqualTo(expected);
            }
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
        void isSchemasDistinct_shouldAccept_whenSchemaNamesAreMissing() {
            assertThat(database("l2nx_load", "l2nx_load_template", Arrays.asList("login", null, null))
                            .isSchemasDistinct())
                    .as("a missing name is reported by its own constraint")
                    .isTrue();
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
