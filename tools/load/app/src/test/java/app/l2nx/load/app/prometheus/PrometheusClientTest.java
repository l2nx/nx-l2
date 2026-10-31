/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.l2nx.load.app.prometheus.model.Sample;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class PrometheusClientTest {
    private static final URI URL = URI.create("http://prometheus.test:9090");
    private static final String QUERY_URL = URL + "/api/v1/query";
    private static final String TARGETS_URL = URL + "/api/v1/targets?state=active";
    private static final String EXPRESSION = "up";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final PrometheusClient client = new PrometheusClient(builder, URL);

    @Nested
    class Query {
        @Test
        void query_shouldReadTheSamples_whenPrometheusAnswersAVector() {
            server.expect(requestTo(QUERY_URL))
                    .andRespond(withSuccess(fixture("infinity.json"), MediaType.APPLICATION_JSON));

            final List<Sample> actual = client.query(EXPRESSION, null);

            assertThat(actual).extracting(Sample::value).containsExactly(Double.POSITIVE_INFINITY);
        }

        @Test
        void query_shouldNameTheReason_whenPrometheusRefusesWithItsEnvelope() {
            server.expect(requestTo(QUERY_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(fixture("error.json")));

            assertThatIllegalStateException()
                    .isThrownBy(() -> client.query(EXPRESSION, null))
                    .withMessageContaining("bad_data");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {200, 404})
        void query_shouldNameTheStatusAndTheUrl_whenTheAnswerIsNotJson(int status) {
            server.expect(requestTo(QUERY_URL))
                    .andRespond(withStatus(HttpStatus.valueOf(status))
                            .contentType(MediaType.TEXT_HTML)
                            .body("<html>Not Prometheus</html>"));

            assertThatIllegalStateException()
                    .isThrownBy(() -> client.query(EXPRESSION, null))
                    .withMessageContaining(String.valueOf(status))
                    .withMessageContaining(QUERY_URL);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {500, 502, 503})
        void query_shouldFailAsAServerError_whenTheAnswerIs5xx(int status) {
            server.expect(requestTo(QUERY_URL))
                    .andRespond(withStatus(HttpStatus.valueOf(status))
                            .contentType(MediaType.TEXT_HTML)
                            .body("<html>Bad Gateway</html>"));

            assertThatExceptionOfType(HttpServerErrorException.class)
                    .isThrownBy(() -> client.query(EXPRESSION, null))
                    .withMessageContaining(String.valueOf(status))
                    .withMessageContaining(QUERY_URL)
                    .withMessageNotContaining("Bad Gateway");
        }

        @Test
        void query_shouldFailAsAServerErrorNamingTheReason_whenPrometheusIsUnavailable() {
            server.expect(requestTo(QUERY_URL))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"status\":\"error\",\"errorType\":\"timeout\",\"error\":\"query timed out\"}"));

            assertThatExceptionOfType(HttpServerErrorException.class)
                    .isThrownBy(() -> client.query(EXPRESSION, null))
                    .withMessageContaining("503")
                    .withMessageContaining("query timed out");
        }

        @Test
        void query_shouldNameTheStatusAndTheUrl_whenTheAnswerHasNoContentType() {
            server.expect(requestTo(QUERY_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatIllegalStateException()
                    .isThrownBy(() -> client.query(EXPRESSION, null))
                    .withMessageContaining("404")
                    .withMessageContaining(QUERY_URL);
        }
    }

    @Nested
    class ScrapeIntervals {
        @Test
        void scrapeIntervals_shouldTakeTheLongestIntervalOfEveryJob() {
            server.expect(requestTo(TARGETS_URL))
                    .andRespond(withSuccess(fixture("targets.json"), MediaType.APPLICATION_JSON));

            final Map<String, Duration> actual = client.scrapeIntervals();

            assertThat(actual).isEqualTo(Map.of("game", Duration.ofSeconds(1), "login", Duration.ofSeconds(90)));
        }

        @Test
        void scrapeIntervals_shouldNameTheReason_whenPrometheusRefuses() {
            server.expect(requestTo(TARGETS_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(fixture("error.json")));

            assertThatIllegalStateException()
                    .isThrownBy(client::scrapeIntervals)
                    .withMessageContaining("bad_data");
        }
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("prometheus/" + name);
    }
}
