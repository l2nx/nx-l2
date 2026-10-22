/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;

class EngineAutoConfigurationTest {
    private final BlockingQueue<ServerExitCode> exits = new LinkedBlockingQueue<>();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EngineAutoConfiguration.class))
            .withBean(GameClock.class, () -> new SystemGameClock(Clock.systemUTC()))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(ServerExit.class, () -> new ServerExit(null) {
                @Override
                public void exit(ServerExitCode code) {
                    exits.add(code);
                }
            });

    private static Thread threadOf(Loop loop) {
        final CompletableFuture<Thread> thread = new CompletableFuture<>();
        loop.offer(l -> thread.complete(Thread.currentThread()));
        return thread.orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Nested
    class Start {
        @Test
        void context_shouldStartTheWorldOnAThreadOfItsOwn() {
            runner.run(context -> {
                assertThat(context).hasSingleBean(PartitionRuntime.class);
                assertThat(context.getBean(EngineProperties.class).passMin()).isEqualTo(Duration.ofMillis(5));
                final PartitionRuntime runtime = context.getBean(PartitionRuntime.class);

                assertThat(runtime.isRunning()).isTrue();
                assertThat(runtime.getPhase())
                        .isEqualTo(SmartLifecycle.DEFAULT_PHASE - 512)
                        .isEqualTo(PartitionRuntime.PHASE);
                assertThat(threadOf(runtime.world()).getName()).isEqualTo("l2nx-loop-world");
            });
        }

        @Test
        void context_shouldFail_whenASettingIsInvalid() {
            runner.withPropertyValues("l2nx.engine.trigger-depth=0").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("l2nx.engine.trigger-depth");
            });
        }

        @Test
        void autoConfiguration_shouldBeRegistered() {
            assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                            .getCandidates())
                    .contains(EngineAutoConfiguration.class.getName());
        }
    }

    @Nested
    class Stop {
        @Test
        void stop_shouldStopTheWorldThread() {
            runner.run(context -> {
                final PartitionRuntime runtime = context.getBean(PartitionRuntime.class);
                final Thread world = threadOf(runtime.world());

                runtime.stop();

                assertThat(runtime.isRunning()).isFalse();
                assertThat(world.isAlive()).isFalse();
                assertThatIllegalStateException().isThrownBy(runtime::world);
                assertThat(exits).isEmpty();
            });
        }

        @Test
        void stop_shouldRunTheCallbackAfterTheWorldThreadEnded() {
            runner.run(context -> {
                final PartitionRuntime runtime = context.getBean(PartitionRuntime.class);
                final Thread world = threadOf(runtime.world());
                final CompletableFuture<Boolean> aliveAtCallback = new CompletableFuture<>();

                runtime.stop(() -> aliveAtCallback.complete(world.isAlive()));

                assertThat(aliveAtCallback.orTimeout(5, TimeUnit.SECONDS).join())
                        .isFalse();
                assertThat(runtime.isRunning()).isFalse();
            });
        }

        @Test
        void close_shouldEndAfterTheShutdownPhaseTimeout_whenAShutdownHookHangs() {
            final CountDownLatch release = new CountDownLatch(1);
            runner.withConfiguration(AutoConfigurations.of(LifecycleAutoConfiguration.class))
                    .withPropertyValues("spring.lifecycle.timeout-per-shutdown-phase=100ms")
                    .run(context -> {
                        final PartitionRuntime runtime = context.getBean(PartitionRuntime.class);
                        runtime.world().offer(loop -> loop.addShutdownHook(l -> awaitReleased(release)));
                        final Thread world = threadOf(runtime.world());
                        try {
                            assertTimeoutPreemptively(Duration.ofSeconds(5), context::close);

                            // The hook cannot return before the release, so the world thread outlives the close.
                            assertThat(world.isAlive()).isTrue();
                            assertThat(runtime.isRunning()).isFalse();
                        } finally {
                            release.countDown();
                        }
                        assertThat(world.join(Duration.ofSeconds(5))).isTrue();
                        assertThat(exits).isEmpty();
                    });
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldWarnButNotExit_whenAWorldSaveBlocksAPassPastTheExitLimit() {
            final ListAppender<ILoggingEvent> logs = new ListAppender<>();
            final Logger watchdogLog = (Logger) LoggerFactory.getLogger("app.l2nx.engine.core.loop.LoopWatchdog");
            logs.start();
            watchdogLog.addAppender(logs);
            try {
                runner.withBean(BlockingSave.class)
                        .withPropertyValues(
                                "l2nx.engine.blocked-pass-warning=50ms", "l2nx.engine.blocked-pass-exit=100ms")
                        .run(context -> {
                            assertThat(context).hasNotFailed();

                            context.close();

                            assertThat(exits).isEmpty();
                        });
            } finally {
                watchdogLog.detachAppender(logs);
            }

            // Tests run in parallel and share the logger: only a stack inside this save counts.
            assertThat(logs.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy().getStackTraceElementProxyArray())
                        .anySatisfy(frame -> assertThat(frame.getSTEAsString()).contains("BlockingSave"));
            });
        }
    }

    static final class BlockingSave implements SmartLifecycle {
        // Past the exit limit by more than a watchdog check interval, so an armed exit would fire.
        private static final long BLOCKED_MILLIS = 1_500;

        private final PartitionRuntime runtime;
        private volatile boolean running;

        BlockingSave(PartitionRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            final CompletableFuture<Void> saved = new CompletableFuture<>();
            runtime.world().offer(loop -> {
                save();
                saved.complete(null);
            });
            saved.orTimeout(10, TimeUnit.SECONDS).join();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return PartitionRuntime.PHASE + 1;
        }

        private static void save() {
            try {
                Thread.sleep(BLOCKED_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    class Crash {
        @Test
        void deadWorldThread_shouldExitTheServerWithCrash() {
            runner.run(context -> {
                context.getBean(PartitionRuntime.class).world().offer(loop -> {
                    throw new AssertionError("broken invariant");
                });

                assertThat(exits.poll(5, TimeUnit.SECONDS)).isEqualTo(ServerExitCode.CRASH);
            });
        }
    }

    private static void awaitReleased(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("The test never released the hook");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
