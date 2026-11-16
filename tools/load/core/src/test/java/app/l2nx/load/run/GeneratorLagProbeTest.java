/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.injection.InjectionProfile;
import app.l2nx.load.injection.RunTimeline;
import app.l2nx.load.latency.GeneratorLagMonitor;
import app.l2nx.load.scenario.model.MeasuredPhase;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Ticker;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeneratorLagProbeTest {
    private static final InjectionProfile PROFILE = InjectionProfile.builder()
            .bots(2)
            .ramp(Duration.ofSeconds(1))
            .warmUp(Duration.ofSeconds(1))
            .steady(Duration.ofSeconds(5))
            .rampDown(Duration.ofSeconds(1))
            .build();
    private static final long STEADY_TICKS = PROFILE.steady().dividedBy(GeneratorLagProbe.PERIOD);

    private final MockTicker ticker = Ticker.newMockTicker();
    private final EmbeddedChannel channel =
            EmbeddedChannel.builder().ticker(ticker).build();
    private final GeneratorLagMonitor lag = new GeneratorLagMonitor(2);

    private GeneratorLagProbe start(RunTimeline timeline) {
        final GeneratorLagProbe probe = new GeneratorLagProbe(channel.eventLoop(), timeline, lag);
        probe.start();
        channel.runPendingTasks();
        return probe;
    }

    private void runUntil(long time) {
        channel.runPendingTasks();
        while (ticker.nanoTime() - time < 0) {
            ticker.advance(100, TimeUnit.MICROSECONDS);
            channel.runPendingTasks();
        }
    }

    @Nested
    class Steady {
        private final RunTimeline timeline = new RunTimeline(PROFILE, MeasuredPhase.STEADY, ticker::nanoTime);

        @Test
        void tick_shouldRecordEveryTickOfTheSteadyWindowOnly_whenTheLoopKeepsUp() {
            start(timeline);

            runUntil(timeline.end());

            final Histogram actual = lag.histogram();
            assertThat(actual.getTotalCount()).isEqualTo(STEADY_TICKS);
            assertThat(actual.getMaxValue()).isLessThan(TimeUnit.MICROSECONDS.toNanos(101));
        }

        @Test
        void tick_shouldOweEveryMissedTick_whenTheLoopStalls() {
            start(timeline);
            runUntil(timeline.measuredStart() + TimeUnit.SECONDS.toNanos(1));

            ticker.advance(500, TimeUnit.MILLISECONDS);
            runUntil(timeline.end());

            final Histogram actual = lag.histogram();
            assertThat(actual.getTotalCount())
                    .as("the ticks the stall skipped run late, each with its own lag")
                    .isEqualTo(STEADY_TICKS);
            assertThat(actual.getMaxValue()).isBetween(499_000_000L, 501_000_000L);
            assertThat(actual.getCountBetweenValues(100_000_000L, 501_000_000L)).isBetween(399L, 401L);
        }

        @Test
        void close_shouldRecordTheDueTick_whenTheStallOutlastsTheRun() {
            final GeneratorLagProbe probe = start(timeline);

            ticker.advance(
                    timeline.measuredStart() - ticker.nanoTime() + TimeUnit.SECONDS.toNanos(2), TimeUnit.NANOSECONDS);
            probe.close();
            channel.runPendingTasks();
            runUntil(timeline.end());

            final Histogram actual = lag.histogram();
            assertThat(actual.getTotalCount()).isEqualTo(1);
            assertThat(actual.getMaxValue()).isBetween(1_999_000_000L, 2_001_000_000L);
        }
    }

    @Nested
    class Ramp {
        private final RunTimeline timeline = new RunTimeline(PROFILE, MeasuredPhase.RAMP, ticker::nanoTime);

        @Test
        void tick_shouldRecordUntilTheLastLoadBotIsDoneArriving_whenThatOutlastsTheRamp() {
            start(timeline);
            runUntil(timeline.start() + TimeUnit.SECONDS.toNanos(3));
            timeline.arrivalOver();
            timeline.arrivalOver();

            runUntil(timeline.end());

            assertThat(lag.histogram().getTotalCount())
                    .as("three seconds of ticks, the one that saw the arrivals over excluded")
                    .isBetween(2_999L, 3_001L);
        }

        @Test
        void tick_shouldRecordTheFirstTick_whenEveryLoadBotIsDoneArrivingBeforeIt() {
            timeline.arrivalOver();
            timeline.arrivalOver();

            start(timeline);
            runUntil(timeline.end());

            assertThat(lag.histogram().getTotalCount())
                    .as("the measured phase always holds its start")
                    .isEqualTo(1);
        }
    }

    @Nested
    class Levels {
        private final RunTimeline timeline = new RunTimeline(
                PROFILE.toBuilder()
                        .levels(InjectionProfile.Levels.builder()
                                .increment(1)
                                .count(2)
                                .duration(Duration.ofSeconds(3))
                                .ramp(Duration.ofSeconds(1))
                                .build())
                        .build(),
                MeasuredPhase.STEADY,
                ticker::nanoTime);

        @Test
        void tick_shouldRecordEachLevelWindowApart() {
            start(timeline);

            runUntil(timeline.end());

            final long levelTicks = Duration.ofSeconds(2).dividedBy(GeneratorLagProbe.PERIOD);
            assertThat(lag.levelHistogram(0).getTotalCount()).isEqualTo(levelTicks);
            assertThat(lag.levelHistogram(1).getTotalCount()).isEqualTo(levelTicks);
            assertThat(lag.histogram().getTotalCount())
                    .as("the run takes the level windows, not the ramp between them")
                    .isEqualTo(2 * levelTicks);
        }
    }
}
