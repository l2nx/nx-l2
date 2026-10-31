/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Builder;

/**
 * The wall-clock interval the server meters were queried over: every PromQL range is {@code [<rangeMillis>ms]} ending
 * at {@code end}, so {@code start} is {@code end} minus the range.
 */
@Builder(toBuilder = true)
public record ServerWindow(Instant start, Instant end, long rangeMillis) {
    /**
     * An empty phase still gets the shortest range PromQL accepts, one millisecond before {@code end}.
     */
    public static ServerWindow of(Instant start, Instant end) {
        final Instant queriedEnd = end.truncatedTo(ChronoUnit.MILLIS);
        final long rangeMillis = Math.max(1, Duration.between(start, queriedEnd).toMillis());
        return ServerWindow.builder()
                .start(queriedEnd.minusMillis(rangeMillis))
                .end(queriedEnd)
                .rangeMillis(rangeMillis)
                .build();
    }
}
