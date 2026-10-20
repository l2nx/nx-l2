/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.config.Transport;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NetworkEventLoopsTest {
    @Nested
    class Constructor {
        @Test
        void groups_shouldHaveOneBossAndConfiguredWorkers() {
            final NetworkProperties properties = withWorkers(3);

            try (NetworkEventLoops loops = new NetworkEventLoops(properties, TransportResolver.nativeAvailability())) {
                assertThat(count(loops.boss())).isEqualTo(1);
                assertThat(count(loops.workers())).isEqualTo(3);
                assertThat(loops.transport().transport()).isNotEqualTo(Transport.AUTO);
            }
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldTerminateBothGroups() {
            final NetworkEventLoops loops =
                    new NetworkEventLoops(withWorkers(1), TransportResolver.nativeAvailability());

            loops.close();

            assertThat(loops.boss().isTerminated()).isTrue();
            assertThat(loops.workers().isTerminated()).isTrue();
        }

        @Test
        void close_shouldShutDownWorkers_whenBossShutdownFails() {
            final ResolvedTransport nio = TransportResolver.nativeAvailability().resolve(Transport.NIO);
            final FailingShutdownGroup boss = new FailingShutdownGroup(nio.ioHandlerFactory());
            final EventLoopGroup workers = new MultiThreadIoEventLoopGroup(1, nio.ioHandlerFactory());
            final NetworkEventLoops loops = new NetworkEventLoops(nio, boss, workers, withWorkers(1));
            try {
                assertThatThrownBy(loops::close).isInstanceOf(IllegalStateException.class);

                assertThat(workers.isTerminated()).isTrue();
            } finally {
                boss.shutDownForReal();
            }
        }
    }

    @Nested
    class OrShutDown {
        @Test
        void orShutDown_shouldShutDownTheBoss_whenWorkerGroupCannotBeCreated() {
            final EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

            assertThatThrownBy(() -> NetworkEventLoops.orShutDown(boss, () -> {
                        throw new IllegalArgumentException("nThreads: -1");
                    }))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(boss.isShuttingDown()).isTrue();
            boss.terminationFuture().syncUninterruptibly();
        }
    }

    private static int count(Iterable<EventExecutor> group) {
        final List<EventExecutor> executors = new ArrayList<>();
        group.forEach(executors::add);
        return executors.size();
    }

    private static NetworkProperties withWorkers(int workers) {
        return NetworkProperties.defaults().toBuilder()
                .transport(Transport.NIO)
                .workerThreads(workers)
                .shutdownQuietPeriod(Duration.ZERO)
                .shutdownTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static final class FailingShutdownGroup extends MultiThreadIoEventLoopGroup {
        FailingShutdownGroup(IoHandlerFactory factory) {
            super(1, factory);
        }

        @Override
        public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            throw new IllegalStateException("boss shutdown failed");
        }

        void shutDownForReal() {
            super.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
