/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Allocates the 32-bit object ids the protocol carries. A released handle is reused only after the quarantine, so a
 * client still holding the id of a despawned object cannot address a new one with it.
 */
@Slf4j
public final class WireHandleAllocator {
    public static final int FIRST_HANDLE = 0x10000000;
    public static final int LAST_HANDLE = 0x7FFFFFFF;

    private final Clock clock;
    private final long quarantineMillis;
    private final ReentrantLock lock = new ReentrantLock();
    // Indexed by handle - FIRST_HANDLE; grows only up to the highest handle ever issued.
    private final BitSet allocated = new BitSet();
    private final ArrayDeque<Released> quarantine = new ArrayDeque<>();
    private int nextFresh = FIRST_HANDLE;
    private boolean freshExhausted;
    private int allocatedCount;

    public WireHandleAllocator(Clock clock, Duration quarantine) {
        this.clock = clock;
        this.quarantineMillis = quarantine.toMillis();
    }

    public int allocate() {
        lock.lock();
        try {
            final Released oldest = quarantine.peekFirst();
            final int handle;
            if (oldest != null && clock.millis() - oldest.releasedAtMillis() >= quarantineMillis) {
                quarantine.pollFirst();
                handle = oldest.handle();
            } else if (!freshExhausted) {
                handle = nextFresh;
                if (nextFresh == LAST_HANDLE) {
                    freshExhausted = true;
                } else {
                    nextFresh++;
                }
            } else {
                throw new IllegalStateException("No wire handle is free: " + allocatedCount + " in use, "
                        + quarantine.size() + " in quarantine.");
            }
            allocated.set(handle - FIRST_HANDLE);
            allocatedCount++;
            return handle;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releasing a handle that is not allocated is logged and ignored, so it cannot be handed out twice.
     */
    public void release(int handle) {
        lock.lock();
        try {
            if (handle < FIRST_HANDLE || !allocated.get(handle - FIRST_HANDLE)) {
                log.warn("Release of wire handle {} that is not allocated.", handle, new IllegalStateException());
                return;
            }
            allocated.clear(handle - FIRST_HANDLE);
            allocatedCount--;
            quarantine.addLast(new Released(handle, clock.millis()));
        } finally {
            lock.unlock();
        }
    }

    public int allocatedCount() {
        lock.lock();
        try {
            return allocatedCount;
        } finally {
            lock.unlock();
        }
    }

    @Builder(toBuilder = true)
    private record Released(int handle, long releasedAtMillis) {}
}
