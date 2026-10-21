/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.engine.core.session.command.CommandDispatcher;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Counts the sessions whose close is not yet handled, so the shutdown can wait for them. */
public final class SessionFactory {
    private final Session.Limits limits;
    private final DrainScheduler scheduler;
    private final CommandDispatcher dispatcher;
    private final SessionMetrics metrics;
    private final ReentrantLock openLock = new ReentrantLock();
    private final Condition allClosed = openLock.newCondition();
    private int open;

    public SessionFactory(
            Session.Limits limits, DrainScheduler scheduler, CommandDispatcher dispatcher, SessionMetrics metrics) {
        this.limits = limits;
        this.scheduler = scheduler;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    public Session create(SessionTransport transport) {
        final Session session = new Session(
                limits, scheduler, dispatcher, metrics, transport, TimeMeter.SYSTEM_NANOTIME, this::finished);
        // Counted after the constructor, which cannot close the session, so a failed construction leaves no count.
        openLock.lock();
        try {
            open++;
        } finally {
            openLock.unlock();
        }
        return session;
    }

    /**
     * Waits until every session created here, also one created during the wait, has handled its close.
     *
     * @return {@code false} on timeout or interrupt (the interrupt flag is set again)
     */
    public boolean awaitClosed(Duration timeout) {
        long remaining = timeout.toNanos();
        openLock.lock();
        try {
            while (open > 0) {
                if (remaining <= 0) {
                    return false;
                }
                remaining = allClosed.awaitNanos(remaining);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            openLock.unlock();
        }
    }

    public int openSessions() {
        openLock.lock();
        try {
            return open;
        } finally {
            openLock.unlock();
        }
    }

    private void finished() {
        openLock.lock();
        try {
            if (--open == 0) {
                allClosed.signalAll();
            }
        } finally {
            openLock.unlock();
        }
    }
}
