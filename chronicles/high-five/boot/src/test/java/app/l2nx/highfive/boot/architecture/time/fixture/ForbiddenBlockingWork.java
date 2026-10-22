/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time.fixture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

public final class ForbiddenBlockingWork {
    private ForbiddenBlockingWork() {}

    public static final class Sleep {
        void pause() throws InterruptedException {
            Thread.sleep(1000);
        }
    }

    public static final class MonitorWait {
        synchronized void pause() throws InterruptedException {
            wait(1000);
        }
    }

    public static final class MonitorWaitForever {
        void pause(Object monitor) throws InterruptedException {
            synchronized (monitor) {
                monitor.wait();
            }
        }
    }

    public static final class ThreadJoin {
        void pause(Thread thread) throws InterruptedException {
            thread.join();
        }
    }

    public static final class Park {
        void pause() {
            LockSupport.parkNanos(1000);
        }
    }

    public static final class LatchAwait {
        boolean pause(CountDownLatch latch) throws InterruptedException {
            return latch.await(1, TimeUnit.SECONDS);
        }
    }

    public static final class SemaphoreAcquire {
        void pause(Semaphore semaphore) {
            semaphore.acquireUninterruptibly();
        }
    }

    public static final class FutureGet {
        Object pause(Future<?> future) throws InterruptedException, ExecutionException {
            return future.get();
        }
    }

    public static final class ScheduledFutureGet {
        Object pause(ScheduledFuture<?> future) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(1, TimeUnit.SECONDS);
        }
    }

    public static final class CompletableFutureJoin {
        Object pause(CompletableFuture<?> future) {
            return future.join();
        }
    }
}
