/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership.fixture;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ForbiddenSingleWriter {
    private ForbiddenSingleWriter() {}

    @SingleWriter
    public static final class ConcurrentMapField {
        private final Map<Integer, Object> map = new ConcurrentHashMap<>();
    }

    @SingleWriter
    public static final class ConcurrentKeySetField {
        private final Set<Integer> ids = ConcurrentHashMap.newKeySet();
    }

    @SingleWriter
    public static final class AtomicField {
        private final AtomicInteger counter = new AtomicInteger();
    }

    @SingleWriter
    public static final class VolatileField {
        private volatile int value;
    }

    @SingleWriter
    public static final class LockField {
        private final ReentrantLock lock = new ReentrantLock();
    }

    @SingleWriter
    public static final class ExecutorField {
        private ExecutorService executor;
    }

    @SingleWriter
    public static final class CompletableFutureField {
        private CompletableFuture<Object> result;
    }

    @SingleWriter
    public static final class LockCall {
        public void run(Lock lock) {
            WorldPartition.checkOwner();
            lock.lock();
        }
    }

    @SingleWriter
    public static final class SynchronizedWrapper {
        private final Map<Integer, Object> map = Collections.synchronizedMap(new HashMap<>());
    }

    @SingleWriter
    public static final class PublicMutableField {
        public static int count;
    }

    @SingleWriter
    public static final class UncheckedPublicMethod {
        private int value;

        public void set(int next) {
            value = next;
        }
    }

    @SingleWriter
    public static final class CheckedAfterCall {
        private int value;

        public void set(int next) {
            Math.abs(next);
            WorldPartition.checkOwner();
            value = next;
        }
    }

    @SingleWriter
    public static final class CheckedAfterFieldWrite {
        private int value;

        public void set(int next) {
            value = next;
            WorldPartition.checkOwner();
        }
    }

    @SingleWriter
    public static final class UncheckedPublicStaticMethod {
        private static final List<Object> ITEMS = new ArrayList<>();

        public static List<Object> items() {
            return List.copyOf(ITEMS);
        }
    }
}
