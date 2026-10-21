/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.prometheus;

import io.prometheus.client.Metrics.Bucket;
import io.prometheus.client.Metrics.Counter;
import io.prometheus.client.Metrics.Gauge;
import io.prometheus.client.Metrics.Histogram;
import io.prometheus.client.Metrics.LabelPair;
import io.prometheus.client.Metrics.Metric;
import io.prometheus.client.Metrics.MetricFamily;
import io.prometheus.client.Metrics.MetricType;
import io.prometheus.client.Metrics.Summary;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MetricFamilies {
    private MetricFamilies() {}

    static Scrape scrape(MetricFamily... families) {
        return Scrape.builder()
                .families(Arrays.stream(families).collect(Collectors.toMap(MetricFamily::getName, Function.identity())))
                .build();
    }

    static List<Scrape> scrapes(MetricFamily... perScrape) {
        return Arrays.stream(perScrape).map(MetricFamilies::scrape).toList();
    }

    static MetricFamily family(String name, MetricType type, Metric... metrics) {
        return MetricFamily.newBuilder()
                .setName(name)
                .setType(type)
                .addAllMetric(List.of(metrics))
                .build();
    }

    static MetricFamily counter(String name, double value, String... labels) {
        return family(name, MetricType.COUNTER, counterMetric(value, labels));
    }

    static MetricFamily gauge(String name, double value, String... labels) {
        return family(name, MetricType.GAUGE, gaugeMetric(value, labels));
    }

    static Metric counterMetric(double value, String... labels) {
        return labelled(labels).setCounter(Counter.newBuilder().setValue(value)).build();
    }

    static Metric gaugeMetric(double value, String... labels) {
        return labelled(labels).setGauge(Gauge.newBuilder().setValue(value)).build();
    }

    static Metric summaryMetric(long count, double sum, String... labels) {
        return labelled(labels)
                .setSummary(Summary.newBuilder().setSampleCount(count).setSampleSum(sum))
                .build();
    }

    /** Finite buckets only: the {@code +Inf} bucket is left to the sample count, as the exposition allows. */
    static Metric histogramMetric(double[] bounds, long[] cumulative, long count, double sum, String... labels) {
        final Histogram.Builder histogram =
                Histogram.newBuilder().setSampleCount(count).setSampleSum(sum);
        for (int i = 0; i < bounds.length; i++) {
            histogram.addBucket(Bucket.newBuilder().setUpperBound(bounds[i]).setCumulativeCount(cumulative[i]));
        }
        return labelled(labels).setHistogram(histogram).build();
    }

    private static Metric.Builder labelled(String... labels) {
        final Metric.Builder metric = Metric.newBuilder();
        for (int i = 0; i < labels.length; i += 2) {
            metric.addLabel(LabelPair.newBuilder().setName(labels[i]).setValue(labels[i + 1]));
        }
        return metric;
    }
}
