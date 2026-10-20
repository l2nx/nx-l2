/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.packet;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.Client;
import app.l2nx.network.Connection;
import app.l2nx.network.config.ConnectionConfig;
import app.l2nx.network.config.NetworkSettings;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PacketExecutorTest {
    // Shutdown waits for the submitted packets, so the assertions see their runs.
    private static final long SHUTDOWN_WAIT_MS = 5_000;

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final AtomicInteger runs = new AtomicInteger();
    private final PacketExecutor<TestClient> executor =
            new PacketExecutor<>(new ConnectionConfig(new InetSocketAddress(0), NetworkSettings.defaults()));

    @AfterEach
    void shutdown() {
        executor.shutdown(SHUTDOWN_WAIT_MS);
    }

    @Nested
    class Execute {
        @Test
        void execute_shouldRecordRunTime_taggedWithPacketClassName() {
            executor.bindMetrics(registry, "test-packets");

            executor.execute(new TimedPacket(null));
            executor.shutdown(SHUTDOWN_WAIT_MS);

            final Timer actual = packetTimer();
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7.0);
        }

        @Test
        void execute_shouldShareOneTimer_forPacketsOfOneClass() {
            executor.bindMetrics(registry, "test-packets");

            executor.execute(new TimedPacket(null));
            executor.execute(new TimedPacket(null));
            executor.shutdown(SHUTDOWN_WAIT_MS);

            assertThat(registry.find(PacketExecutor.RUN_TIME).timers()).hasSize(1);
            assertThat(packetTimer().count()).isEqualTo(2);
        }

        @Test
        void execute_shouldRecordRunTime_taggedWithError_whenPacketThrows() {
            executor.bindMetrics(registry, "test-packets");

            executor.execute(new TimedPacket(new IllegalStateException("boom")));
            executor.shutdown(SHUTDOWN_WAIT_MS);

            final Timer actual = errorTimer("IllegalStateException");
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7.0);
            assertThat(packetTimer().count()).isZero();
        }

        @Test
        void execute_shouldTagBinaryName_whenExceptionClassIsAnonymous() {
            executor.bindMetrics(registry, "test-packets");
            final IllegalArgumentException failure = new IllegalArgumentException("boom") {};

            executor.execute(new TimedPacket(failure));
            executor.shutdown(SHUTDOWN_WAIT_MS);

            assertThat(errorTimer(failure.getClass().getName()).count()).isEqualTo(1);
        }

        @Test
        void execute_shouldRunUntimed_whenMetricsAreNotBound() {
            executor.execute(new TimedPacket(null));
            executor.shutdown(SHUTDOWN_WAIT_MS);

            assertThat(runs.get()).isEqualTo(1);
            assertThat(registry.find(PacketExecutor.RUN_TIME).timer()).isNull();
        }
    }

    private Timer packetTimer() {
        return registry.get(PacketExecutor.RUN_TIME)
                .tags(PacketExecutor.TYPE_TAG, TimedPacket.class.getName(), "outcome", "SUCCESS", "exception", "none")
                .timer();
    }

    private Timer errorTimer(String exception) {
        return registry.get(PacketExecutor.RUN_TIME)
                .tags(PacketExecutor.TYPE_TAG, TimedPacket.class.getName(), "outcome", "ERROR", "exception", exception)
                .timer();
    }

    private abstract static class TestClient extends Client<Connection<TestClient>> {
        TestClient(Connection<TestClient> connection) {
            super(connection);
        }
    }

    private final class TimedPacket extends ReadablePacket<TestClient> {
        private final RuntimeException failure;

        /** {@code failure} is null for a packet that runs normally. */
        TimedPacket(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public boolean read() {
            return true;
        }

        @Override
        public void run() {
            runs.incrementAndGet();
            clock.add(Duration.ofMillis(7));
            if (failure != null) {
                throw failure;
            }
        }
    }
}
