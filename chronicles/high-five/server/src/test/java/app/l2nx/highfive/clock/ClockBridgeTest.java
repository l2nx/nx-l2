/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.ManualGameClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class ClockBridgeTest {
    private static final Instant WALL_START = Instant.parse("2026-03-01T12:30:00Z");

    private ClockBridge.Binding previous;
    private ManualGameClock clock;

    @BeforeEach
    void unbind() {
        previous = ClockBridge.swap(null);
        clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);
    }

    @AfterEach
    void restore() {
        ClockBridge.swap(previous);
    }

    @Nested
    class Unbound {
        static Stream<Arguments> accessors() {
            return Stream.of(
                    accessor("clock", ClockBridge::clock),
                    accessor("gameMillis", ClockBridge::gameMillis),
                    accessor("wallMillis", ClockBridge::wallMillis),
                    accessor("wallClock", ClockBridge::wallClock),
                    accessor("calendar", ClockBridge::calendar),
                    accessor("random", ClockBridge::random));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("accessors")
        void accessor_shouldThrow_whenUnbound(String name, ThrowingCallable accessor) {
            assertThatIllegalStateException().isThrownBy(accessor).withMessageContaining("not bound");
        }

        private static Arguments accessor(String name, ThrowingCallable accessor) {
            return Arguments.of(name, accessor);
        }
    }

    @Nested
    class Bound {
        @Test
        void gameMillis_shouldReadTheBoundClock() {
            ClockBridge.bind(clock, ThreadLocalRandom::current);
            clock.advance(Duration.ofSeconds(3));

            assertThat(ClockBridge.gameMillis()).isEqualTo(GameClock.ORIGIN_MILLIS + 3_000);
            assertThat(ClockBridge.wallMillis()).isEqualTo(WALL_START.toEpochMilli() + 3_000);
            assertThat(ClockBridge.clock()).isSameAs(clock);
        }

        @Test
        void calendar_shouldBeTheWallTimeInTheWallZone() {
            ClockBridge.bind(clock, ThreadLocalRandom::current);

            final Calendar actual = ClockBridge.calendar();

            assertThat(actual.getTimeInMillis()).isEqualTo(WALL_START.toEpochMilli());
            assertThat(actual.get(Calendar.HOUR_OF_DAY)).isEqualTo(12);
            assertThat(actual.get(Calendar.MINUTE)).isEqualTo(30);
        }

        @Test
        void random_shouldUseTheBoundGenerator() {
            final RandomGenerator seeded =
                    RandomGeneratorFactory.of("L64X128MixRandom").create(7L);
            final long expected =
                    RandomGeneratorFactory.of("L64X128MixRandom").create(7L).nextLong();
            ClockBridge.bind(clock, () -> seeded);

            assertThat(ClockBridge.random().nextLong()).isEqualTo(expected);
        }
    }

    @Nested
    class BindingBean {
        @Test
        void constructor_shouldBindTheClockAndThreadLocalRandom() {
            new ClockBridgeBinding(clock);

            assertThat(ClockBridge.clock()).isSameAs(clock);
            assertThat(ClockBridge.random()).isSameAs(ThreadLocalRandom.current());
        }
    }
}
