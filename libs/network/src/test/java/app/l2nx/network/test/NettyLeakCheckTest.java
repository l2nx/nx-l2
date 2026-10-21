/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

// Alone in the JVM: it provokes a real leak and drains the recorder, so no other class may run beside it.
// The provoked leak keeps its tracker open for good, so the tests that need no open tracker run before it.
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NettyLeakCheckTest {
    @Test
    @Order(1)
    void collectLeaks_shouldSkipGc_whenEveryTrackedBufferIsReleased() {
        LeakRecorder.drain();
        PooledByteBufAllocator.DEFAULT.buffer(16).release();
        final AtomicInteger gcs = new AtomicInteger();

        assertThat(NettyLeakCheck.collectLeaks(gcs::incrementAndGet)).isEmpty();
        assertThat(gcs).hasValue(0);
    }

    @Test
    @Order(1)
    void collectLeaks_shouldRunGc_whileTrackedBufferIsUnreleased() {
        LeakRecorder.drain();
        final ByteBuf held = PooledByteBufAllocator.DEFAULT.buffer(16);
        final AtomicInteger gcs = new AtomicInteger();
        try {
            assertThat(LeakRecorder.openTrackers()).isPositive();
            assertThat(NettyLeakCheck.collectLeaks(gcs::incrementAndGet)).isEmpty();
        } finally {
            held.release();
        }

        assertThat(gcs).hasPositiveValue();
        assertThat(LeakRecorder.openTrackers()).isZero();
    }

    @Test
    @Order(2)
    void recorder_shouldReportLeak_whenBufferIsCollectedUnreleased() {
        LeakRecorder.drain();
        leakOneBuffer();

        assertThat(NettyLeakCheck.collectLeaks()).isNotEmpty();
    }

    @Test
    @Order(1)
    void recorder_shouldReportNothing_whenBufferIsReleased() {
        LeakRecorder.drain();
        final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(16);
        buffer.release();

        assertThat(NettyLeakCheck.collectLeaks()).isEmpty();
    }

    private static void leakOneBuffer() {
        PooledByteBufAllocator.DEFAULT.buffer(16).writeInt(1);
    }
}
