/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.AbstractEventExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NettyLazyExecuteTest {
    private static final long BLOCK_MILLIS = 200;

    @Nested
    class LazyExecute {
        @Test
        void lazyExecute_shouldWaitForTheNextWakeUp_whenTheEventLoopBlocksInItsSelector() throws Exception {
            final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            try {
                final AbstractEventExecutor loop = (AbstractEventExecutor) group.next();
                loop.submit(() -> {}).sync();
                // With no channel and no timer the selector blocks until woken; give the loop time to get there.
                Thread.sleep(BLOCK_MILLIS);
                final CountDownLatch ran = new CountDownLatch(1);

                loop.lazyExecute(ran::countDown);

                assertThat(ran.await(BLOCK_MILLIS, TimeUnit.MILLISECONDS))
                        .as("ran without a wake-up")
                        .isFalse();
                loop.execute(() -> {});
                assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            }
        }
    }
}
