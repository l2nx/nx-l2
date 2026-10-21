/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import static app.l2nx.load.prometheus.MetricFamilies.counter;
import static app.l2nx.load.prometheus.MetricFamilies.counterMetric;
import static app.l2nx.load.prometheus.MetricFamilies.family;
import static app.l2nx.load.prometheus.MetricFamilies.gauge;
import static app.l2nx.load.prometheus.MetricFamilies.histogramMetric;
import static app.l2nx.load.prometheus.MetricFamilies.scrape;
import static app.l2nx.load.prometheus.MetricFamilies.scrapes;
import static app.l2nx.load.prometheus.MetricFamilies.summaryMetric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import io.prometheus.client.Metrics.MetricFamily;
import io.prometheus.client.Metrics.MetricType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.Builder;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MetricWindowTest {
    private static final String PASS = "l2nx_engine_pass_seconds";
    private static final double INF = Double.POSITIVE_INFINITY;

    @Nested
    class Constructor {
        @ParameterizedTest(name = "{0} scrapes")
        @ValueSource(ints = {0, 1})
        void constructor_shouldReject_whenTheWindowLacksItsBounds(int size) {
            final List<Scrape> scrapes =
                    IntStream.range(0, size).mapToObj(i -> scrape()).toList();

            assertThatIllegalArgumentException().isThrownBy(() -> new MetricWindow(scrapes));
        }
    }

    @Nested
    class BucketQuantile {
        private static final NavigableMap<Double, Double> SPREAD =
                buckets(0.1, 10, 0.2, 30, 0.5, 90, 1.0, 100, INF, 100);

        @ParameterizedTest(name = "{0}: q={1}")
        @MethodSource("promqlResults")
        void bucketQuantile_shouldMatchPromql_whenBucketsHoldObservations(
                String name, double q, NavigableMap<Double, Double> buckets, double expected) {
            final double actual = MetricWindow.bucketQuantile(q, buckets);

            assertThat(actual).isCloseTo(expected, within(1e-12));
        }

        static Stream<Arguments> promqlResults() {
            return Stream.of(
                    Arguments.of("inside the lowest bucket, from 0", 0.05, SPREAD, 0.05),
                    Arguments.of("upper bound of the lowest bucket", 0.1, SPREAD, 0.1),
                    Arguments.of("interpolated in the middle", 0.5, SPREAD, 0.3),
                    Arguments.of("interpolated in the highest finite bucket", 0.99, SPREAD, 0.95),
                    Arguments.of("rank in +Inf takes the highest finite bound", 0.9, buckets(0.1, 10, INF, 20), 0.1),
                    Arguments.of("lowest bucket at or below 0", 0.2, buckets(0.0, 5, 1.0, 10, INF, 10), 0.0),
                    Arguments.of("non-monotonic counts are forced up", 0.5, buckets(0.1, 10, 0.2, 8, INF, 20), 0.1));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("noRank")
        void bucketQuantile_shouldBeNaN_whenNoRankCanBeTaken(
                String name, double q, NavigableMap<Double, Double> buckets) {
            final double actual = MetricWindow.bucketQuantile(q, buckets);

            assertThat(actual).isNaN();
        }

        static Stream<Arguments> noRank() {
            return Stream.of(
                    Arguments.of("no +Inf bucket", 0.5, buckets(0.1, 10, 0.2, 20)),
                    Arguments.of("no observations", 0.5, buckets(0.1, 0, INF, 0)),
                    Arguments.of("only the +Inf bucket", 0.5, buckets(INF, 10)),
                    Arguments.of("q is NaN", Double.NaN, SPREAD));
        }

        @ParameterizedTest(name = "q={0}")
        @ValueSource(doubles = {-0.1, 1.1})
        void bucketQuantile_shouldBeInfinite_whenQIsOutOfRange(double q) {
            final double actual = MetricWindow.bucketQuantile(q, SPREAD);

            assertThat(actual).isInfinite().isEqualTo(Math.signum(q) * INF);
        }
    }

    @Nested
    class HistogramQuantile {
        private static final double[] LINEAR =
                IntStream.rangeClosed(1, 100).mapToDouble(i -> i / 100.0).toArray();
        private static final double[] EXPONENTIAL =
                IntStream.rangeClosed(0, 12).mapToDouble(i -> 0.001 * (1 << i)).toArray();
        private static final double WARM_UP_VALUE = 0.95;

        @ParameterizedTest(name = "q={0}")
        @ValueSource(doubles = {0.5, 0.9, 0.99, 0.999})
        void histogramQuantile_shouldMatchHdrHistogram_whenDataIsUniformInsideBuckets(double q) {
            final SplittableRandom random = new SplittableRandom(42);
            final KnownData data = KnownData.record(LINEAR, random::nextDouble);

            final double actual =
                    data.window().histogram(PASS, "loop").quantile(q).get(Map.of("loop", "world"));

            assertThat(actual).isCloseTo(data.exact(q), within(0.003));
        }

        @ParameterizedTest(name = "q={0}")
        @ValueSource(doubles = {0.5, 0.9, 0.99, 0.999})
        void histogramQuantile_shouldFallInTheBucketOfHdrHistogram_whenDataIsSkewed(double q) {
            final SplittableRandom random = new SplittableRandom(7);
            final KnownData data = KnownData.record(EXPONENTIAL, () -> -0.02 * Math.log(1 - random.nextDouble()));

            final double actual =
                    data.window().histogram(PASS, "loop").quantile(q).get(Map.of("loop", "world"));

            final double exact = data.exact(q);
            final int bucket = Math.abs(Arrays.binarySearch(EXPONENTIAL, exact) + 1);
            final double lower = bucket == 0 ? 0 : EXPONENTIAL[bucket - 1];
            assertThat(actual).isBetween(lower, EXPONENTIAL[bucket]);
        }

        @Test
        void histogramQuantile_shouldSumTheSeriesOfAGroup_whenOtherLabelsDiffer() {
            final double[] bounds = {0.1, 0.2};
            final List<Scrape> scrapes = scrapes(
                    family(
                            PASS,
                            MetricType.HISTOGRAM,
                            histogramMetric(bounds, new long[] {0, 0}, 0, 0, "loop", "world", "application", "a"),
                            histogramMetric(bounds, new long[] {0, 0}, 0, 0, "loop", "world", "application", "b")),
                    family(
                            PASS,
                            MetricType.HISTOGRAM,
                            histogramMetric(bounds, new long[] {10, 10}, 10, 0.5, "loop", "world", "application", "a"),
                            histogramMetric(bounds, new long[] {0, 10}, 10, 1.5, "loop", "world", "application", "b")));

            final Map<Map<String, String>, Double> actual =
                    new MetricWindow(scrapes).histogram(PASS, "loop").quantile(0.75);

            assertThat(actual).containsOnlyKeys(Map.of("loop", "world"));
            assertThat(actual.get(Map.of("loop", "world"))).isCloseTo(0.15, within(1e-12));
        }

        @Test
        void histogramQuantile_shouldSkipSeries_whenTheFamilyIsASummary() {
            final List<Scrape> scrapes = scrapes(
                    family(PASS, MetricType.SUMMARY, summaryMetric(1, 0.1)),
                    family(PASS, MetricType.SUMMARY, summaryMetric(5, 0.6)));

            final Map<Map<String, String>, Double> actual =
                    new MetricWindow(scrapes).histogram(PASS).quantile(0.99);

            assertThat(actual).isEmpty();
        }
    }

    @Nested
    class Increase {
        private static final String FAILURES = "l2nx_engine_failures_total";

        @ParameterizedTest(name = "{0}")
        @MethodSource("counterValues")
        void increase_shouldTakePromqlIncrease_whenTheCounterMoves(String name, Double[] values, double expected) {
            final List<Scrape> scrapes = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                scrapes.add(values[i] == null ? scrape() : scrape(counter(FAILURES, values[i])));
            }

            final double actual = new MetricWindow(scrapes).increase(FAILURES).get(Map.of());

            assertThat(actual).isEqualTo(expected);
        }

        static Stream<Arguments> counterValues() {
            return Stream.of(
                    Arguments.of("delta between the bounds", new Double[] {10.0, 25.0}, 15.0),
                    Arguments.of("reset takes the end value", new Double[] {100.0, 7.0}, 7.0),
                    Arguments.of("reset between samples", new Double[] {10.0, 25.0, 5.0, 8.0}, 23.0),
                    Arguments.of("series created inside the window", new Double[] {null, 4.0, 9.0}, 9.0),
                    Arguments.of("gap keeps the last value", new Double[] {10_000.0, null, 10_005.0}, 5.0),
                    Arguments.of("decrease after a gap is a reset", new Double[] {3.0, null, 2.0}, 2.0));
        }

        @Test
        void increase_shouldSumTheSeriesOfAGroup_whenOtherLabelsDiffer() {
            final List<Scrape> scrapes = scrapes(
                    family(
                            FAILURES,
                            MetricType.COUNTER,
                            counterMetric(1, "loop", "world", "phase", "inbox"),
                            counterMetric(0, "loop", "world", "phase", "timers"),
                            counterMetric(5, "loop", "arena", "phase", "inbox")),
                    family(
                            FAILURES,
                            MetricType.COUNTER,
                            counterMetric(4, "loop", "world", "phase", "inbox"),
                            counterMetric(2, "loop", "world", "phase", "timers"),
                            counterMetric(5, "loop", "arena", "phase", "inbox")));

            final Map<Map<String, String>, Double> actual = new MetricWindow(scrapes).increase(FAILURES, "loop");

            assertThat(actual).isEqualTo(Map.of(Map.of("loop", "world"), 5.0, Map.of("loop", "arena"), 0.0));
        }
    }

    @Nested
    class Mean {
        @Test
        void mean_shouldDivideTheSumDeltaByTheCountDelta_whenTheWindowHasObservations() {
            final List<Scrape> scrapes = scrapes(
                    family(PASS, MetricType.SUMMARY, summaryMetric(10, 1.0)),
                    family(PASS, MetricType.SUMMARY, summaryMetric(30, 5.0)));

            final double actual =
                    new MetricWindow(scrapes).histogram(PASS).mean().get(Map.of());

            assertThat(actual).isCloseTo(0.2, within(1e-12));
        }

        @Test
        void mean_shouldBeNaN_whenTheWindowHasNoObservations() {
            final List<Scrape> scrapes = scrapes(
                    family(PASS, MetricType.SUMMARY, summaryMetric(10, 1.0)),
                    family(PASS, MetricType.SUMMARY, summaryMetric(10, 1.0)));

            final double actual =
                    new MetricWindow(scrapes).histogram(PASS).mean().get(Map.of());

            assertThat(actual).isNaN();
        }
    }

    @Nested
    class WindowMax {
        private static final double[] BOUNDS = {0.1, 0.5, 1.0, 10.0};

        @ParameterizedTest(name = "first max sample {0}")
        @MethodSource("maxSamples")
        void windowMax_shouldTakeTheLargestSampleBoundByTheFilledBuckets(double firstSample, double expected) {
            final List<Scrape> scrapes = List.of(
                    pass(new long[] {5, 5, 5, 6}, 6, firstSample),
                    pass(new long[] {8, 9, 9, 10}, 10, 0.3),
                    pass(new long[] {12, 15, 15, 16}, 16, 0.25));

            final double actual =
                    new MetricWindow(scrapes).histogram(PASS).max().get(Map.of());

            assertThat(actual).isEqualTo(expected);
        }

        static Stream<Arguments> maxSamples() {
            return Stream.of(
                    Arguments.of(0.2, 0.3),
                    // A warm-up observation of 5 s still held by the decaying maximum: no bucket above 0.5 filled.
                    Arguments.of(5.0, 0.5));
        }

        @Test
        void windowMax_shouldBeNaN_whenTheWindowHasNoObservations() {
            final List<Scrape> scrapes =
                    List.of(pass(new long[] {1, 1, 1, 1}, 1, 0.05), pass(new long[] {1, 1, 1, 1}, 1, 0.05));

            final double actual =
                    new MetricWindow(scrapes).histogram(PASS).max().get(Map.of());

            assertThat(actual).isNaN();
        }

        @Test
        void max_shouldBeNull_whenTheMaxGaugeIsMissing() {
            final List<Scrape> scrapes = scrapes(
                    family(PASS, MetricType.HISTOGRAM, histogramMetric(BOUNDS, new long[] {1, 1, 1, 1}, 1, 0.05)),
                    family(PASS, MetricType.HISTOGRAM, histogramMetric(BOUNDS, new long[] {2, 2, 2, 2}, 2, 0.1)));

            final Map<Map<String, String>, Double> actual =
                    new MetricWindow(scrapes).histogram(PASS).max();

            assertThat(actual).isNull();
        }

        private static Scrape pass(long[] cumulative, long count, double max) {
            return scrape(
                    family(PASS, MetricType.HISTOGRAM, histogramMetric(BOUNDS, cumulative, count, 1.0)),
                    gauge(PASS + "_max", max));
        }
    }

    @Nested
    class Samples {
        private static final String DEPTH = "l2nx_engine_inbox_depth";

        @ParameterizedTest(name = "{0}")
        @MethodSource("statistics")
        void sample_shouldTakeTheStatisticOverTheScrapes_whenTheGaugeIsSampled(
                String name, Function<MetricWindow, Map<Map<String, String>, Double>> statistic, double expected) {
            final MetricFamily[] depths = IntStream.rangeClosed(1, 100)
                    .mapToObj(depth -> gauge(DEPTH, 101 - depth, "loop", "world"))
                    .toArray(MetricFamily[]::new);

            final double actual =
                    statistic.apply(new MetricWindow(scrapes(depths))).get(Map.of("loop", "world"));

            assertThat(actual).isCloseTo(expected, within(1e-9));
        }

        static Stream<Arguments> statistics() {
            return Stream.of(
                    Arguments.of("max", statistic(window -> window.sampleMax(DEPTH, "loop")), 100.0),
                    Arguments.of("mean", statistic(window -> window.sampleMean(DEPTH, "loop")), 50.5),
                    Arguments.of(
                            "p99 as quantile_over_time",
                            statistic(window -> window.sampleQuantile(0.99, DEPTH, "loop")),
                            99.01));
        }
    }

    @Nested
    class MissingFamily {
        @ParameterizedTest(name = "{0}")
        @MethodSource("readings")
        void reading_shouldBeNull_whenNoScrapeHoldsTheFamily(String name, Function<MetricWindow, Object> reading) {
            final MetricWindow window = new MetricWindow(scrapes(counter("other_total", 1), counter("other_total", 2)));

            assertThat(reading.apply(window)).isNull();
        }

        static Stream<Arguments> readings() {
            return Stream.of(
                    Arguments.of("histogram", reading(window -> window.histogram(PASS))),
                    Arguments.of("increase", reading(window -> window.increase(PASS))),
                    Arguments.of("sampleMax", reading(window -> window.sampleMax(PASS))),
                    Arguments.of("sampleMean", reading(window -> window.sampleMean(PASS))),
                    Arguments.of("sampleQuantile", reading(window -> window.sampleQuantile(0.99, PASS))));
        }

        private static Function<MetricWindow, Object> reading(Function<MetricWindow, Object> reading) {
            return reading;
        }
    }

    private static Function<MetricWindow, Map<Map<String, String>, Double>> statistic(
            Function<MetricWindow, Map<Map<String, String>, Double>> statistic) {
        return statistic;
    }

    private static NavigableMap<Double, Double> buckets(double... boundsAndCounts) {
        final NavigableMap<Double, Double> buckets = new TreeMap<>();
        for (int i = 0; i < boundsAndCounts.length; i += 2) {
            buckets.put(boundsAndCounts[i], boundsAndCounts[i + 1]);
        }
        return buckets;
    }

    @Builder
    private record KnownData(MetricWindow window, Histogram exact) {
        private static final int WARM_UP = 5_000;
        private static final int STEADY = 200_000;

        static KnownData record(double[] bounds, DoubleSupplier steady) {
            final long[] cumulative = new long[bounds.length];
            final long[] count = {0};
            final double[] sum = {0};
            final Histogram exact = new Histogram(3);
            final List<Scrape> scrapes = new ArrayList<>();
            for (int i = 0; i < WARM_UP; i++) {
                observe(bounds, cumulative, count, sum, HistogramQuantile.WARM_UP_VALUE);
            }
            scrapes.add(snapshot(bounds, cumulative, count[0], sum[0]));
            for (int i = 0; i < STEADY; i++) {
                final double value = steady.getAsDouble();
                observe(bounds, cumulative, count, sum, value);
                exact.recordValue(Math.round(value * 1_000_000));
                if (i == STEADY / 2) {
                    scrapes.add(snapshot(bounds, cumulative, count[0], sum[0]));
                }
            }
            scrapes.add(snapshot(bounds, cumulative, count[0], sum[0]));
            return KnownData.builder()
                    .window(new MetricWindow(scrapes))
                    .exact(exact)
                    .build();
        }

        double exact(double q) {
            return exact.getValueAtPercentile(q * 100) / 1_000_000.0;
        }

        private static void observe(double[] bounds, long[] cumulative, long[] count, double[] sum, double value) {
            for (int b = 0; b < bounds.length; b++) {
                if (value <= bounds[b]) {
                    cumulative[b]++;
                }
            }
            count[0]++;
            sum[0] += value;
        }

        private static Scrape snapshot(double[] bounds, long[] cumulative, long count, double sum) {
            return scrape(family(
                    PASS,
                    MetricType.HISTOGRAM,
                    histogramMetric(bounds, cumulative.clone(), count, sum, "loop", "world")));
        }
    }
}
