/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/** The envelope of {@code /api/v1/query}; only instant vectors are read. */
@Builder(toBuilder = true)
public record QueryResponse(String status, Data data, String errorType, String error) {
    @Builder(toBuilder = true)
    public record Data(String resultType, List<Series> result) {
        public Data {
            result = result == null ? null : List.copyOf(result);
        }
    }

    @Builder(toBuilder = true)
    public record Series(Map<String, String> metric, Point value) {
        public Series {
            metric = metric == null ? null : Map.copyOf(metric);
        }
    }

    /** {@code [<unix time>, "<value>"]}: Prometheus sends the value as a string so NaN and infinities survive JSON. */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @Builder(toBuilder = true)
    public record Point(double time, String value) {}

    /** @throws IllegalStateException on an error envelope or a result other than an instant vector */
    public List<Sample> samples(String expression) {
        if (!"success".equals(status)) {
            throw new IllegalStateException("Prometheus refused " + expression + ": " + errorType + ": " + error);
        }
        if (!"vector".equals(data.resultType())) {
            throw new IllegalStateException(expression + " returned a " + data.resultType() + ", not a vector");
        }
        return data.result().stream()
                .map(series -> new Sample(series.metric(), parse(series.value().value())))
                .toList();
    }

    /** A sample value or an {@code le} bound: {@link Double#parseDouble} reads NaN but not Go's +Inf and -Inf. */
    public static double parse(String value) {
        return switch (value) {
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(value);
        };
    }
}
