/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.latency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.report.model.IntentStats;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LatencyRecorderTest {
    private final LatencyRecorder recorder = new LatencyRecorder();

    @Nested
    class Stats {
        @Test
        void stats_shouldReportMillisecondsAndCounts_whenIntentsWereSent() {
            for (int millis = 1; millis <= 100; millis++) {
                recorder.recordSent(IntentKind.SAY);
                recorder.recordAcknowledged(IntentKind.SAY, TimeUnit.MILLISECONDS.toNanos(millis));
            }
            recorder.recordSent(IntentKind.SAY);
            recorder.recordTimeout(IntentKind.SAY);
            recorder.recordSent(IntentKind.SAY);
            recorder.recordError(IntentKind.SAY);

            final IntentStats actual = recorder.stats().get(IntentKind.SAY);

            assertThat(actual.sent()).isEqualTo(102);
            assertThat(actual.acknowledged()).isEqualTo(100);
            assertThat(actual.timeouts()).isEqualTo(1);
            assertThat(actual.errors()).isEqualTo(1);
            assertThat(actual.p50Millis()).isCloseTo(50.0, within(0.1));
            assertThat(actual.p99Millis()).isCloseTo(99.0, within(0.1));
            assertThat(actual.maxMillis()).isCloseTo(100.0, within(0.1));
        }

        @Test
        void stats_shouldOmitKinds_whenNothingWasSent() {
            recorder.recordSent(IntentKind.MOVE_TO);

            assertThat(recorder.stats()).containsOnlyKeys(IntentKind.MOVE_TO);
        }
    }

    @Nested
    class Histograms {
        @Test
        void histogram_shouldKeepEveryValue_whenReadRepeatedly() {
            recorder.recordAcknowledged(IntentKind.MOVE_TO, 1_000);
            recorder.histogram(IntentKind.MOVE_TO);
            recorder.recordAcknowledged(IntentKind.MOVE_TO, 2_000);

            assertThat(recorder.histogram(IntentKind.MOVE_TO).getTotalCount()).isEqualTo(2);
            assertThat(recorder.histogram(IntentKind.MOVE_TO).getTotalCount()).isEqualTo(2);
        }
    }

    @Nested
    class Errors {
        @Test
        void recordError_shouldCountPerKind() {
            recorder.recordError(IntentKind.SAY);
            recorder.recordError(IntentKind.SAY);

            assertThat(recorder.errors(IntentKind.SAY)).isEqualTo(2);
            assertThat(recorder.errors(IntentKind.MOVE_TO)).isZero();
        }
    }
}
