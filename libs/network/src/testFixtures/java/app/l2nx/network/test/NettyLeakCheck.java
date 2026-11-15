/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.List;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Checks the test JVM for leaked buffers once, after its last test: the forced full GC the check needs stops every
 * test running beside it, so a check per test container stalled parallel tests for seconds.
 */
public final class NettyLeakCheck implements BeforeAllCallback {
    private static final int TRIGGER_ALLOCATIONS = 64;
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(NettyLeakCheck.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        // The root store closes its values when the engine finishes, after every test of the JVM.
        context.getRoot()
                .getStore(NAMESPACE)
                .computeIfAbsent(FinalCheck.class, key -> new FinalCheck(), FinalCheck.class);
    }

    /**
     * A collected buffer is reported only by a later tracked allocation, so the check allocates after the GC.
     */
    public static List<String> collectLeaks() {
        return collectLeaks(System::gc);
    }

    // GC only while a tracked buffer is unreleased: otherwise nothing can leak.
    static List<String> collectLeaks(Runnable gc) {
        if (LeakRecorder.openTrackers() == 0) {
            return LeakRecorder.drain();
        }
        for (int round = 0; round < 3; round++) {
            gc.run();
            for (int i = 0; i < TRIGGER_ALLOCATIONS; i++) {
                PooledByteBufAllocator.DEFAULT.buffer(1).release();
                UnpooledByteBufAllocator.DEFAULT.buffer(1).release();
            }
        }
        return LeakRecorder.drain();
    }

    private static final class FinalCheck implements AutoCloseable {
        @Override
        public void close() {
            final List<String> leaks = collectLeaks();
            if (!leaks.isEmpty()) {
                throw new AssertionError("Netty buffer leak in this test JVM:\n" + String.join("\n---\n", leaks));
            }
        }
    }
}
