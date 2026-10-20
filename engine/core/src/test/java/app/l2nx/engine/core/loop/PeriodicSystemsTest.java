/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.periodic.PeriodicSystem;
import app.l2nx.engine.core.loop.periodic.TestSystem;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PeriodicSystemsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Loop loop = TestLoops.loop("test", TestLoops.clock(), registry);
    private final List<String> log = new ArrayList<>();

    @Nested
    class RunPass {
        @Test
        void runPass_shouldRunASystemOnItsCadenceAndSkipMissedRunsWithoutABurst() {
            loop.addSystem(new TestSystem("movement", Duration.ofMillis(50), log));

            for (long now : new long[] {10, 50, 60, 170, 200}) {
                loop.runPass(T0 + now);
            }

            assertThat(log).containsExactly("movement@50", "movement@170", "movement@200");
            assertThat(registry.get("l2nx.engine.system.skipped")
                            .tags("loop", "test", "system", "movement")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.engine.system")
                            .tags("loop", "test", "system", "movement")
                            .timer()
                            .count())
                    .isEqualTo(3);
        }

        @Test
        void runPass_shouldRunSystemsInRegistrationOrder() {
            loop.addSystem(new TestSystem("b", Duration.ofMillis(50), log));
            loop.addSystem(new TestSystem("a", Duration.ofMillis(50), log));

            loop.runPass(T0 + 50);

            assertThat(log).containsExactly("b@50", "a@50");
        }

        @Test
        void runPass_shouldCountAFailingSystemAndRunTheNextOne() {
            loop.addSystem(new FailingSystem());
            loop.addSystem(new TestSystem("visibility", Duration.ofMillis(50), log));

            loop.runPass(T0 + 50);

            assertThat(log).containsExactly("visibility@50");
            assertThat(registry.get("l2nx.engine.failures")
                            .tags("loop", "test", "phase", "systems")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    class NextPassAt {
        @Test
        void nextPassAt_shouldWaitForTheNextDueSystem() {
            loop.addSystem(new TestSystem("visibility", Duration.ofMillis(100), log));
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 100);

            loop.runPass(T0 + 100);
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 200);
        }

        @Test
        void nextPassAt_shouldBeTheDueTime_whenTheCadenceIsUnderPassMin() {
            loop.addSystem(new TestSystem("movement", Duration.ofMillis(2), log));

            loop.runPass(T0 + 2);

            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 4);
        }
    }

    @Nested
    class AddSystem {
        @Test
        void addSystem_shouldReject_whenTheNameIsTaken() {
            loop.addSystem(new TestSystem("movement", Duration.ofMillis(50), log));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> loop.addSystem(new TestSystem("movement", Duration.ofMillis(100), log)));
        }

        @Test
        void addSystem_shouldReject_whenTheCadenceIsUnderOneMillisecond() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> loop.addSystem(new TestSystem("ai", Duration.ofNanos(10), log)));
        }

        // A replay sets the clock to the recorded pass time, so a live clock reading taken inside the pass differs.
        @Test
        void addSystem_shouldScheduleTheFirstRunFromThePassTime_whenCalledInAPass() {
            final ManualGameClock clock = TestLoops.clock();
            final Loop timed = TestLoops.loop("timed", clock, registry);
            timed.offer(l -> {
                clock.advance(Duration.ofMillis(3));
                l.addSystem(new TestSystem("visibility", Duration.ofMillis(50), log));
            });

            timed.runPass(T0 + 10);

            assertThat(timed.nextPassAt(timed.getPassTime())).isEqualTo(T0 + 60);
        }

        @Test
        void addSystem_shouldScheduleTheFirstRunFromTheLoopStart_whenCalledBeforeTheFirstPass() {
            final ManualGameClock clock = TestLoops.clock();
            final Loop timed = TestLoops.loop("timed", clock, registry);
            clock.advance(Duration.ofMillis(7));

            timed.addSystem(new TestSystem("visibility", Duration.ofMillis(50), log));

            assertThat(timed.nextPassAt(timed.getPassTime())).isEqualTo(T0 + 50);
        }
    }

    private static final class FailingSystem implements PeriodicSystem {
        @Override
        public String name() {
            return "failing";
        }

        @Override
        public Duration cadence() {
            return Duration.ofMillis(50);
        }

        @Override
        public void run(Loop loop, long now) {
            throw new IllegalStateException("failing system");
        }
    }
}
