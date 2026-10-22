/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopWatchdogTest {
    private final ManualGameClock clock = TestLoops.clock();
    private final MockClock nanoClock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, nanoClock);
    private final List<Throwable> failures = new ArrayList<>();
    private final List<Thread> failedThreads = new ArrayList<>();

    private LoopWatchdog watchdog(EngineProperties settings, Loop loop) {
        final LoopWatchdog watchdog = new LoopWatchdog(settings, nanoClock::monotonicTime, (thread, failure) -> {
            failedThreads.add(thread);
            failures.add(failure);
        });
        watchdog.watch(loop, Thread.currentThread());
        return watchdog;
    }

    private Loop loop(EngineProperties settings) {
        return new Loop("watched", clock, TestLoops.SEED, settings, registry, PassRecorder.NOOP);
    }

    private void blockFor(long millis) {
        nanoClock.add(Duration.ofMillis(millis));
    }

    @Nested
    class Check {
        private final EngineProperties settings = EngineProperties.defaults();
        private final Loop loop = loop(settings);
        private final LoopWatchdog watchdog = watchdog(settings, loop);
        private final List<Integer> seen = new ArrayList<>();

        @Test
        void check_shouldLogStackOncePerThreshold_whenPassRunsTooLong() {
            loop.offer(l -> {
                blockFor(2_100);
                watchdog.check();
                seen.add(watchdog.warnings());
                blockFor(900);
                watchdog.check();
                seen.add(watchdog.warnings());
                blockFor(1_200);
                watchdog.check();
                seen.add(watchdog.warnings());
            });

            loop.runPass(TestLoops.T0);

            assertThat(seen).containsExactly(1, 1, 2);
            assertThat(failures).isEmpty();
        }

        @Test
        void check_shouldWarnOnce_whenTheCheckComesLateByManyThresholds() {
            loop.offer(l -> {
                blockFor(6_500);
                watchdog.check();
                seen.add(watchdog.warnings());
                blockFor(1_000);
                watchdog.check();
                seen.add(watchdog.warnings());
                blockFor(600);
                watchdog.check();
                seen.add(watchdog.warnings());
            });

            loop.runPass(TestLoops.T0);

            assertThat(seen).containsExactly(1, 1, 2);
        }

        @Test
        void check_shouldWarnAgain_whenALaterPassBlocks() {
            loop.offer(l -> {
                blockFor(2_100);
                watchdog.check();
            });
            loop.runPass(TestLoops.T0);
            loop.offer(l -> {
                blockFor(2_100);
                watchdog.check();
            });

            loop.runPass(TestLoops.T0 + 10);

            assertThat(watchdog.warnings()).isEqualTo(2);
        }

        @Test
        void check_shouldDoNothing_whenLoopIdle() {
            blockFor(60_000);

            watchdog.check();

            assertThat(loop.runningPassStartNanos()).isZero();
            assertThat(watchdog.warnings()).isZero();
        }

        @Test
        void check_shouldDoNothing_whenTheLongPassHasEnded() {
            loop.offer(l -> blockFor(5_000));
            loop.runPass(TestLoops.T0);

            watchdog.check();

            assertThat(loop.runningPassStartNanos()).isZero();
            assertThat(watchdog.warnings()).isZero();
        }

        @Test
        void check_shouldNotWarn_duringTheStartupPass() {
            loop.offer(l -> {
                blockFor(1_000);
                watchdog.check();
            });
            loop.offerStartup(l -> {
                blockFor(120_000);
                watchdog.check();
            });
            loop.offer(l -> {
                blockFor(3_000);
                watchdog.check();
            });

            loop.runPass(TestLoops.T0);

            assertThat(watchdog.warnings()).isZero();
        }

        @Test
        void check_shouldWarn_whenAPassAfterTheStartupPassBlocks() {
            loop.offerStartup(l -> blockFor(120_000));
            loop.runPass(TestLoops.T0);
            loop.offer(l -> {
                blockFor(2_100);
                watchdog.check();
            });

            loop.runPass(TestLoops.T0 + 10);

            assertThat(watchdog.warnings()).isEqualTo(1);
        }
    }

    @Nested
    class Exit {
        private final EngineProperties settings = EngineProperties.defaults().toBuilder()
                .blockedPassExit(Duration.ofSeconds(10))
                .build();
        private final Loop loop = loop(settings);
        private final LoopWatchdog watchdog = watchdog(settings, loop);

        @Test
        void check_shouldCallFailureHandler_whenExitThresholdPassed() {
            loop.offer(l -> {
                blockFor(9_900);
                watchdog.check();
                blockFor(200);
                watchdog.check();
                blockFor(10_000);
                watchdog.check();
            });

            loop.runPass(TestLoops.T0);

            assertThat(failedThreads).containsExactly(Thread.currentThread());
            assertThat(failures)
                    .singleElement(InstanceOfAssertFactories.THROWABLE)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("watched")
                    .hasMessageContaining("10100 ms");
        }

        @Test
        void check_shouldWarnInsteadOfExiting_whenExitIsDisarmed() {
            watchdog.disarmExit();
            loop.offer(l -> {
                blockFor(10_100);
                watchdog.check();
            });

            loop.runPass(TestLoops.T0);

            assertThat(failures).isEmpty();
            assertThat(watchdog.warnings()).isEqualTo(1);
        }

        @Test
        void check_shouldNotCallFailureHandler_whenExitIsOff() {
            final EngineProperties off = EngineProperties.defaults();
            final Loop unbounded = loop(off);
            final LoopWatchdog lenient = watchdog(off, unbounded);
            unbounded.offer(l -> {
                blockFor(600_000);
                lenient.check();
            });

            unbounded.runPass(TestLoops.T0);

            assertThat(failures).isEmpty();
            assertThat(lenient.warnings()).isEqualTo(1);
        }

        @Test
        void check_shouldNotCallFailureHandler_duringTheStartupPass() {
            loop.offerStartup(l -> {
                blockFor(600_000);
                watchdog.check();
            });

            loop.runPass(TestLoops.T0);

            assertThat(failures).isEmpty();
        }
    }
}
