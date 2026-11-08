/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.latency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeneratorLagMonitorTest {
    private final GeneratorLagMonitor monitor = new GeneratorLagMonitor();

    @Nested
    class Record {
        @Test
        void p99Millis_shouldReflectTheSlowTail() {
            for (int i = 0; i < 98; i++) {
                monitor.record(TimeUnit.MICROSECONDS.toNanos(100));
            }
            monitor.record(TimeUnit.MILLISECONDS.toNanos(20));
            monitor.record(TimeUnit.MILLISECONDS.toNanos(30));

            assertThat(monitor.p99Millis()).isCloseTo(20.0, within(0.05));
        }

        @Test
        void record_shouldCountNegativeLagAsNone() {
            monitor.record(-5_000);

            assertThat(monitor.histogram().getTotalCount()).isEqualTo(1);
            assertThat(monitor.p99Nanos()).isZero();
        }
    }

    @Nested
    class Levels {
        @Test
        void levelHistogram_shouldKeepEachLevelApart() {
            final GeneratorLagMonitor levelled = new GeneratorLagMonitor(2);
            for (int i = 0; i < 100; i++) {
                levelled.recordLevel(0, 1_000_000);
                levelled.recordLevel(1, 20_000_000);
            }

            assertThat(levelled.levelHistogram(0).getValueAtPercentile(99.0)).isBetween(999_000L, 1_001_000L);
            assertThat(levelled.levelHistogram(1).getValueAtPercentile(99.0)).isBetween(19_990_000L, 20_010_000L);
            assertThat(levelled.p99Nanos())
                    .as("the run-wide lag is recorded apart")
                    .isZero();
        }

        @Test
        void levelHistogram_shouldBeEmpty_whenNoTaskOfTheLevelRan() {
            final GeneratorLagMonitor levelled = new GeneratorLagMonitor(2);
            levelled.recordLevel(0, 1_000_000);

            assertThat(levelled.levelHistogram(1).getTotalCount()).isZero();
            assertThat(levelled.levelHistogram(0).getTotalCount())
                    .as("a read does not drain the level")
                    .isEqualTo(1);
            assertThat(levelled.levelHistogram(0).getTotalCount()).isEqualTo(1);
        }
    }
}
