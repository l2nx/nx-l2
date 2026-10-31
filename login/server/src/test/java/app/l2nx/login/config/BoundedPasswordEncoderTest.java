/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BoundedPasswordEncoderTest {
    private static final int LIMIT = 2;
    private static final int CALLERS = 5;
    private static final String STORED = "stored";

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final BlockingEncoder delegate = new BlockingEncoder();
    private final PasswordEncoder encoder = new BoundedPasswordEncoder(delegate, LIMIT, registry);

    @Nested
    class Encode {
        @Test
        void encode_shouldRunAtMostTheLimit_whenMoreCallersHash() throws Exception {
            assertBounded(encoder -> encoder.encode("secret"));
        }
    }

    @Nested
    class Matches {
        @Test
        void matches_shouldRunAtMostTheLimit_whenMoreCallersHash() throws Exception {
            assertBounded(encoder -> encoder.matches("secret", STORED));
        }
    }

    @Nested
    class UpgradeEncoding {
        @Test
        void upgradeEncoding_shouldNotWait_whenEverySlotIsHeld() throws Exception {
            final List<Thread> holders = start(LIMIT, encoder -> encoder.encode("secret"));
            assertThat(delegate.entered.await(5, TimeUnit.SECONDS)).isTrue();

            final boolean actual =
                    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> encoder.upgradeEncoding(STORED));

            assertThat(actual).isTrue();
            delegate.release.countDown();
            join(holders);
        }
    }

    private void assertBounded(Consumer<PasswordEncoder> hash) throws Exception {
        final List<Thread> callers = start(CALLERS, hash);
        assertThat(delegate.entered.await(5, TimeUnit.SECONDS)).isTrue();
        awaitQueued(CALLERS - LIMIT);

        assertThat(delegate.running.get()).isEqualTo(LIMIT);
        delegate.release.countDown();
        join(callers);
        assertThat(delegate.maxRunning.get()).isEqualTo(LIMIT);
        assertThat(delegate.finished.get()).isEqualTo(CALLERS);
        assertThat(registry.get("l2nx.login.password.wait").timer().count()).isEqualTo(CALLERS);
    }

    private List<Thread> start(int count, Consumer<PasswordEncoder> hash) {
        final List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            threads.add(Thread.ofVirtual().start(() -> hash.accept(encoder)));
        }
        return threads;
    }

    /** Polls the queue gauge with a deadline: the semaphore offers no wait for a queue length. */
    private void awaitQueued(int expected) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((queued() != expected) && (System.nanoTime() < deadline)) {
            Thread.onSpinWait();
        }
        assertThat(queued()).isEqualTo(expected);
    }

    private double queued() {
        return registry.get("l2nx.login.password.queued").gauge().value();
    }

    private static void join(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            assertThat(thread.join(Duration.ofSeconds(5))).isTrue();
        }
    }

    private static final class BlockingEncoder implements PasswordEncoder {
        private final CountDownLatch entered = new CountDownLatch(LIMIT);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger maxRunning = new AtomicInteger();
        private final AtomicInteger finished = new AtomicInteger();

        @Override
        public String encode(CharSequence rawPassword) {
            hold();
            return STORED;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            hold();
            return true;
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return true;
        }

        private void hold() {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                running.decrementAndGet();
                finished.incrementAndGet();
            }
        }
    }
}
