/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServerWindowTest {
    private static final Instant END = Instant.parse("2026-10-31T10:02:00.250Z");

    @Nested
    class Of {
        @ParameterizedTest(name = "start {0} ns before, end {1} ns after a millisecond: range {2} ms")
        @CsvSource({
            "120000000000, 0, 120000",
            "120000400000, 0, 120000",
            "1500000, 999999, 1",
            "0, 0, 1",
            "0, 999999, 1"
        })
        void of_shouldRangeOverWholeMillisecondsEndingAtTheTruncatedEnd(
                long startNanosBefore, long endNanosAfter, long expectedRangeMillis) {
            final ServerWindow actual = ServerWindow.of(END.minusNanos(startNanosBefore), END.plusNanos(endNanosAfter));

            final ServerWindow expected = ServerWindow.builder()
                    .start(END.minusMillis(expectedRangeMillis))
                    .end(END)
                    .rangeMillis(expectedRangeMillis)
                    .build();
            assertThat(actual).isEqualTo(expected);
        }
    }
}
