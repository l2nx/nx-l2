/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.boot.ServerExit;
import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.WorldLoader;
import app.l2nx.highfive.WorldShutdown;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.context.support.GenericApplicationContext;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class WorldLifecycleTest {
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);
    private static final long WAIT_SECONDS = 5;
    private static final int PLAYER_ID = 0x7ffe_0001;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PartitionRuntime runtime = new PartitionRuntime(
            EngineProperties.defaults(),
            new SystemGameClock(Clock.systemUTC()),
            registry,
            () -> 42L,
            new ServerExit(new GenericApplicationContext()),
            STOP_TIMEOUT);
    private final WorldLoader loader = mock(WorldLoader.class);
    private final WorldShutdown shutdown = mock(WorldShutdown.class);
    private final OnlinePlayersGauge onlinePlayers = new OnlinePlayersGauge(registry);
    private final WorldSystem system = mock(WorldSystem.class);
    private final WorldLifecycle lifecycle =
            new WorldLifecycle(runtime, loader, shutdown, onlinePlayers, List.of(system));

    private Loop previous;

    @BeforeEach
    void startTheRuntime() {
        previous = WorldPartition.swap(null);
        runtime.start();
    }

    @AfterEach
    void stopTheRuntime() {
        runtime.stop();
        WorldPartition.swap(previous);
    }

    @Nested
    class Start {
        @Test
        void start_shouldLoadInAStartupPassOfTheWorldLoop() {
            final AtomicReference<Loop> loadedOn = new AtomicReference<>();
            doAnswer(invocation -> {
                        loadedOn.set(Loop.current());
                        return null;
                    })
                    .when(loader)
                    .load();

            lifecycle.start();

            assertThat(loadedOn.get()).isSameAs(runtime.world());
            assertThat(registry.get("l2nx.engine.pass").timer().count())
                    .as("a startup pass is not timed")
                    .isZero();
            assertThat(lifecycle.isRunning()).isTrue();
        }

        @Test
        void start_shouldPublishTheOnlinePlayers_fromTheStartupPassOn() throws Exception {
            final AtomicInteger expected = new AtomicInteger();
            final Player player = mock(Player.class);
            when(player.getObjectId()).thenReturn(PLAYER_ID);
            when(player.isPlayer()).thenReturn(true);
            when(player.asPlayer()).thenReturn(player);
            doAnswer(invocation -> {
                        expected.set(World.getPlayers().size() + 1);
                        World.addObject(player);
                        return null;
                    })
                    .when(loader)
                    .load();

            Throwable failure = null;
            try {
                lifecycle.start();
                // The startup pass flushes after the load completes, so a later pass has seen its flush.
                onTheWorldLoop(() -> {});

                assertThat(registry.get("l2nx.players.online").gauge().value()).isEqualTo(expected.get());
            } catch (Throwable e) {
                failure = e;
                throw e;
            } finally {
                // A failed start leaves no loop to clean up on; that cleanup error must not hide the failure.
                try {
                    onTheWorldLoop(() -> World.removeObject(player));
                } catch (Exception cleanup) {
                    if (failure == null) {
                        throw cleanup;
                    }
                    failure.addSuppressed(cleanup);
                }
            }
        }

        @Test
        void start_shouldInstallTheWorldSystemsInTheStartupPass_beforeTheLoad() {
            final AtomicReference<Loop> installedOn = new AtomicReference<>();
            doAnswer(invocation -> {
                        installedOn.set(Loop.current());
                        return null;
                    })
                    .when(system)
                    .install(runtime.world());

            lifecycle.start();

            assertThat(installedOn.get()).isSameAs(runtime.world());
            final InOrder order = inOrder(system, loader);
            order.verify(system).install(runtime.world());
            order.verify(loader).load();
        }

        @Test
        void start_shouldBindTheWorldPartition() {
            lifecycle.start();

            assertThat(WorldPartition.loop()).isSameAs(runtime.world());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.partition.WorldLifecycleTest#failures")
        void start_shouldThrowTheLoadFailure_whenTheLoadFails(Throwable failure) throws Exception {
            doThrow(failure).when(loader).load();

            assertThatThrownBy(lifecycle::start).isSameAs(failure);

            assertThat(lifecycle.isRunning()).isFalse();
            final CompletableFuture<Void> ran = new CompletableFuture<>();
            runtime.world().execute(() -> ran.complete(null));
            ran.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Nested
    class Stop {
        @Test
        void stop_shouldRunTheShutdownOnTheWorldLoop_beforeReturning() {
            lifecycle.start();
            final AtomicReference<Loop> shutDownOn = new AtomicReference<>();
            doAnswer(invocation -> {
                        shutDownOn.set(Loop.current());
                        return null;
                    })
                    .when(shutdown)
                    .run();

            lifecycle.stop();

            assertThat(shutDownOn.get()).isSameAs(runtime.world());
            assertThat(lifecycle.isRunning()).isFalse();
        }

        @Test
        void stop_shouldLeaveTheShutdownToTheLoopsHooks_whenTheWorldLoopStoppedFirst() {
            lifecycle.start();
            final AtomicReference<Loop> shutDownOn = new AtomicReference<>();
            doAnswer(invocation -> {
                        shutDownOn.set(Loop.current());
                        return null;
                    })
                    .when(shutdown)
                    .run();
            final Loop world = runtime.world();
            runtime.stop();

            lifecycle.stop();

            assertThat(shutDownOn.get()).isSameAs(world);
            verify(shutdown).run();
            assertThat(lifecycle.isRunning()).isFalse();
        }

        @Test
        void stop_shouldShutTheWorldDownOnce_whenTheLoopStopsAfterwards() {
            lifecycle.start();

            lifecycle.stop();
            runtime.stop();

            verify(shutdown).run();
        }

        @Test
        void stop_shouldNotShutTheWorldDown_whenTheLoadFailed() {
            doThrow(new IllegalStateException("thrown by the test"))
                    .when(loader)
                    .load();
            assertThatThrownBy(lifecycle::start).isInstanceOf(IllegalStateException.class);

            runtime.stop();

            verify(shutdown, never()).run();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.partition.WorldLifecycleTest#failures")
        void stop_shouldThrowTheShutdownFailure_whenTheShutdownFails(Throwable failure) {
            lifecycle.start();
            doThrow(failure).when(shutdown).run();

            assertThatThrownBy(lifecycle::stop).isSameAs(failure);

            assertThat(lifecycle.isRunning()).isFalse();
            verify(shutdown).run();
        }
    }

    private void onTheWorldLoop(Runnable work) throws Exception {
        CompletableFuture.runAsync(work, runtime.world()::execute).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    static Stream<Throwable> failures() {
        return Stream.of(new IllegalStateException("thrown by the test"), new AssertionError("thrown by the test"));
    }
}
