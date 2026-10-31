/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus;

import app.l2nx.load.app.prometheus.model.QueryResponse;
import app.l2nx.load.app.prometheus.model.Sample;
import app.l2nx.load.app.prometheus.model.TargetsResponse;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ExchangeFunction;
import org.springframework.web.client.RestClientException;

public class PrometheusClient {
    static final MediaType TEXT_EXPOSITION = MediaType.parseMediaType("text/plain;version=0.0.4;charset=utf-8");

    private final RestClient rest;
    private final URI url;

    public PrometheusClient(RestClient.Builder builder, URI url) {
        this.rest = builder.baseUrl(url.toString()).build();
        this.url = url;
    }

    public URI url() {
        return url;
    }

    /** {@code false} while Prometheus starts up or cannot be reached. */
    public boolean ready() {
        try {
            return rest.get()
                    .uri("/-/ready")
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (RestClientException e) {
            return false;
        }
    }

    /**
     * Sent as a form: a GET would pass the PromQL braces through URI template expansion.
     *
     * @param time {@code null} evaluates at Prometheus's now
     * @throws IllegalStateException when Prometheus refuses the query or answers other than an instant vector
     * @throws HttpServerErrorException on a 5xx, such as {@code 503} while it starts: worth a retry
     */
    public List<Sample> query(String expression, Instant time) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", expression);
        if (time != null) {
            form.add("time", BigDecimal.valueOf(time.toEpochMilli(), 3).toPlainString());
        }
        return rest.post()
                .uri("/api/v1/query")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange(envelope(QueryResponse.class))
                .samples(expression);
    }

    /**
     * The longest scrape interval of every job with an active target.
     *
     * @throws IllegalStateException and {@link HttpServerErrorException} as {@link #query}
     */
    public Map<String, Duration> scrapeIntervals() {
        return rest.get()
                .uri("/api/v1/targets?state=active")
                .exchange(envelope(TargetsResponse.class))
                .scrapeIntervals();
    }

    // The error envelope names the reason (400/422 refuse the query, 503 unavailable); a proxy or wrong path answers
    // without one. A 5xx stays a RestClientException so callers can tell a retryable failure from a refusal.
    private static <T> ExchangeFunction<T> envelope(Class<T> type) {
        return (request, answer) -> {
            final HttpStatusCode status = answer.getStatusCode();
            final MediaType contentType = answer.getHeaders().getContentType();
            final boolean json = contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
            if (status.is5xxServerError()) {
                final byte[] body = answer.getBody().readAllBytes();
                throw HttpServerErrorException.create(
                        "Prometheus answered " + status.value() + " to " + request.getURI()
                                + (json ? ": " + new String(body, StandardCharsets.UTF_8) : ""),
                        status,
                        answer.getStatusText(),
                        answer.getHeaders(),
                        body,
                        StandardCharsets.UTF_8);
            }
            if (!json) {
                throw new IllegalStateException("Prometheus answered " + status.value() + " ("
                        + (contentType == null ? "no content type" : contentType) + ") to " + request.getURI());
            }
            final T body = answer.bodyTo(type);
            if (body == null) {
                throw new IllegalStateException("Prometheus answered " + request.getURI() + " with no body");
            }
            return body;
        };
    }

    public String text(URI metricsUrl) {
        return rest.get().uri(metricsUrl).accept(TEXT_EXPOSITION).retrieve().body(String.class);
    }
}
