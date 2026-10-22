/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.periodic.TestSystem;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopStartupTest {
    private static final Duration LOAD = Duration.ofSeconds(30);

    private final ManualGameClock clock = TestLoops.clock();
    private final MockClock meterClock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, meterClock);
    private final Loop loop = TestLoops.loop("test", clock, registry);
    private final List<String> ran = new ArrayList<>();

    private void load() {
        clock.advance(LOAD);
        meterClock.add(LOAD);
    }

    private double overBudget() {
        return registry.get("l2nx.engine.pass.over.budget")
                .tag("loop", "test")
                .counter()
                .count();
    }

    private Timer passTimer() {
        return registry.get("l2nx.engine.pass").tag("loop", "test").timer();
    }

    private Timer inboxPhaseTimer() {
        return registry.get("l2nx.engine.pass.phase")
                .tags("loop", "test", "phase", "inbox")
                .timer();
    }

    @Nested
    class OfferStartup {
        @Test
        void offerStartup_shouldReadClockLive_duringStartupWork() {
            final List<Long> game = new ArrayList<>();
            final List<Long> wall = new ArrayList<>();
            loop.offerStartup(l -> {
                game.add(l.gameNow());
                wall.add(l.wallNow());
                load();
                game.add(l.gameNow());
                wall.add(l.wallNow());
            });

            loop.runPass(clock.gameMillis());

            assertThat(game.get(1) - game.get(0)).isEqualTo(LOAD.toMillis());
            assertThat(wall.get(1) - wall.get(0)).isEqualTo(LOAD.toMillis());
        }

        @Test
        void offerStartup_shouldReadPassTimeAgain_afterStartupWork() {
            final List<Long> seen = new ArrayList<>();
            loop.offerStartup(l -> load());
            loop.offer(l -> seen.add(l.gameNow()));
            final long passTime = clock.gameMillis();

            loop.runPass(passTime);

            assertThat(seen).containsExactly(passTime);
        }

        @Test
        void offerStartup_shouldArmTimersFromLiveTime_notFromLoadStart() {
            loop.offerStartup(l -> {
                load();
                l.schedule(() -> ran.add("task"), 10, TimeUnit.SECONDS);
            });
            loop.runPass(clock.gameMillis());

            clock.advance(Duration.ofSeconds(9));
            loop.runPass(clock.gameMillis());
            final List<String> beforeDue = List.copyOf(ran);
            clock.advance(Duration.ofSeconds(1));
            loop.runPass(clock.gameMillis());

            assertThat(beforeDue).isEmpty();
            assertThat(ran).containsExactly("task");
        }

        @Test
        void offerStartup_shouldStartSystemsFromLiveTime_notFromLoadStart() {
            loop.offerStartup(l -> {
                load();
                l.addSystem(new TestSystem("movement", Duration.ofSeconds(10), ran));
            });
            loop.runPass(clock.gameMillis());

            clock.advance(Duration.ofSeconds(9));
            loop.runPass(clock.gameMillis());

            assertThat(ran).isEmpty();
        }

        @Test
        void offerStartup_shouldNeitherTimeNorCountOverBudget() {
            loop.offerStartup(l -> load());

            loop.runPass(clock.gameMillis());

            assertThat(overBudget()).isZero();
            assertThat(passTimer().count()).isZero();
            assertThat(inboxPhaseTimer().count()).isZero();
        }

        @Test
        void offerStartup_shouldTimeAndCountLaterPassesOverBudget() {
            loop.offerStartup(l -> load());
            loop.runPass(clock.gameMillis());
            loop.offer(l -> load());

            loop.runPass(clock.gameMillis());

            assertThat(overBudget()).isEqualTo(1.0);
            assertThat(passTimer().count()).isEqualTo(1);
            assertThat(passTimer().totalTime(TimeUnit.SECONDS)).isEqualTo(LOAD.toSeconds());
            assertThat(inboxPhaseTimer().totalTime(TimeUnit.SECONDS)).isEqualTo(LOAD.toSeconds());
        }

        @Test
        void offerStartup_shouldEndTheStartupPass_whenWorkFails() {
            final List<Long> seen = new ArrayList<>();
            loop.offerStartup(l -> {
                load();
                throw new IllegalStateException("broken data file");
            });
            loop.offer(l -> seen.add(l.gameNow()));
            final long passTime = clock.gameMillis();

            loop.runPass(passTime);

            assertThat(seen).containsExactly(passTime);
            assertThat(overBudget()).isZero();
        }

        @Test
        void offerStartup_shouldRefuse_whenLoopStopped() {
            loop.shutdown();

            assertThat(loop.offerStartup(l -> ran.add("late"))).isFalse();
        }
    }
}
