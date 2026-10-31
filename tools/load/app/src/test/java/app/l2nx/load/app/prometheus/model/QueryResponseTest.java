/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** Answers recorded from a real Prometheus scraping itself. */
class QueryResponseTest {
    private static final String EXPRESSION = "a query";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Nested
    class Samples {
        @Test
        void samples_shouldKeepLabelsAndNaN_whenTheResultIsAVector() throws IOException {
            final List<Sample> actual = read("vector.json").samples(EXPRESSION);

            assertThat(actual).isNotEmpty().allMatch(sample -> sample.labels().containsKey("handler"));
            assertThat(actual)
                    .as("a quantile of a handler never hit in the range")
                    .anyMatch(sample -> Double.isNaN(sample.value()));
            assertThat(actual)
                    .as("a quantile of a handler hit in the range")
                    .anyMatch(sample -> sample.value() > 0 && Double.isFinite(sample.value()));
        }

        @Test
        void samples_shouldBeEmpty_whenNoSeriesMatches() throws IOException {
            final List<Sample> actual = read("empty.json").samples(EXPRESSION);

            assertThat(actual).isEmpty();
        }

        @Test
        void samples_shouldReadGoInfinity() throws IOException {
            final List<Sample> actual = read("infinity.json").samples(EXPRESSION);

            assertThat(actual).extracting(Sample::value).containsExactly(Double.POSITIVE_INFINITY);
        }

        @Test
        void samples_shouldNameTheExpressionAndTheReason_whenPrometheusRefusesTheQuery() throws IOException {
            final QueryResponse response = read("error.json");

            assertThatIllegalStateException()
                    .isThrownBy(() -> response.samples(EXPRESSION))
                    .withMessageContaining(EXPRESSION)
                    .withMessageContaining("bad_data");
        }

        @Test
        void samples_shouldRefuse_whenTheResultIsNotAVector() throws IOException {
            final QueryResponse response = read("matrix.json");

            assertThatIllegalStateException()
                    .isThrownBy(() -> response.samples(EXPRESSION))
                    .withMessageContaining("matrix");
        }
    }

    @Nested
    class Parse {
        @ParameterizedTest(name = "{0}")
        @CsvSource({"+Inf, Infinity", "-Inf, -Infinity", "NaN, NaN", "0.25, 0.25"})
        void parse_shouldReadPrometheusValues(String value, double expected) {
            // By comparison, which takes NaN as equal to itself.
            assertThat(QueryResponse.parse(value)).isEqualByComparingTo(expected);
        }
    }

    @Nested
    class Copy {
        @Test
        void samples_shouldKeepTheSeries_whenTheSourceChanges() {
            final Map<String, String> metric = new HashMap<>(Map.of("job", "game"));
            final List<QueryResponse.Series> result = new ArrayList<>(List.of(QueryResponse.Series.builder()
                    .metric(metric)
                    .value(QueryResponse.Point.builder().time(1).value("1").build())
                    .build()));
            final QueryResponse response = QueryResponse.builder()
                    .status("success")
                    .data(QueryResponse.Data.builder()
                            .resultType("vector")
                            .result(result)
                            .build())
                    .build();

            metric.put("job", "login");
            result.clear();

            assertThat(response.samples(EXPRESSION))
                    .singleElement()
                    .satisfies(sample -> assertThat(sample.labels()).isEqualTo(Map.of("job", "game")));
        }
    }

    private QueryResponse read(String fixture) throws IOException {
        try (InputStream json = QueryResponseTest.class.getResourceAsStream("/prometheus/" + fixture)) {
            return jsonMapper.readValue(json, QueryResponse.class);
        }
    }
}
