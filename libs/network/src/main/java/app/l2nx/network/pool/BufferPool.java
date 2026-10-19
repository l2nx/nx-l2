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
package app.l2nx.network.pool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a pool of {@link ByteBuffer} objects for efficient reuse, reducing allocation overhead.<br>
 * Uses an {@link ArrayDeque} backed by a {@link ReentrantLock} for correct, thread-safe pool management without the estimate-drift issues of a lock-free approach.
 * @author Mobius
 */
public class BufferPool {
    private final ArrayDeque<ByteBuffer> buffers;
    private final ReentrantLock lock = new ReentrantLock();
    private final int bufferSize;
    private int maxSize;

    /**
     * Creates a buffer pool with the specified capacity and buffer size.
     * @param maxSize the maximum number of pooled buffers
     * @param bufferSize the capacity (in bytes) of each pooled buffer
     */
    public BufferPool(int maxSize, int bufferSize) {
        this.maxSize = maxSize;
        this.bufferSize = bufferSize;
        buffers = new ArrayDeque<>(maxSize);
    }

    /**
     * Pre-allocates buffers into the pool based on the given factor.<br>
     * The number pre-allocated is {@code min(maxSize, maxSize * factor)}.
     * @param factor the fraction of the pool to pre-fill (0 = none, 1.0 = full)
     */
    public void initialize(float factor) {
        final int amount = (int) Math.min(maxSize, maxSize * factor);
        lock.lock();
        try {
            for (int i = 0; i < amount; i++) {
                buffers.offer(ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN));
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves a pooled {@link ByteBuffer}, or {@code null} if the pool is empty.
     * @return a cleared buffer ready for use, or {@code null}
     */
    public ByteBuffer get() {
        lock.lock();
        try {
            return buffers.poll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a buffer to the pool if capacity allows.<br>
     * The buffer is cleared before being stored.
     * @param buffer the buffer to recycle
     * @return {@code true} if the buffer was accepted; {@code false} if the pool was full
     */
    public boolean recycle(ByteBuffer buffer) {
        lock.lock();
        try {
            if (buffers.size() < maxSize) {
                buffers.offer(buffer.clear());
                return true;
            }

            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Expands the pool's maximum capacity and optionally allocates additional buffers.<br>
     * If {@code factor > 0}, new buffers are allocated immediately and the max size is increased.<br>
     * Otherwise, the max size is simply doubled to allow future recycling.
     * @param factor allocation factor; if {@code > 0}, new buffers are created immediately
     * @param limit the pool only expands when its current max size does not exceed this value
     */
    public void expandCapacity(float factor, int limit) {
        lock.lock();
        try {
            if (maxSize > limit) {
                return;
            }

            if (factor > 0) {
                final int amount = (int) (maxSize * factor);
                for (int i = 0; i < amount; i++) {
                    buffers.offer(ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN));
                }

                maxSize += amount;
            } else {
                maxSize *= 2;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the configured maximum capacity of this pool.
     * @return the maximum number of buffers that may be held
     */
    public int getMaxSize() {
        lock.lock();
        try {
            return maxSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code true} when the pool holds at least as many buffers as its current max size.
     * @return {@code true} if the pool is at capacity
     */
    public boolean isFull() {
        lock.lock();
        try {
            return buffers.size() >= maxSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code true} when the pool holds no buffers.
     * @return {@code true} if the pool is empty
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return buffers.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return "Pool {maxSize=" + maxSize + ", bufferSize=" + bufferSize + ", currentSize=" + buffers.size() + '}';
        } finally {
            lock.unlock();
        }
    }
}
