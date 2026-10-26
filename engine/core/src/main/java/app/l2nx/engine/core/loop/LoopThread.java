/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.loop.inbox.Wakeup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Runs the passes of one loop, pinned to it for the loop's lifetime, as a Netty channel is to its event loop. */
@Slf4j
final class LoopThread implements Wakeup {
    private static final VarHandle SLEEPING;

    static {
        try {
            SLEEPING = MethodHandles.lookup().findVarHandle(LoopThread.class, "sleeping", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Getter
    private final String name;

    @Getter(AccessLevel.PACKAGE)
    private final Thread thread;

    private final Loop loop;
    private final MeterRegistry registry;
    private final Counter wakeups;
    private final ThreadFailureHandler failures;

    @Getter(AccessLevel.PACKAGE)
    private volatile boolean sleeping;

    private volatile boolean stopping;

    // Times the sleeping flag was raised; read by others only after join.
    @Getter(AccessLevel.PACKAGE)
    private long sleeps;

    LoopThread(String name, Loop loop, MeterRegistry registry, ThreadFailureHandler failures) {
        this.name = Objects.requireNonNull(name, "name");
        this.loop = Objects.requireNonNull(loop, "loop");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.failures = Objects.requireNonNull(failures, "failures");
        // Daemon: a shutdown hook that never returns must not keep the JVM up after the context closed.
        this.thread = Thread.ofPlatform().name(name).daemon(true).unstarted(this::run);
        this.wakeups =
                Counter.builder("l2nx.engine.loop.wakeups").tag("thread", name).register(registry);
        try {
            loop.bind(thread, this);
        } catch (RuntimeException e) {
            registry.remove(wakeups);
            throw e;
        }
    }

    void start() {
        thread.start();
    }

    @Override
    public void wake() {
        if (sleeping && SLEEPING.compareAndSet(this, true, false)) {
            wakeups.increment();
            LockSupport.unpark(thread);
        }
    }

    void requestStop() {
        stopping = true;
        LockSupport.unpark(thread);
    }

    void join() throws InterruptedException {
        thread.join();
    }

    boolean join(Duration timeout) throws InterruptedException {
        return thread.join(timeout);
    }

    void close() {
        registry.remove(wakeups);
    }

    private void run() {
        try {
            while (!stopping) {
                final long now = loop.getClock().gameMillis();
                final long at = loop.nextPassAt(now);
                if (at <= now) {
                    runPass(now);
                } else {
                    // From the same reading: a second one could see an offer that came after this one and park for
                    // good.
                    park(at - now, at <= loop.passGate());
                }
                Loop.clearInterrupt("by a pass");
            }
        } catch (Throwable failure) {
            // An Error, or a defect in the loop itself: the partition of this thread cannot go on.
            log.error("Loop thread {} died", name, failure);
            failures.onThreadFailure(thread, failure);
        } finally {
            stopLoop();
        }
    }

    /**
     * {@code gated}: the next pass comes no later than the pass-min gate, so the park ends within pass-min anyway and an
     * offer would wait for that pass; it needs no wake-up.
     */
    private void park(long waitMillis, boolean gated) {
        final long nanos = TimeUnit.MILLISECONDS.toNanos(waitMillis);
        if (gated) {
            LockSupport.parkNanos(this, nanos);
            return;
        }
        SLEEPING.setVolatile(this, true);
        sleeps++;
        // Re-check after publishing the flag: an offer before it saw the thread awake and did not unpark it.
        if (stopping || loop.nextWorkAt() <= loop.getClock().gameMillis()) {
            SLEEPING.setVolatile(this, false);
            return;
        }
        LockSupport.parkNanos(this, nanos);
        SLEEPING.setVolatile(this, false);
    }

    private void runPass(long now) {
        try {
            loop.runPass(now);
        } catch (RuntimeException e) {
            log.error("Pass of loop {} failed", loop.getName(), e);
        }
    }

    private void stopLoop() {
        try {
            loop.shutdown();
        } catch (Throwable e) {
            log.error("Shutdown of loop {} failed", loop.getName(), e);
        } finally {
            loop.close();
        }
    }
}
