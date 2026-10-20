/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimitedLogTest {
    @Test
    void tryAcquire_shouldAllowOncePerIntervalPerKey() {
        final AtomicLong now = new AtomicLong();
        final RateLimitedLog log = new RateLimitedLog(Duration.ofSeconds(10), now::get);

        assertThat(log.tryAcquire("a")).isTrue();
        assertThat(log.tryAcquire("a")).isFalse();
        assertThat(log.tryAcquire("b")).isTrue();
        now.addAndGet(Duration.ofSeconds(10).toNanos());
        assertThat(log.tryAcquire("a")).isTrue();
    }
}
