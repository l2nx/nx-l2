/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.clock.SystemGameClock;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoopGroupTest {
    private static final ThreadFailureHandler UNEXPECTED = (thread, failure) -> {
        throw new AssertionError("Unexpected death of " + thread.getName(), failure);
    };
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final EngineProperties SETTINGS = EngineProperties.defaults();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SystemGameClock clock = new SystemGameClock(Clock.systemUTC());

    private static Thread threadOf(Loop loop) {
        final CompletableFuture<Thread> thread = new CompletableFuture<>();
        loop.offer(l -> thread.complete(Thread.currentThread()));
        return thread.orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Nested
    class Pin {
        @Test
        void pin_shouldGiveTheLoopAThreadOfItsOwn() {
            final LoopGroup group = new LoopGroup("l2nx-loop-g1", registry, SETTINGS, UNEXPECTED);
            try {
                final Loop world = TestLoops.loop("world", clock, registry);

                group.pin(world);

                assertThat(threadOf(world).getName()).isEqualTo("l2nx-loop-g1-world");
            } finally {
                group.shutdown(STOP_TIMEOUT);
            }
        }

        @Test
        void pin_shouldMakeTheLoopRejectCallsFromOtherThreads() {
            final LoopGroup group = new LoopGroup("l2nx-loop-g2", registry, SETTINGS, UNEXPECTED);
            try {
                final Loop loop = TestLoops.loop("pinned", clock, registry);

                group.pin(loop);

                assertThatThrownBy(loop::random)
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("l2nx-loop-g2-pinned");
            } finally {
                group.shutdown(STOP_TIMEOUT);
            }
        }

        @Test
        void pin_shouldThrow_whenTheGroupIsShutDown() {
            final LoopGroup group = new LoopGroup("l2nx-loop-g3", registry, SETTINGS, UNEXPECTED);
            group.shutdown(STOP_TIMEOUT);
            final Loop late = TestLoops.loop("late", clock, registry);

            assertThatIllegalStateException().isThrownBy(() -> group.pin(late));
            assertThat(late.offer(l -> {})).isTrue();
            late.random();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldStopTheLoopOnItsOwnThreadAndRemoveTheThreadMeters() {
            final LoopGroup group = new LoopGroup("l2nx-loop-g4", registry, SETTINGS, UNEXPECTED);
            final Loop loop = TestLoops.loop("stopping", clock, registry);
            final CompletableFuture<String> hookThread = new CompletableFuture<>();
            loop.addShutdownHook(l -> hookThread.complete(Thread.currentThread().getName()));
            group.pin(loop);
            final Thread thread = threadOf(loop);

            final List<String> actual = group.shutdown(STOP_TIMEOUT);

            assertThat(actual).isEmpty();
            assertThat(thread.isAlive()).isFalse();
            assertThat(hookThread).isCompletedWithValue("l2nx-loop-g4-stopping");
            assertThat(loop.offer(l -> {})).isFalse();
            assertThat(registry.find("l2nx.engine.loop.wakeups").counter()).isNull();
        }

        @Test
        void shutdown_shouldReturnTheThreadsStillAlive_whenAShutdownHookOutlastsTheTimeout() throws Exception {
            final LoopGroup group = new LoopGroup("l2nx-loop-g5", registry, SETTINGS, UNEXPECTED);
            final Loop loop = TestLoops.loop("hanging", clock, registry);
            final CountDownLatch release = new CountDownLatch(1);
            loop.addShutdownHook(l -> awaitReleased(release));
            group.pin(loop);
            final Thread thread = threadOf(loop);
            try {
                // The hook cannot return before the release, so the thread is alive when the wait ends.
                assertThat(group.shutdown(Duration.ofMillis(50))).containsExactly("l2nx-loop-g5-hanging");
                assertThat(thread.isAlive()).isTrue();
            } finally {
                release.countDown();
            }
            assertThat(thread.join(Duration.ofSeconds(5))).isTrue();
        }

        @Test
        void shutdown_shouldReturnAtOnce_whenCalledAgain() {
            final LoopGroup group = new LoopGroup("l2nx-loop-g6", registry, SETTINGS, UNEXPECTED);
            group.pin(TestLoops.loop("twice", clock, registry));
            group.shutdown(STOP_TIMEOUT);

            assertThat(group.shutdown(STOP_TIMEOUT)).isEmpty();
        }
    }

    @Nested
    class Watchdog {
        @Test
        void pin_shouldStartOneWatchdogThread_andShutdownShouldStopIt() {
            final LoopGroup group = new LoopGroup("l2nx-loop-g7", registry, SETTINGS, UNEXPECTED);
            group.pin(TestLoops.loop("first", clock, registry));
            group.pin(TestLoops.loop("second", clock, registry));
            final List<Thread> watchdogs = threadsNamed("l2nx-loop-g7-watchdog");

            group.shutdown(STOP_TIMEOUT);

            assertThat(watchdogs).singleElement().satisfies(thread -> {
                assertThat(thread.isDaemon()).isTrue();
                assertThat(thread.isAlive()).isFalse();
            });
        }

        @Test
        void pin_shouldReportTheLoopThread_whenAPassIsBlockedPastTheExitLimit() throws Exception {
            final EngineProperties settings = SETTINGS.toBuilder()
                    .blockedPassWarning(Duration.ofMillis(10))
                    .blockedPassExit(Duration.ofMillis(20))
                    .build();
            final CompletableFuture<Thread> reported = new CompletableFuture<>();
            final LoopGroup group =
                    new LoopGroup("l2nx-loop-g8", registry, settings, (thread, failure) -> reported.complete(thread));
            final Loop loop = TestLoops.loop("blocked", clock, registry);
            final CountDownLatch release = new CountDownLatch(1);
            try {
                group.pin(loop);
                loop.offer(l -> awaitReleased(release));

                assertThat(reported.get(5, TimeUnit.SECONDS).getName()).isEqualTo("l2nx-loop-g8-blocked");
            } finally {
                release.countDown();
                group.shutdown(STOP_TIMEOUT);
            }
        }

        @Test
        void shutdown_shouldWarnButNotExit_whenADrainPassIsBlocked() throws Exception {
            final EngineProperties settings = SETTINGS.toBuilder()
                    .blockedPassWarning(Duration.ofMillis(10))
                    .blockedPassExit(Duration.ofMillis(20))
                    .build();
            final List<Throwable> failures = new CopyOnWriteArrayList<>();
            final LoopGroup group =
                    new LoopGroup("l2nx-loop-g9", registry, settings, (thread, failure) -> failures.add(failure));
            final Loop loop = TestLoops.loop("draining", clock, registry);
            final CountDownLatch release = new CountDownLatch(1);
            loop.addShutdownHook(l -> l.offer(blocked -> awaitReleased(release)));
            final ListAppender<ILoggingEvent> logs = new ListAppender<>();
            final Logger watchdogLog = (Logger) LoggerFactory.getLogger(LoopWatchdog.class);
            logs.start();
            watchdogLog.addAppender(logs);
            group.pin(loop);
            final Thread thread = threadOf(loop);
            try {
                assertThat(group.shutdown(Duration.ofMillis(1_500))).containsExactly("l2nx-loop-g9-draining");
            } finally {
                release.countDown();
                watchdogLog.detachAppender(logs);
            }

            assertThat(thread.join(Duration.ofSeconds(5))).isTrue();
            assertThat(failures).isEmpty();
            assertThat(logs.list)
                    .filteredOn(event -> event.getThreadName().equals("l2nx-loop-g9-watchdog"))
                    .isNotEmpty()
                    .allSatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("draining");
                        assertThat(event.getThrowableProxy().getStackTraceElementProxyArray())
                                .anySatisfy(frame ->
                                        assertThat(frame.getSTEAsString()).contains("awaitReleased"));
                    });
        }

        @Test
        void shutdown_shouldWarnButNotExit_whenAShutdownHookIsBlocked() throws Exception {
            final EngineProperties settings = SETTINGS.toBuilder()
                    .blockedPassWarning(Duration.ofMillis(10))
                    .blockedPassExit(Duration.ofMillis(20))
                    .build();
            final List<Throwable> failures = new CopyOnWriteArrayList<>();
            final LoopGroup group =
                    new LoopGroup("l2nx-loop-g10", registry, settings, (thread, failure) -> failures.add(failure));
            final Loop loop = TestLoops.loop("saving", clock, registry);
            final CountDownLatch release = new CountDownLatch(1);
            loop.addShutdownHook(l -> awaitReleased(release));
            final ListAppender<ILoggingEvent> logs = new ListAppender<>();
            final Logger watchdogLog = (Logger) LoggerFactory.getLogger(LoopWatchdog.class);
            logs.start();
            watchdogLog.addAppender(logs);
            group.pin(loop);
            final Thread thread = threadOf(loop);
            try {
                assertThat(group.shutdown(Duration.ofMillis(1_500))).containsExactly("l2nx-loop-g10-saving");
            } finally {
                release.countDown();
                watchdogLog.detachAppender(logs);
            }

            assertThat(thread.join(Duration.ofSeconds(5))).isTrue();
            assertThat(failures).isEmpty();
            assertThat(logs.list)
                    .filteredOn(event -> event.getThreadName().equals("l2nx-loop-g10-watchdog"))
                    .isNotEmpty()
                    .allSatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("saving");
                        assertThat(event.getThrowableProxy().getStackTraceElementProxyArray())
                                .anySatisfy(frame ->
                                        assertThat(frame.getSTEAsString()).contains("awaitReleased"));
                    });
        }

        private static List<Thread> threadsNamed(String name) {
            return Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().equals(name))
                    .toList();
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
