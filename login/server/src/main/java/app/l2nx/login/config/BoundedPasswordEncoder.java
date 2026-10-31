/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bounds the hashes running at once: each Argon2id hash holds its whole memory cost on the heap, so an unbounded burst
 * needs heap proportional to the core count. {@link #upgradeEncoding} only parses the stored hash and is not bounded.
 */
final class BoundedPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate;
    private final Semaphore permits;
    private final MeterRegistry registry;
    private final Timer wait;

    BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrentHashes, MeterRegistry registry) {
        this.delegate = delegate;
        // Fair, so a burst cannot starve an early caller.
        permits = new Semaphore(maxConcurrentHashes, true);
        this.registry = registry;
        wait = Timer.builder("l2nx.login.password.wait")
                .description("Time a password hash waits for a slot")
                .register(registry);
        Gauge.builder("l2nx.login.password.queued", permits, Semaphore::getQueueLength)
                .description("Password hashes waiting for a slot")
                .baseUnit(BaseUnits.TASKS)
                .register(registry);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bounded(() -> delegate.encode(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return bounded(() -> delegate.matches(rawPassword, encodedPassword));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    /** @throws IllegalStateException when interrupted while waiting, which only an executor shutdown does */
    private <T> T bounded(Supplier<T> hash) {
        final Timer.Sample waiting = Timer.start(registry);
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a password hash slot", e);
        }
        try {
            waiting.stop(wait);
            return hash.get();
        } finally {
            permits.release();
        }
    }
}
