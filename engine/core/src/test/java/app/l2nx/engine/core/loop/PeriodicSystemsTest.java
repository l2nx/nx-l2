/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static app.l2nx.engine.core.loop.TestLoops.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.engine.core.clock.ManualGameClock;
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
            loop.addSystem("movement", Duration.ofMillis(50), new TestSystem("movement", log));

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
            loop.addSystem("b", Duration.ofMillis(50), new TestSystem("b", log));
            loop.addSystem("a", Duration.ofMillis(50), new TestSystem("a", log));

            loop.runPass(T0 + 50);

            assertThat(log).containsExactly("b@50", "a@50");
        }

        @Test
        void runPass_shouldCountAFailingSystemAndRunTheNextOne() {
            loop.addSystem("failing", Duration.ofMillis(50), (l, now) -> {
                throw new IllegalStateException("failing system");
            });
            loop.addSystem("visibility", Duration.ofMillis(50), new TestSystem("visibility", log));

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
            loop.addSystem("visibility", Duration.ofMillis(100), new TestSystem("visibility", log));
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 100);

            loop.runPass(T0 + 100);
            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 200);
        }

        @Test
        void nextPassAt_shouldBeTheDueTime_whenTheCadenceIsUnderPassMin() {
            loop.addSystem("movement", Duration.ofMillis(2), new TestSystem("movement", log));

            loop.runPass(T0 + 2);

            assertThat(loop.nextPassAt(loop.getPassTime())).isEqualTo(T0 + 4);
        }
    }

    @Nested
    class AddSystem {
        @Test
        void addSystem_shouldReject_whenTheNameIsTaken() {
            loop.addSystem("movement", Duration.ofMillis(50), new TestSystem("movement", log));

            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () -> loop.addSystem("movement", Duration.ofMillis(100), new TestSystem("movement", log)));
        }

        @Test
        void addSystem_shouldReject_whenTheCadenceIsUnderOneMillisecond() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> loop.addSystem("ai", Duration.ofNanos(10), new TestSystem("ai", log)));
        }

        // The clock moves inside the pass; the first run must follow the pass time to stay deterministic.
        @Test
        void addSystem_shouldScheduleTheFirstRunFromThePassTime_whenCalledInAPass() {
            final ManualGameClock clock = TestLoops.clock();
            final Loop timed = TestLoops.loop("timed", clock, registry);
            timed.offer(l -> {
                clock.advance(Duration.ofMillis(3));
                l.addSystem("visibility", Duration.ofMillis(50), new TestSystem("visibility", log));
            });

            timed.runPass(T0 + 10);

            assertThat(timed.nextPassAt(timed.getPassTime())).isEqualTo(T0 + 60);
        }

        @Test
        void addSystem_shouldScheduleTheFirstRunFromTheLoopStart_whenCalledBeforeTheFirstPass() {
            final ManualGameClock clock = TestLoops.clock();
            final Loop timed = TestLoops.loop("timed", clock, registry);
            clock.advance(Duration.ofMillis(7));

            timed.addSystem("visibility", Duration.ofMillis(50), new TestSystem("visibility", log));

            assertThat(timed.nextPassAt(timed.getPassTime())).isEqualTo(T0 + 50);
        }
    }
}
