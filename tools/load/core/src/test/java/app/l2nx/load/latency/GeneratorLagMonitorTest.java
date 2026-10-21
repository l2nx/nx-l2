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
}
