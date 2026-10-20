/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

// Alone in the JVM: it provokes a real leak and drains the recorder, so no other class may run beside it.
@Isolated
class NettyLeakCheckTest {
    @Test
    void recorder_shouldReportLeak_whenBufferIsCollectedUnreleased() {
        LeakRecorder.drain();
        leakOneBuffer();

        assertThat(NettyLeakCheck.collectLeaks()).isNotEmpty();
    }

    @Test
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
