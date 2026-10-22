/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.inbox.Wakeup;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoopOwnershipTest {
    private final ManualGameClock clock = TestLoops.clock();
    private final MockClock meterClock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, meterClock);
    private final Loop loop = TestLoops.loop("test", clock, registry);
    private final Logger loopLog = (Logger) LoggerFactory.getLogger(Loop.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            if (Thread.currentThread() == testThread) {
                super.append(event);
            }
        }
    };

    private volatile Thread testThread;

    @BeforeEach
    void captureLogs() {
        testThread = Thread.currentThread();
        logs.start();
        loopLog.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        loopLog.detachAppender(logs);
        logs.stop();
    }

    private double violations() {
        return registry.get("l2nx.engine.ownership.violations")
                .tag("loop", "test")
                .counter()
                .count();
    }

    private long violationLogs() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().startsWith("Ownership violation"))
                .count();
    }

    private void checkOwner() {
        loop.checkOwner();
    }

    @Nested
    class CheckOwner {
        @Test
        void checkOwner_shouldPass_whenCalledInPass() {
            final AtomicBoolean checked = new AtomicBoolean();
            loop.execute(() -> {
                loop.checkOwner();
                checked.set(true);
            });

            loop.runPass(clock.gameMillis() + 5);

            assertThat(checked).isTrue();
            assertThat(violations()).isZero();
        }

        @Test
        void checkOwner_shouldPass_whenCalledOnThePinnedThread() {
            loop.bind(Thread.currentThread(), Wakeup.NONE);

            loop.checkOwner();

            assertThat(violations()).isZero();
        }

        @Test
        void checkOwner_shouldThrowAndCount_whenCalledOffLoop() {
            assertThatThrownBy(loop::checkOwner)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("loop test")
                    .hasMessageContaining(Thread.currentThread().getName());

            assertThat(violations()).isEqualTo(1);
        }

        @Test
        void checkOwner_shouldThrow_whenCalledFromAnotherThreadThanThePinnedOne() {
            loop.bind(Thread.currentThread(), Wakeup.NONE);

            assertThatThrownBy(
                            () -> CompletableFuture.runAsync(loop::checkOwner).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(violations()).isEqualTo(1);
        }

        @Test
        void checkOwner_shouldCountEveryViolation_butLogOnce_whenOneSiteRepeatsWithinAMinute() {
            for (int i = 0; i < 100; i++) {
                assertThatThrownBy(loop::checkOwner).isInstanceOf(IllegalStateException.class);
            }

            assertThat(violations()).isEqualTo(100);
            assertThat(violationLogs()).isEqualTo(1);
        }

        @Test
        void checkOwner_shouldLogAgain_whenAMinutePassed() {
            assertThatThrownBy(loop::checkOwner).isInstanceOf(IllegalStateException.class);
            meterClock.add(Duration.ofSeconds(59));
            assertThatThrownBy(loop::checkOwner).isInstanceOf(IllegalStateException.class);
            meterClock.add(Duration.ofSeconds(1));
            assertThatThrownBy(loop::checkOwner).isInstanceOf(IllegalStateException.class);

            assertThat(violationLogs()).isEqualTo(2);
        }

        @Test
        void checkOwner_shouldLogEachSite_whenTwoSitesViolateWithinAMinute() {
            assertThatThrownBy(() -> loop.checkOwner()).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> loop.checkOwner()).isInstanceOf(IllegalStateException.class);

            assertThat(violationLogs()).isEqualTo(2);
        }

        @Test
        void checkOwner_shouldNameTheCallerOfABridge_whenCalledThroughOne() {
            assertThatThrownBy(() -> checkOwner())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(LoopOwnershipTest.class.getName() + "$CheckOwner.lambda$")
                    .hasMessageNotContaining("LoopOwnershipTest.checkOwner");
        }
    }
}
