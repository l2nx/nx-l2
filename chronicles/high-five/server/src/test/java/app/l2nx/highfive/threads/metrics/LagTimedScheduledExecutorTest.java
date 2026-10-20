/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LagTimedScheduledExecutorTest {
    private static final long BLOCK_MS = 200;
    private static final long WAIT_S = 10;
    // A late start beyond this is a stalled test machine, not a measurement.
    private static final double SANE_LAG_MS = 5_000;

    private final Timer lag = Timer.builder("lag").register(new SimpleMeterRegistry());
    private final LagTimedScheduledExecutor executor = new LagTimedScheduledExecutor(
            1, Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy(), lag);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Nested
    class BeforeExecute {
        @Test
        void beforeExecute_shouldRecordStartDelay_whenTaskWaitsForBusyThread() throws InterruptedException {
            final CountDownLatch done = new CountDownLatch(1);
            executor.execute(LagTimedScheduledExecutorTest::block);
            executor.schedule(done::countDown, 10, TimeUnit.MILLISECONDS);

            assertThat(done.await(WAIT_S, TimeUnit.SECONDS)).isTrue();

            assertThat(lag.count()).isEqualTo(2);
            assertThat(lag.max(TimeUnit.MILLISECONDS)).isBetween((double) BLOCK_MS - 20, SANE_LAG_MS);
        }

        @Test
        void beforeExecute_shouldRecordEveryFixedRateRun() throws InterruptedException {
            final CountDownLatch runs = new CountDownLatch(3);
            final ScheduledFuture<?> future =
                    executor.scheduleAtFixedRate(runs::countDown, 0, 10, TimeUnit.MILLISECONDS);

            assertThat(runs.await(WAIT_S, TimeUnit.SECONDS)).isTrue();
            future.cancel(false);

            assertThat(lag.count()).isGreaterThanOrEqualTo(3);
            assertThat(lag.max(TimeUnit.MILLISECONDS)).isBetween(0.0, SANE_LAG_MS);
        }
    }

    private static void block() {
        try {
            Thread.sleep(BLOCK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
