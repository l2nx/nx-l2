/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import io.prometheus.client.Metrics.MetricFamily;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Spring Boot Actuator {@code /actuator/prometheus} endpoint in the delimited protobuf exposition, the format
 * the window math works on, and at the window's boundaries also in the text format, kept verbatim for the report.
 */
public final class PrometheusScraper implements AutoCloseable {
    static final String PROTOBUF =
            "application/vnd.google.protobuf; proto=io.prometheus.client.MetricFamily; encoding=delimited";
    static final String TEXT = "text/plain; version=0.0.4";

    private static final String PROTOBUF_MEDIA_TYPE = "application/vnd.google.protobuf";

    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient client;

    /** @param timeout limit of the connect and of every request */
    public PrometheusScraper(URI endpoint, Duration timeout) {
        this.endpoint = endpoint;
        this.timeout = timeout;
        // Actuator serves plain HTTP/1.1; the default HTTP/2 would try an h2c upgrade on every request.
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    public Scrape scrape() throws IOException, InterruptedException {
        return Scrape.builder().families(families()).build();
    }

    public Scrape scrapeWithText() throws IOException, InterruptedException {
        final Map<String, MetricFamily> families = families();
        final String text = get(TEXT, BodyHandlers.ofString()).body();
        return Scrape.builder().families(families).text(text).build();
    }

    @Override
    public void close() {
        client.close();
    }

    static Map<String, MetricFamily> parse(InputStream in) throws IOException {
        final Map<String, MetricFamily> families = new LinkedHashMap<>();
        MetricFamily family;
        while ((family = MetricFamily.parseDelimitedFrom(in)) != null) {
            if (families.putIfAbsent(family.getName(), family) != null) {
                throw new IOException("Metric family " + family.getName() + " is exposed twice");
            }
        }
        return families;
    }

    private Map<String, MetricFamily> families() throws IOException, InterruptedException {
        final HttpResponse<byte[]> response = get(PROTOBUF, BodyHandlers.ofByteArray());
        final String contentType = response.headers().firstValue("Content-Type").orElse(null);
        // A server without the protobuf exposition answers with text, which would parse as garbage.
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(PROTOBUF_MEDIA_TYPE)) {
            throw new IOException(endpoint + " answered " + contentType + " instead of the protobuf exposition");
        }
        return parse(new ByteArrayInputStream(response.body()));
    }

    private <T> HttpResponse<T> get(String accept, BodyHandler<T> handler) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", accept)
                .GET()
                .build();
        final HttpResponse<T> response = client.send(request, handler);
        if (response.statusCode() != 200) {
            throw new IOException(endpoint + " answered " + response.statusCode() + " to Accept: " + accept);
        }
        return response;
    }
}
