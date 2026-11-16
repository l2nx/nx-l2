/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.injection.RunClock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Netty I/O event loops on platform threads, in real time. */
final class SystemGeneratorLoops implements GeneratorLoops {
    private static final Duration SHUTDOWN_QUIET_PERIOD = Duration.ofMillis(100);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public RunClock clock() {
        return RunClock.SYSTEM;
    }

    @Override
    public EventLoopGroup open(int threads, IoHandlerFactory ioHandlerFactory) {
        return new MultiThreadIoEventLoopGroup(threads, new DefaultThreadFactory("l2nx-load"), ioHandlerFactory);
    }

    @Override
    public void close(EventLoopGroup group) {
        final Future<?> terminated = group.shutdownGracefully(
                SHUTDOWN_QUIET_PERIOD.toMillis(), SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        // The quiet period may run up to the timeout, so wait for both plus a margin.
        final long waitMillis = SHUTDOWN_QUIET_PERIOD.toMillis() + SHUTDOWN_TIMEOUT.toMillis() + 1_000;
        if (!terminated.awaitUninterruptibly(waitMillis)) {
            throw new IllegalStateException("Load bot event loops did not terminate in " + waitMillis + " ms");
        }
    }
}
