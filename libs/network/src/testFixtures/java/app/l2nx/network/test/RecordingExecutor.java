/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.test;

import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An event loop that runs nothing until asked and tells {@code lazyExecute} from {@code execute}. Reports every thread
 * as its own, so a handler bound to it is added and writes inline. Single-threaded use only.
 */
public final class RecordingExecutor extends AbstractEventExecutor {
    private final List<Runnable> tasks = new ArrayList<>();
    private final AtomicInteger terminationListeners = new AtomicInteger();
    private final Promise<Void> termination = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE) {
        @Override
        public Promise<Void> addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
            terminationListeners.incrementAndGet();
            return super.addListener(listener);
        }
    };
    private int lazy;
    private int immediate;
    private boolean shutDown;
    private boolean rejecting;
    private Error executeFailure;

    @Override
    public void lazyExecute(Runnable task) {
        accept(task);
        lazy++;
    }

    @Override
    public void execute(Runnable task) {
        if (executeFailure != null) {
            throw executeFailure;
        }
        accept(task);
        immediate++;
    }

    public int lazy() {
        return lazy;
    }

    public int immediate() {
        return immediate;
    }

    public int pending() {
        return tasks.size();
    }

    public void runNext() {
        tasks.removeFirst().run();
    }

    public void runAll() {
        while (!tasks.isEmpty()) {
            runNext();
        }
    }

    /** Accepts tasks still, as a terminating event loop does after its last pass. */
    public void shutDown() {
        shutDown = true;
    }

    public void reject() {
        rejecting = true;
    }

    public void failExecuteWith(Error failure) {
        executeFailure = failure;
    }

    public void terminate() {
        termination.setSuccess(null);
    }

    public int terminationListeners() {
        return terminationListeners.get();
    }

    private void accept(Runnable task) {
        if (rejecting) {
            throw new RejectedExecutionException("The event loop rejects tasks");
        }
        tasks.add(task);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return true;
    }

    @Override
    public boolean isShuttingDown() {
        return shutDown;
    }

    @Override
    public boolean isShutdown() {
        return shutDown;
    }

    @Override
    public boolean isTerminated() {
        return termination.isDone();
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        shutDown = true;
        return termination;
    }

    @Override
    public Future<?> terminationFuture() {
        return termination;
    }

    @Override
    @Deprecated
    public void shutdown() {
        shutDown = true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return termination.isDone();
    }
}
