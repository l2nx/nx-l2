/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time.fixture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.IntStream;

public final class ForbiddenScheduledWork {
    private ForbiddenScheduledWork() {}

    public static final class ThreadSubclass extends Thread {}

    public static final class NewThread {
        Thread start(Runnable work) {
            return new Thread(work);
        }
    }

    public static final class PlatformThread {
        Thread start(Runnable work) {
            return Thread.ofPlatform().start(work);
        }
    }

    public static final class VirtualThread {
        Thread start(Runnable work) {
            return Thread.ofVirtual().start(work);
        }
    }

    public static final class StartVirtualThread {
        Thread start(Runnable work) {
            return Thread.startVirtualThread(work);
        }
    }

    public static final class ExecutorsFactory {
        Object create() {
            return Executors.newSingleThreadExecutor();
        }
    }

    public static final class NewExecutor {
        Object create() {
            return new ScheduledThreadPoolExecutor(1);
        }
    }

    public static final class AsyncFuture {
        Object run(Runnable work) {
            return CompletableFuture.runAsync(work);
        }
    }

    public static final class AsyncContinuation {
        Object run(CompletableFuture<String> future) {
            return future.thenApplyAsync(String::length);
        }
    }

    public static final class ParallelStream {
        long count(List<String> values) {
            return values.parallelStream().count();
        }
    }

    public static final class ParallelIntStream {
        int sum() {
            return IntStream.range(0, 10).parallel().sum();
        }
    }

    public static final class ExecutorField {
        ExecutorService executor;
    }

    public static final class ScheduledExecutorField {
        ScheduledExecutorService executor;
    }
}
