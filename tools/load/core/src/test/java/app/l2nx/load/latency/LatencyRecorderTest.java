/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.latency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.report.model.FrameStats;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.StepStats;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LatencyRecorderTest {
    private final LatencyRecorder recorder = new LatencyRecorder();

    @Nested
    class Frames {
        @Test
        void frameStats_shouldBeNull_whenTheProtocolReportedNoFrame() {
            assertThat(recorder.frameStats()).isNull();
        }

        @Test
        void frameStats_shouldReportMillisecondsAndTheCount() {
            for (int millis = 1; millis <= 100; millis++) {
                recorder.recordFrame(TimeUnit.MILLISECONDS.toNanos(millis));
            }

            final FrameStats actual = recorder.frameStats();

            assertThat(actual.frames()).isEqualTo(100);
            assertThat(actual.p50Millis()).isCloseTo(50.0, within(0.1));
            assertThat(actual.p99Millis()).isCloseTo(99.0, within(0.1));
            assertThat(actual.maxMillis()).isCloseTo(100.0, within(0.1));
        }
    }

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
        void stats_shouldRaiseThePercentilesButNotTheAcknowledged_whenAnIntentTimesOutInTheMeasuredPhase() {
            for (int count = 0; count < 99; count++) {
                recorder.recordSent(IntentKind.SAY);
                recorder.recordAcknowledged(IntentKind.SAY, TimeUnit.MILLISECONDS.toNanos(1));
            }
            recorder.recordSent(IntentKind.SAY);
            recorder.recordTimeout(IntentKind.SAY, TimeUnit.SECONDS.toNanos(5));

            final IntentStats actual = recorder.stats().get(IntentKind.SAY);

            assertThat(actual.acknowledged()).isEqualTo(99);
            assertThat(actual.timeouts()).isEqualTo(1);
            assertThat(actual.maxMillis()).isCloseTo(5_000.0, within(5.0));
        }

        @Test
        void stats_shouldLeaveTheLatenciesAlone_whenATimeoutFallsOutsideTheMeasuredPhase() {
            recorder.recordSent(IntentKind.LOGIN);
            recorder.recordTimeout(IntentKind.LOGIN);

            final IntentStats actual = recorder.stats().get(IntentKind.LOGIN);

            assertThat(actual.timeouts()).isEqualTo(1);
            assertThat(recorder.histogram(IntentKind.LOGIN).getTotalCount()).isZero();
        }

        @Test
        void stats_shouldOmitKinds_whenNothingWasSent() {
            recorder.recordSent(IntentKind.MOVE_TO);

            assertThat(recorder.stats()).containsOnlyKeys(IntentKind.MOVE_TO);
        }
    }

    @Nested
    class StepTimeouts {
        @Test
        void stepStats_shouldEnterTheHistogramAtTheTimeout_whenTheStepCounts() {
            recorder.recordStep(LoadBotState.LOGIN_AUTH, TimeUnit.MILLISECONDS.toNanos(1));
            recorder.recordStepTimeout(LoadBotState.LOGIN_AUTH, TimeUnit.SECONDS.toNanos(30));

            final StepStats actual = recorder.stepStats().get(LoadBotState.LOGIN_AUTH);

            assertThat(actual.count()).isEqualTo(2);
            assertThat(actual.timeouts()).isEqualTo(1);
            assertThat(actual.maxMillis()).isCloseTo(30_000.0, within(30.0));
        }

        @Test
        void stepStats_shouldKeepARowWithoutSamples_whenTheTimeoutFallsOutsideTheMeasuredPhase() {
            recorder.recordStepTimeout(LoadBotState.LOGIN_AUTH);

            final StepStats actual = recorder.stepStats().get(LoadBotState.LOGIN_AUTH);

            assertThat(actual.count()).isZero();
            assertThat(actual.timeouts()).isEqualTo(1);
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
