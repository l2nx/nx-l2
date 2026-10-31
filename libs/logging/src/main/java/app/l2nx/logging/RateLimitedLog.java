/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.logging;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Keys must be message categories, never client addresses: buckets are never evicted.
 */
public final class RateLimitedLog {
    private final Duration interval;
    private final TimeMeter time;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitedLog(Duration interval, LongSupplier nanoClock) {
        this.interval = interval;
        time = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return nanoClock.getAsLong();
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
    }

    public static RateLimitedLog everyTenSeconds() {
        return new RateLimitedLog(Duration.ofSeconds(10), System::nanoTime);
    }

    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(1).refillIntervally(1, interval))
                .withCustomTimePrecision(time)
                .build();
    }
}
