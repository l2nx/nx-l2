/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import io.netty.util.concurrent.EventExecutor;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

/**
 * Event loops with outbound drains queued while open; closing wakes each once. Confined to the opening thread.
 */
@Slf4j
public final class OutboundHandoff {
    private static final Runnable WAKE_UP = () -> {};

    private EventExecutor[] executors = new EventExecutor[16];
    private int size;
    private boolean open;

    public void open() {
        open = true;
    }

    public boolean isOpen() {
        return open;
    }

    void defer(EventExecutor executor) {
        for (int i = 0; i < size; i++) {
            if (executors[i] == executor) {
                return;
            }
        }
        if (size == executors.length) {
            executors = Arrays.copyOf(executors, size * 2);
        }
        executors[size++] = executor;
    }

    /**
     * Wakes each recorded event loop once; a failure is rethrown after the others are woken. A rejection is ignored:
     * a terminating loop still runs accepted drains and the queue's termination listener releases the rest.
     */
    public void wake() {
        open = false;
        Throwable failure = null;
        for (int i = 0; i < size; i++) {
            final EventExecutor executor = executors[i];
            executors[i] = null;
            try {
                executor.execute(WAKE_UP);
            } catch (RejectedExecutionException e) {
                log.debug("Event loop {} rejected an outbound wake-up while shutting down", executor);
            } catch (Throwable e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        size = 0;
        switch (failure) {
            case null -> {}
            case Error error -> throw error;
            case RuntimeException exception -> throw exception;
            default -> throw new IllegalStateException("Outbound wake-up failed", failure);
        }
    }
}
