/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import static app.l2nx.load.prometheus.MetricFamilies.counter;
import static app.l2nx.load.prometheus.MetricFamilies.gauge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.Metrics.MetricFamily;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PrometheusScraperTest {
    private static final MetricFamily FAILURES = counter("l2nx_engine_failures_total", 3, "loop", "world");
    private static final MetricFamily DEPTH = gauge("l2nx_engine_inbox_depth", 7, "loop", "world");
    private static final String TEXT = """
        # TYPE l2nx_engine_inbox_depth gauge
        l2nx_engine_inbox_depth{loop="world"} 7.0
        """;

    @Nested
    class Parse {
        @Test
        void parse_shouldReadEveryFamilyByName_whenTheStreamIsDelimited() throws IOException {
            final byte[] stream = delimited(FAILURES, DEPTH);

            final Map<String, MetricFamily> actual = PrometheusScraper.parse(new ByteArrayInputStream(stream));

            assertThat(actual)
                    .containsExactly(Map.entry(FAILURES.getName(), FAILURES), Map.entry(DEPTH.getName(), DEPTH));
        }

        @Test
        void parse_shouldFail_whenAFamilyIsExposedTwice() {
            final byte[] stream = delimited(DEPTH, DEPTH);

            assertThatExceptionOfType(IOException.class)
                    .isThrownBy(() -> PrometheusScraper.parse(new ByteArrayInputStream(stream)))
                    .withMessageContaining(DEPTH.getName());
        }
    }

    @Nested
    class Scraping {
        private HttpServer server;

        @AfterEach
        void stop() {
            server.stop(0);
        }

        @Test
        void scrape_shouldReadTheProtobufExposition_whenTheEndpointServesIt() throws Exception {
            try (PrometheusScraper scraper = scraper(actuator(200))) {
                final Scrape actual = scraper.scrape();

                assertThat(actual.families()).containsOnlyKeys(FAILURES.getName(), DEPTH.getName());
                assertThat(actual.text()).isNull();
            }
        }

        @Test
        void scrapeWithText_shouldKeepTheTextExpositionVerbatim_whenTheWindowStartsOrEnds() throws Exception {
            try (PrometheusScraper scraper = scraper(actuator(200))) {
                final Scrape actual = scraper.scrapeWithText();

                assertThat(actual.families()).containsOnlyKeys(FAILURES.getName(), DEPTH.getName());
                assertThat(actual.text()).isEqualTo(TEXT);
            }
        }

        @Test
        void scrape_shouldFail_whenTheEndpointAnswersWithText() throws Exception {
            try (PrometheusScraper scraper = scraper(textOnly())) {
                assertThatExceptionOfType(IOException.class)
                        .isThrownBy(scraper::scrape)
                        .withMessageContaining("text/plain");
            }
        }

        @Test
        void scrape_shouldFail_whenTheStatusIsNotOk() throws Exception {
            try (PrometheusScraper scraper = scraper(actuator(503))) {
                assertThatExceptionOfType(IOException.class)
                        .isThrownBy(scraper::scrape)
                        .withMessageContaining("503");
            }
        }

        private PrometheusScraper scraper(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/actuator/prometheus", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.start();
            final URI endpoint =
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/actuator/prometheus");
            return new PrometheusScraper(endpoint, Duration.ofSeconds(5));
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static Handler actuator(int status) {
        return exchange -> {
            final String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept.startsWith("application/vnd.google.protobuf")) {
                respond(exchange, status, PrometheusScraper.PROTOBUF, delimited(FAILURES, DEPTH));
            } else {
                respond(exchange, status, "text/plain; version=0.0.4; charset=utf-8", text());
            }
        };
    }

    private static Handler textOnly() {
        return exchange -> respond(exchange, 200, "text/plain; version=0.0.4; charset=utf-8", text());
    }

    private static byte[] text() {
        return TEXT.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] delimited(MetricFamily... families) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (MetricFamily family : families) {
                family.writeDelimitedTo(out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
