/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WireHandleAllocatorTest {
    private static final Duration QUARANTINE = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock();
    private final WireHandleAllocator allocator = new WireHandleAllocator(clock, QUARANTINE);

    @Nested
    class Allocate {
        @Test
        void allocate_shouldStartAtFirstHandle() {
            assertThat(allocator.allocate()).isEqualTo(WireHandleAllocator.FIRST_HANDLE);
            assertThat(allocator.allocate()).isEqualTo(WireHandleAllocator.FIRST_HANDLE + 1);
        }

        @Test
        void allocate_shouldNotReuseReleasedHandle_whileInQuarantine() {
            final int handle = allocator.allocate();
            allocator.release(handle);
            clock.advance(QUARANTINE.minusMillis(1));

            assertThat(allocator.allocate()).isNotEqualTo(handle);
        }

        @Test
        void allocate_shouldReuseReleasedHandle_afterQuarantine() {
            final int handle = allocator.allocate();
            allocator.release(handle);
            clock.advance(QUARANTINE);

            assertThat(allocator.allocate()).isEqualTo(handle);
        }
    }

    @Nested
    class Release {
        @Test
        void release_shouldIgnoreHandleReleasedTwice() {
            final int handle = allocator.allocate();
            allocator.release(handle);
            allocator.release(handle);
            clock.advance(QUARANTINE);

            assertThat(allocator.allocate()).isEqualTo(handle);
            assertThat(allocator.allocate()).isNotEqualTo(handle);
        }

        @Test
        void release_shouldIgnoreHandleNeverAllocated() {
            allocator.release(WireHandleAllocator.FIRST_HANDLE + 42);

            assertThat(allocator.allocatedCount()).isZero();
        }

        @Test
        void release_shouldCountAllocatedHandles() {
            final int first = allocator.allocate();
            allocator.allocate();
            allocator.release(first);

            assertThat(allocator.allocatedCount()).isEqualTo(1);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
