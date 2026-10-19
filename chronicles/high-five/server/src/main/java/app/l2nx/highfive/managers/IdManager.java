/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.config.IdManagerConfig;
import app.l2nx.highfive.util.PrimeCapacityAllocator;
import java.util.BitSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages unique IDs for objects.
 * @author Mobius
 */
@Slf4j
public class IdManager {

    private static final int TOTAL_ID_COUNT = IdManagerConfig.LAST_OBJECT_ID - IdManagerConfig.FIRST_OBJECT_ID;

    private BitSet freeIds;
    private int freeIdCount;
    private int nextFreeId;
    private final Lock lock = new ReentrantLock();

    public IdManager() {
        // Cleanup database.
        DatabaseIdManager.cleanDatabase();
        DatabaseIdManager.cleanCharacterStatus();
        DatabaseIdManager.cleanTimestamps();

        // Initialize BitSet with initial capacity or next prime.
        try {
            freeIds = new BitSet(PrimeCapacityAllocator.nextCapacity(IdManagerConfig.INITIAL_CAPACITY));
            freeIds.clear();
            freeIdCount = TOTAL_ID_COUNT;

            // Register used ids.
            for (int usedObjectId : DatabaseIdManager.getUsedIds()) {
                final int objectId = usedObjectId - IdManagerConfig.FIRST_OBJECT_ID;
                if (objectId < 0) {
                    continue;
                }

                freeIds.set(objectId);
                freeIdCount--;
            }

            nextFreeId = freeIds.nextClearBit(0);
        } catch (Exception e) {
            log.error("Could not be initialized properly: {}", e.getMessage());
        }

        log.info("{} ids available.", freeIds.size());
    }

    /**
     * Increases the capacity of the BitSet to accommodate more IDs, based on the current utilization and threshold.<br>
     * Uses the next prime number for optimal BitSet size, up to the maximum allowed ID range.
     */
    private void increaseBitSetCapacity() {
        final int currentSize = freeIds.size();
        final int newSize = Math.min(
                PrimeCapacityAllocator.nextCapacity((int) (currentSize * IdManagerConfig.RESIZE_MULTIPLIER)),
                TOTAL_ID_COUNT);

        // Only resize if the new size is larger than the current size.
        if (newSize > currentSize) {
            final BitSet newBitSet = new BitSet(newSize);
            newBitSet.or(freeIds);
            freeIds = newBitSet;
            log.info("Increased BitSet capacity to {}", newSize);
        }
    }

    /**
     * Allocates and returns the next available unique ID.<br>
     * If the BitSet reaches the defined utilization threshold, it is resized to maintain available capacity.
     * @return the next unique ID
     * @throws NullPointerException if there are no valid IDs remaining in the configured range
     */
    public int getNextId() {
        lock.lock();
        try {
            final int newId = nextFreeId;
            freeIds.set(newId);
            freeIdCount--;

            // Check utilization and increase capacity if needed.
            final double utilization = (double) (TOTAL_ID_COUNT - freeIdCount) / freeIds.size();
            if (utilization >= IdManagerConfig.RESIZE_THRESHOLD) {
                increaseBitSetCapacity();
                nextFreeId = freeIds.nextClearBit(0); // Reset to the first available ID in the resized BitSet.
            }

            int nextFree = freeIds.nextClearBit(newId);
            if (nextFree < 0) {
                nextFree = freeIds.nextClearBit(0);
            }

            if (nextFree < 0) {
                if (freeIds.size() < TOTAL_ID_COUNT) {
                    increaseBitSetCapacity();
                    nextFreeId = freeIds.nextClearBit(0); // Reset again if resizing occurred.
                } else {
                    throw new NullPointerException("IdManager: Ran out of valid ids.");
                }
            }

            nextFreeId = nextFree;

            return newId + IdManagerConfig.FIRST_OBJECT_ID;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases a previously allocated ID, making it available for future allocations.
     * @param objectId the ID to release
     */
    public void releaseId(int objectId) {
        lock.lock();
        try {
            if ((objectId - IdManagerConfig.FIRST_OBJECT_ID) > -1) {
                freeIds.clear(objectId - IdManagerConfig.FIRST_OBJECT_ID);
                freeIdCount++;
            } else {
                log.warn("Release objectID {} failed (< {})", objectId, IdManagerConfig.FIRST_OBJECT_ID);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the current count of available IDs within the range.
     * @return the number of available IDs
     */
    public int getAvailableIdCount() {
        lock.lock();
        try {
            return freeIdCount;
        } finally {
            lock.unlock();
        }
    }

    public static IdManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final IdManager INSTANCE = new IdManager();
    }
}
