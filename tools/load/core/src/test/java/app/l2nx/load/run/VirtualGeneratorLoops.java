/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.injection.RunClock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Ticker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Netty's own test event loop on a {@link MockTicker}: every load bot shares one loop on the test thread, and a wait of
 * the run moves the virtual clock from one scheduled task to the next and runs what is due. A task "blocks" the loop by
 * advancing the ticker itself, so a run is the same however busy the machine is.
 */
final class VirtualGeneratorLoops implements GeneratorLoops, RunClock {
    private final MockTicker ticker = Ticker.newMockTicker();
    private final EmbeddedChannel channel =
            EmbeddedChannel.builder().ticker(ticker).build();

    /** Moves the virtual clock, as a task blocking its event loop for that long would. */
    void block(long duration, TimeUnit unit) {
        ticker.advance(duration, unit);
    }

    @Override
    public RunClock clock() {
        return this;
    }

    @Override
    public EventLoopGroup open(int threads, IoHandlerFactory ioHandlerFactory) {
        return channel.eventLoop();
    }

    @Override
    public void close(EventLoopGroup group) {
        drain();
    }

    @Override
    public long now() {
        return ticker.nanoTime();
    }

    @Override
    public void sleepUntil(long time) {
        runUntil(time, () -> false);
    }

    @Override
    public boolean await(CountDownLatch latch, long deadline) {
        return runUntil(deadline, () -> latch.getCount() == 0);
    }

    private boolean runUntil(long deadline, BooleanSupplier done) {
        drain();
        while (!done.getAsBoolean()) {
            final long left = deadline - now();
            if (left <= 0) {
                return false;
            }
            final long next = channel.runScheduledPendingTasks();
            // A scheduled task may hand work to the loop at once; it runs before the clock moves on.
            if (!channel.hasPendingTasks()) {
                ticker.advance(next < 0 ? left : Math.min(next, left), TimeUnit.NANOSECONDS);
            }
            drain();
        }
        return true;
    }

    /** Runs what is due, including what those tasks hand to the loop, and fails on a task that threw. */
    private void drain() {
        do {
            channel.runPendingTasks();
        } while (channel.hasPendingTasks());
        channel.checkException();
    }
}
