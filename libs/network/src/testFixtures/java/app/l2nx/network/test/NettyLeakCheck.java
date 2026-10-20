/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.List;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class NettyLeakCheck implements AfterAllCallback {
    private static final int TRIGGER_ALLOCATIONS = 64;

    @Override
    public void afterAll(ExtensionContext context) {
        final List<String> leaks = collectLeaks();
        if (!leaks.isEmpty()) {
            throw new AssertionError(
                    "Netty buffer leak during " + context.getDisplayName() + ":\n" + String.join("\n---\n", leaks));
        }
    }

    /**
     * A collected buffer is reported only by a later tracked allocation, so the check allocates after the GC.
     */
    public static List<String> collectLeaks() {
        for (int round = 0; round < 3; round++) {
            System.gc();
            for (int i = 0; i < TRIGGER_ALLOCATIONS; i++) {
                PooledByteBufAllocator.DEFAULT.buffer(1).release();
                UnpooledByteBufAllocator.DEFAULT.buffer(1).release();
            }
        }
        return LeakRecorder.drain();
    }
}
