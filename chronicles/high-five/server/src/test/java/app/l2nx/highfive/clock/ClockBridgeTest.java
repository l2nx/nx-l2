/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
    private static final Instant LOOP_WALL_START = Instant.parse("2026-04-01T08:00:00Z");

    private ClockBridge.TimeSource previous;
    private ManualGameClock clock;
    private ManualGameClock loopClock;
    private Loop loop;

    @BeforeEach
    void unbind() {
        previous = ClockBridge.swap(null);
        clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);
        loopClock = new ManualGameClock(LOOP_WALL_START, ZoneOffset.UTC);
        loop = new Loop(
                "world", loopClock, 7L, EngineProperties.defaults(), new SimpleMeterRegistry(), PassRecorder.NOOP);
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
    class GameMillis {
        @Test
        void gameMillis_shouldReturnPassTime_onLoop() {
            ClockBridge.bindLoops(clock);
            loopClock.advance(Duration.ofSeconds(3));

            final Long actual = inPass(() -> {
                loopClock.advance(Duration.ofSeconds(1));
                return ClockBridge.gameMillis();
            });

            assertThat(actual).isEqualTo(GameClock.ORIGIN_MILLIS + 3_000);
        }

        @Test
        void gameMillis_shouldThrow_offLoop() {
            ClockBridge.bindLoops(clock);

            assertThatIllegalStateException()
                    .isThrownBy(ClockBridge::gameMillis)
                    .withMessageContaining("off a loop thread");
        }

        @Test
        void gameMillis_shouldReadTheBoundClock_onLoopWhenFixed() {
            ClockBridge.bind(clock, ThreadLocalRandom::current);
            clock.advance(Duration.ofSeconds(2));

            assertThat(inPass(ClockBridge::gameMillis)).isEqualTo(GameClock.ORIGIN_MILLIS + 2_000);
        }
    }

    @Nested
    class WallMillis {
        @Test
        void wallMillis_shouldReturnPassWallTime_onLoop() {
            ClockBridge.bindLoops(clock);

            final Long actual = inPass(() -> {
                loopClock.advance(Duration.ofSeconds(1));
                return ClockBridge.wallMillis();
            });

            assertThat(actual).isEqualTo(LOOP_WALL_START.toEpochMilli());
        }

        @Test
        void wallMillis_shouldReadClock_offLoop() {
            ClockBridge.bindLoops(clock);
            clock.advance(Duration.ofSeconds(3));

            assertThat(ClockBridge.wallMillis()).isEqualTo(WALL_START.toEpochMilli() + 3_000);
        }
    }

    @Nested
    class WallClock {
        @Test
        void wallClock_shouldReadPassWallTimeInTheWallZone_onLoop() {
            ClockBridge.bindLoops(clock);

            final Clock actual = inPass(() -> {
                final Clock wall = ClockBridge.wallClock();
                loopClock.advance(Duration.ofSeconds(1));
                return Clock.fixed(wall.instant(), wall.getZone());
            });

            assertThat(actual.instant()).isEqualTo(LOOP_WALL_START);
            assertThat(actual.getZone()).isEqualTo(ZoneOffset.UTC);
        }

        @Test
        void wallClock_shouldReadClock_offLoop() {
            ClockBridge.bindLoops(clock);
            clock.advance(Duration.ofSeconds(3));

            assertThat(ClockBridge.wallClock().instant()).isEqualTo(WALL_START.plusSeconds(3));
        }

        @Test
        void calendar_shouldBeTheWallTime_offLoop() {
            ClockBridge.bindLoops(clock);

            assertThat(ClockBridge.calendar().getTimeInMillis()).isEqualTo(WALL_START.toEpochMilli());
        }
    }

    @Nested
    class RandomOf {
        @Test
        void random_shouldReturnLoopGenerator_onLoop() {
            ClockBridge.bindLoops(clock);

            final RandomGenerator actual = inPass(ClockBridge::random);

            assertThat(actual).isSameAs(inPass(loop::random));
        }

        @Test
        void random_shouldThrow_offLoop() {
            ClockBridge.bindLoops(clock);

            assertThatIllegalStateException()
                    .isThrownBy(ClockBridge::random)
                    .withMessageContaining("off a loop thread");
        }
    }

    @Nested
    class ClockOf {
        @Test
        void clock_shouldReadPassTime_onLoop() {
            ClockBridge.bindLoops(clock);
            loopClock.advance(Duration.ofSeconds(3));

            final long[] actual = inPass(() -> {
                final GameClock passClock = ClockBridge.clock();
                loopClock.advance(Duration.ofSeconds(1));
                return new long[] {
                    passClock.gameMillis(), passClock.wallMillis(), passClock.wallMillisAt(GameClock.ORIGIN_MILLIS)
                };
            });

            assertThat(actual)
                    .containsExactly(
                            GameClock.ORIGIN_MILLIS + 3_000,
                            LOOP_WALL_START.toEpochMilli() + 3_000,
                            LOOP_WALL_START.toEpochMilli());
        }

        @Test
        void clock_shouldThrow_offLoop() {
            ClockBridge.bindLoops(clock);

            assertThatIllegalStateException().isThrownBy(ClockBridge::clock).withMessageContaining("off a loop thread");
        }
    }

    @Nested
    class BindingBean {
        @Test
        void constructor_shouldAnswerFromTheLoop_onLoop() {
            new ClockBridgeBinding(clock);
            loopClock.advance(Duration.ofSeconds(3));

            final Long actual = inPass(ClockBridge::gameMillis);

            assertThat(actual).isEqualTo(GameClock.ORIGIN_MILLIS + 3_000);
        }

        @Test
        void constructor_shouldAnswerWallTimeOnly_offLoop() {
            new ClockBridgeBinding(clock);

            assertThat(ClockBridge.wallMillis()).isEqualTo(WALL_START.toEpochMilli());
            assertThatIllegalStateException()
                    .isThrownBy(ClockBridge::gameMillis)
                    .withMessageContaining("off a loop thread");
            assertThatIllegalStateException()
                    .isThrownBy(ClockBridge::random)
                    .withMessageContaining("off a loop thread");
        }
    }

    private <T> T inPass(Supplier<T> body) {
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        loop.execute(() -> {
            try {
                result.set(body.get());
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        loop.runPass(loopClock.gameMillis());
        if (failure.get() != null) {
            throw failure.get();
        }
        assertThat(result.get()).as("result of the pass").isNotNull();
        return result.get();
    }
}
