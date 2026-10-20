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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue;

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

    private final MeterRegistry registry;
    private final Counter wakeups;
    private final ThreadFailureHandler failures;
    private final List<Loop> loops = new ArrayList<>();
    private final MpscUnboundedAtomicArrayQueue<Loop> joining = new MpscUnboundedAtomicArrayQueue<>(16);

    @Getter(AccessLevel.PACKAGE)
    private volatile boolean sleeping;

    private volatile boolean stopping;
    // Some loop's next pass comes no later than its pass-min gate: the park ends within pass-min anyway, and an offer
    // to that loop would wait for the same pass, so it needs no wake-up.
    private boolean gated;

    // Times the sleeping flag was raised; read by others only after join.
    @Getter(AccessLevel.PACKAGE)
    private long sleeps;

    LoopThread(String name, MeterRegistry registry, ThreadFailureHandler failures) {
        this.name = Objects.requireNonNull(name, "name");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.wakeups =
                Counter.builder("l2nx.engine.loop.wakeups").tag("thread", name).register(registry);
        // Daemon: a shutdown hook that never returns must not keep the JVM up after the context closed.
        this.thread = Thread.ofPlatform().name(name).daemon(true).unstarted(this::run);
    }

    void start() {
        thread.start();
    }

    // Synchronized with the stop request: a loop added before it is taken by the thread's last addJoining.
    synchronized void add(Loop loop) {
        if (stopping) {
            throw new IllegalStateException("Loop thread " + name + " is stopping");
        }
        loop.bind(thread, this);
        joining.offer(loop);
        wake();
    }

    @Override
    public void wake() {
        if (sleeping && SLEEPING.compareAndSet(this, true, false)) {
            wakeups.increment();
            LockSupport.unpark(thread);
        }
    }

    void requestStop() {
        synchronized (this) {
            stopping = true;
        }
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
                addJoining();
                final long waitMillis = runDuePasses();
                Loop.clearInterrupt("by a pass");
                if (waitMillis > 0) {
                    park(waitMillis);
                }
            }
        } catch (Throwable failure) {
            // An Error, or a defect in the loop itself: the partitions of this thread cannot go on.
            log.error("Loop thread {} died", name, failure);
            failures.onThreadFailure(thread, failure);
        } finally {
            // A dead thread refuses loops too, so none is added after the ones it stops here.
            synchronized (this) {
                stopping = true;
            }
            addJoining();
            for (Loop loop : loops) {
                stopLoop(loop);
            }
            loops.clear();
        }
    }

    private void addJoining() {
        Loop loop;
        while ((loop = joining.poll()) != null) {
            loops.add(loop);
        }
    }

    /** Runs every due pass once; returns 0 if any ran, else the milliseconds until the earliest due work. */
    private long runDuePasses() {
        boolean ran = false;
        gated = false;
        long waitMillis = Long.MAX_VALUE;
        for (int i = 0; i < loops.size(); i++) {
            final Loop loop = loops.get(i);
            final long now = loop.getClock().gameMillis();
            final long at = loop.nextPassAt(now);
            if (at <= now) {
                runPass(loop, now);
                ran = true;
            } else {
                waitMillis = Math.min(waitMillis, at - now);
                // From the same reading: a second one could see an offer that came after this one and park for good.
                gated |= at <= loop.passGate();
            }
        }
        return ran ? 0 : waitMillis;
    }

    private void park(long waitMillis) {
        final long nanos = TimeUnit.MILLISECONDS.toNanos(waitMillis);
        if (gated) {
            LockSupport.parkNanos(this, nanos);
            return;
        }
        SLEEPING.setVolatile(this, true);
        sleeps++;
        // Re-check after publishing the flag: an offer before it saw the thread awake and did not unpark it.
        if (stopping || !joining.isEmpty() || anyWork()) {
            SLEEPING.setVolatile(this, false);
            return;
        }
        LockSupport.parkNanos(this, nanos);
        SLEEPING.setVolatile(this, false);
    }

    private boolean anyWork() {
        for (int i = 0; i < loops.size(); i++) {
            final Loop loop = loops.get(i);
            if (loop.nextWorkAt() <= loop.getClock().gameMillis()) {
                return true;
            }
        }
        return false;
    }

    private static void runPass(Loop loop, long now) {
        try {
            loop.runPass(now);
        } catch (RuntimeException e) {
            log.error("Pass of loop {} failed", loop.getName(), e);
        }
    }

    private static void stopLoop(Loop loop) {
        try {
            loop.shutdown();
        } catch (Throwable e) {
            log.error("Shutdown of loop {} failed", loop.getName(), e);
        } finally {
            loop.close();
        }
    }
}
