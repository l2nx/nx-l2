/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.netty.channel.DefaultEventLoop;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventLoopTasksTest {
    @Nested
    class Run {
        @Test
        void run_shouldHopToTheLoop_whenCalledFromAnotherThread() throws Exception {
            final DefaultEventLoop loop = new DefaultEventLoop();
            try {
                final CountDownLatch ran = new CountDownLatch(1);
                final AtomicBoolean onLoop = new AtomicBoolean();

                EventLoopTasks.run(loop, () -> {
                    onLoop.set(loop.inEventLoop());
                    ran.countDown();
                });

                assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(onLoop).isTrue();
            } finally {
                loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
            }
        }

        @Test
        void run_shouldDropTheAction_whenTheLoopIsShutDown() throws Exception {
            final DefaultEventLoop loop = new DefaultEventLoop();
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
            final AtomicBoolean ran = new AtomicBoolean();

            assertThatCode(() -> EventLoopTasks.run(loop, () -> ran.set(true))).doesNotThrowAnyException();
            assertThat(ran).isFalse();
        }
    }
}
