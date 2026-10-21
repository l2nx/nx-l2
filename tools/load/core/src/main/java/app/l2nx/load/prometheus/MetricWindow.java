/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import io.prometheus.client.Metrics.Bucket;
import io.prometheus.client.Metrics.LabelPair;
import io.prometheus.client.Metrics.Metric;
import io.prometheus.client.Metrics.MetricFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;

/**
 * Server meter values over one window: the first and last scrape bound it, those between are the per-second samples.
 * Methods aggregate like PromQL {@code sum by (by...)}, return values by group, and return {@code null} when no scrape
 * holds the family.
 *
 * <p>Cumulative values use {@code increase} without extrapolation: a lower value is a reset and counts in full, a
 * missing series in a scrape is a gap, and a series first seen after the window start counts from zero.
 */
public final class MetricWindow {
    private static final String MAX_SUFFIX = "_max";

    private final List<Scrape> scrapes;

    public MetricWindow(List<Scrape> scrapes) {
        if (scrapes.size() < 2) {
            throw new IllegalArgumentException("A window needs its first and last scrape, got " + scrapes.size());
        }
        this.scrapes = List.copyOf(scrapes);
    }

    public HistogramWindow histogram(String family, String... by) {
        final Map<Map<String, String>, HistogramDelta> deltas = histograms(family, by);
        if (deltas == null) {
            return null;
        }
        return new HistogramWindow(deltas, samples(family + MAX_SUFFIX, by, Math::max));
    }

    public Map<Map<String, String>, Double> increase(String family, String... by) {
        final Map<Map<String, String>, Metric[]> series = series(family);
        if (series == null) {
            return null;
        }
        final Map<Map<String, String>, Double> increases = new LinkedHashMap<>();
        series.forEach((labels, samples) ->
                increases.merge(group(labels, by), increase(values(samples, MetricWindow::scalar)), Double::sum));
        return increases;
    }

    public Map<Map<String, String>, Double> sampleMax(String family, String... by) {
        return fromSamples(
                family,
                by,
                values -> values.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
    }

    public Map<Map<String, String>, Double> sampleMean(String family, String... by) {
        return fromSamples(
                family,
                by,
                values -> values.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElseThrow());
    }

    public Map<Map<String, String>, Double> sampleQuantile(double q, String family, String... by) {
        return fromSamples(family, by, values -> sampleQuantile(q, values));
    }

    /** PromQL's {@code histogram_quantile} over cumulative counts by upper bound. */
    static double bucketQuantile(double q, NavigableMap<Double, Double> cumulative) {
        if (Double.isNaN(q)) {
            return Double.NaN;
        }
        if (q < 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (q > 1) {
            return Double.POSITIVE_INFINITY;
        }
        if (cumulative.size() < 2 || cumulative.lastKey() != Double.POSITIVE_INFINITY) {
            return Double.NaN;
        }
        final int size = cumulative.size();
        final double[] bounds = new double[size];
        final double[] counts = monotonicCounts(cumulative, bounds);
        final double observations = counts[size - 1];
        if (observations == 0 || Double.isNaN(observations)) {
            return Double.NaN;
        }
        double rank = q * observations;
        int bucket = 0;
        while (bucket < size - 1 && counts[bucket] < rank) {
            bucket++;
        }
        if (bucket == size - 1) {
            return bounds[size - 2];
        }
        if (bucket == 0 && bounds[0] <= 0) {
            return bounds[0];
        }
        double bucketStart = 0;
        double count = counts[bucket];
        if (bucket > 0) {
            bucketStart = bounds[bucket - 1];
            count -= counts[bucket - 1];
            rank -= counts[bucket - 1];
        }
        return bucketStart + (bounds[bucket] - bucketStart) * (rank / count);
    }

    /** PromQL's {@code quantile_over_time}: linear interpolation between the closest ranks. */
    static double sampleQuantile(double q, List<Double> values) {
        if (values.isEmpty() || Double.isNaN(q)) {
            return Double.NaN;
        }
        if (q < 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (q > 1) {
            return Double.POSITIVE_INFINITY;
        }
        final double[] sorted =
                values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        final double rank = q * (sorted.length - 1);
        final int lower = (int) Math.floor(rank);
        final int upper = Math.min(sorted.length - 1, lower + 1);
        final double weight = rank - lower;
        return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }

    /** Increase of a cumulative value over the scrapes, {@code null} where the series or the value is absent. */
    static double increase(Double[] values) {
        double total = 0;
        Double previous = null;
        for (int i = 0; i < values.length; i++) {
            final Double value = values[i];
            if (value == null) {
                continue;
            }
            if (previous == null) {
                if (i > 0) {
                    total += value;
                }
            } else {
                total += value < previous ? value : value - previous;
            }
            previous = value;
        }
        return total;
    }

    // PromQL forces the cumulative counts monotonic before it ranks them.
    private static double[] monotonicCounts(NavigableMap<Double, Double> cumulative, double[] bounds) {
        final double[] counts = new double[cumulative.size()];
        int i = 0;
        double max = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Double, Double> entry : cumulative.entrySet()) {
            bounds[i] = entry.getKey();
            max = Math.max(max, entry.getValue());
            counts[i++] = max;
        }
        return counts;
    }

    // The smallest upper bound that holds every observation of the window.
    private static double coveringBound(NavigableMap<Double, Double> cumulative) {
        final double[] bounds = new double[cumulative.size()];
        final double[] counts = monotonicCounts(cumulative, bounds);
        final double observations = counts[counts.length - 1];
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] >= observations) {
                return bounds[i];
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private Map<Map<String, String>, Double> fromSamples(
            String family, String[] by, Function<List<Double>, Double> value) {
        final Map<Map<String, String>, List<Double>> samples = samples(family, by, Double::sum);
        if (samples == null) {
            return null;
        }
        final Map<Map<String, String>, Double> values = new LinkedHashMap<>();
        samples.forEach((group, groupSamples) -> values.put(group, value.apply(groupSamples)));
        return values;
    }

    /** Per group, one value per scrape that holds the group: its series combined by {@code aggregate}. */
    private Map<Map<String, String>, List<Double>> samples(String family, String[] by, DoubleBinaryOperator aggregate) {
        boolean found = false;
        final Map<Map<String, String>, List<Double>> samples = new LinkedHashMap<>();
        for (Scrape scrape : scrapes) {
            final MetricFamily metrics = scrape.families().get(family);
            if (metrics == null) {
                continue;
            }
            found = true;
            final Map<Map<String, String>, Double> scrapeValues = new LinkedHashMap<>();
            for (Metric metric : metrics.getMetricList()) {
                final Double value = scalar(metric);
                if (value != null) {
                    scrapeValues.merge(group(labels(metric), by), value, aggregate::applyAsDouble);
                }
            }
            scrapeValues.forEach((group, value) ->
                    samples.computeIfAbsent(group, ignored -> new ArrayList<>()).add(value));
        }
        return found ? samples : null;
    }

    private Map<Map<String, String>, HistogramDelta> histograms(String family, String[] by) {
        final Map<Map<String, String>, Metric[]> series = series(family);
        if (series == null) {
            return null;
        }
        final Map<Map<String, String>, HistogramDelta> deltas = new LinkedHashMap<>();
        series.forEach((labels, samples) -> {
            final HistogramDelta delta = deltas.computeIfAbsent(group(labels, by), ignored -> new HistogramDelta());
            delta.count += increase(values(samples, MetricWindow::sampleCount));
            delta.sum += increase(values(samples, MetricWindow::sampleSum));
            addBuckets(delta, samples);
        });
        return deltas;
    }

    private static void addBuckets(HistogramDelta delta, Metric[] samples) {
        @SuppressWarnings("unchecked")
        final NavigableMap<Double, Double>[] buckets = new NavigableMap[samples.length];
        final TreeSet<Double> bounds = new TreeSet<>();
        for (int i = 0; i < samples.length; i++) {
            buckets[i] = buckets(samples[i]);
            if (buckets[i] != null) {
                bounds.addAll(buckets[i].keySet());
            }
        }
        if (bounds.isEmpty()) {
            return;
        }
        delta.classic = true;
        for (Double bound : bounds) {
            final Double[] values = new Double[samples.length];
            for (int i = 0; i < samples.length; i++) {
                values[i] = buckets[i] == null ? null : buckets[i].get(bound);
            }
            delta.buckets.merge(bound, increase(values), Double::sum);
        }
    }

    /** Series of the family by their labels, each with its metric per scrape ({@code null} where absent). */
    private Map<Map<String, String>, Metric[]> series(String family) {
        boolean found = false;
        final Map<Map<String, String>, Metric[]> series = new LinkedHashMap<>();
        for (int i = 0; i < scrapes.size(); i++) {
            final MetricFamily metrics = scrapes.get(i).families().get(family);
            if (metrics == null) {
                continue;
            }
            found = true;
            for (Metric metric : metrics.getMetricList()) {
                series.computeIfAbsent(labels(metric), ignored -> new Metric[scrapes.size()])[i] = metric;
            }
        }
        return found ? series : null;
    }

    private static Double[] values(Metric[] samples, Function<Metric, Double> value) {
        final Double[] values = new Double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            values[i] = samples[i] == null ? null : value.apply(samples[i]);
        }
        return values;
    }

    private static Map<String, String> labels(Metric metric) {
        final SortedMap<String, String> labels = new TreeMap<>();
        for (LabelPair label : metric.getLabelList()) {
            labels.put(label.getName(), label.getValue());
        }
        return Collections.unmodifiableSortedMap(labels);
    }

    private static Map<String, String> group(Map<String, String> labels, String[] by) {
        final SortedMap<String, String> group = new TreeMap<>();
        for (String name : by) {
            final String value = labels.get(name);
            if (value != null) {
                group.put(name, value);
            }
        }
        return Collections.unmodifiableSortedMap(group);
    }

    private static Double scalar(Metric metric) {
        if (metric.hasCounter()) {
            return metric.getCounter().getValue();
        }
        if (metric.hasGauge()) {
            return metric.getGauge().getValue();
        }
        if (metric.hasUntyped()) {
            return metric.getUntyped().getValue();
        }
        return null;
    }

    private static Double sampleCount(Metric metric) {
        if (metric.hasHistogram()) {
            final double countFloat = metric.getHistogram().getSampleCountFloat();
            return countFloat > 0 ? countFloat : metric.getHistogram().getSampleCount();
        }
        if (metric.hasSummary()) {
            return (double) metric.getSummary().getSampleCount();
        }
        return null;
    }

    private static Double sampleSum(Metric metric) {
        if (metric.hasHistogram()) {
            return metric.getHistogram().getSampleSum();
        }
        if (metric.hasSummary()) {
            return metric.getSummary().getSampleSum();
        }
        return null;
    }

    /** Cumulative counts of the classic buckets, with the {@code +Inf} bucket the exposition may leave implicit. */
    private static NavigableMap<Double, Double> buckets(Metric metric) {
        if (metric == null || !metric.hasHistogram() || metric.getHistogram().getBucketCount() == 0) {
            return null;
        }
        final NavigableMap<Double, Double> buckets = new TreeMap<>();
        for (Bucket bucket : metric.getHistogram().getBucketList()) {
            final double countFloat = bucket.getCumulativeCountFloat();
            buckets.put(bucket.getUpperBound(), countFloat > 0 ? countFloat : bucket.getCumulativeCount());
        }
        buckets.putIfAbsent(Double.POSITIVE_INFINITY, sampleCount(metric));
        return buckets;
    }

    private static final class HistogramDelta {
        private final NavigableMap<Double, Double> buckets = new TreeMap<>();
        private double count;
        private double sum;
        private boolean classic;
    }

    /** Values of one histogram or summary over the window, by group. */
    public static final class HistogramWindow {
        private final Map<Map<String, String>, HistogramDelta> deltas;
        private final Map<Map<String, String>, List<Double>> maxSamples;

        private HistogramWindow(
                Map<Map<String, String>, HistogramDelta> deltas, Map<Map<String, String>, List<Double>> maxSamples) {
            this.deltas = deltas;
            this.maxSamples = maxSamples;
        }

        /** Summaries (no classic buckets) are skipped; a group without observations is {@code NaN}. */
        public Map<Map<String, String>, Double> quantile(double q) {
            final Map<Map<String, String>, Double> quantiles = new LinkedHashMap<>();
            deltas.forEach((group, delta) -> {
                if (delta.classic) {
                    quantiles.put(group, bucketQuantile(q, delta.buckets));
                }
            });
            return quantiles;
        }

        public Map<Map<String, String>, Double> count() {
            return values(delta -> delta.count);
        }

        public Map<Map<String, String>, Double> mean() {
            return values(delta -> delta.sum / delta.count);
        }

        /**
         * From the {@code <family>_max} gauge, capped at the highest bucket the window filled: the gauge is a decaying
         * maximum that still holds pre-window observations. {@code NaN} without observations; {@code null} without the
         * gauge.
         */
        public Map<Map<String, String>, Double> max() {
            if (maxSamples == null) {
                return null;
            }
            final Map<Map<String, String>, Double> maxima = new LinkedHashMap<>();
            maxSamples.forEach((group, values) -> {
                double max =
                        values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                final HistogramDelta delta = deltas.get(group);
                if (delta != null) {
                    if (delta.count == 0) {
                        max = Double.NaN;
                    } else if (delta.classic) {
                        max = Math.min(max, coveringBound(delta.buckets));
                    }
                }
                maxima.put(group, max);
            });
            return maxima;
        }

        private Map<Map<String, String>, Double> values(Function<HistogramDelta, Double> value) {
            final Map<Map<String, String>, Double> values = new LinkedHashMap<>();
            deltas.forEach((group, delta) -> values.put(group, value.apply(delta)));
            return values;
        }
    }
}
