/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.inbox.Inbox;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Inbox<String> inbox = new Inbox<>();
    private final LoopMetrics metrics = new LoopMetrics(registry, "world", inbox);

    @Nested
    class Pass {
        @Test
        void pass_shouldRecordDurationItemsAndLiveTimers() {
            metrics.phase(PassPhase.DEADLINES, 2_000_000);
            metrics.pass(5_000_000, 3, 16, 7, 120);

            assertThat(registry.get("l2nx.engine.pass")
                            .tag("loop", "world")
                            .timer()
                            .count())
                    .isEqualTo(1);
            assertThat(registry.get("l2nx.engine.pass.phase")
                            .tags("loop", "world", "phase", "deadlines")
                            .timer()
                            .totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(2.0);
            assertThat(registry.get("l2nx.engine.pass.items")
                            .tags("loop", "world", "kind", "input")
                            .summary()
                            .totalAmount())
                    .isEqualTo(16.0);
            assertThat(registry.get("l2nx.engine.pass.items")
                            .tags("loop", "world", "kind", "timer")
                            .summary()
                            .totalAmount())
                    .isEqualTo(7.0);
            assertThat(registry.get("l2nx.engine.timers")
                            .tag("loop", "world")
                            .gauge()
                            .value())
                    .isEqualTo(120.0);
        }

        @Test
        void pass_shouldPublishP95AndP99() {
            metrics.pass(1_000_000, 0, 0, 0, 0);

            final ValueAtPercentile[] actual =
                    registry.get("l2nx.engine.pass").timer().takeSnapshot().percentileValues();

            assertThat(actual).extracting(ValueAtPercentile::percentile).containsExactly(0.95, 0.99);
        }
    }

    @Nested
    class InboxDepth {
        @Test
        void inboxDepth_shouldFollowTheInbox() {
            inbox.offer("a");
            inbox.offer("b");

            assertThat(registry.get("l2nx.engine.inbox.depth")
                            .tag("loop", "world")
                            .gauge()
                            .value())
                    .isEqualTo(2.0);
        }
    }

    @Nested
    class Counters {
        @Test
        void counters_shouldCountSystemsBudgetOverdueTriggersAndFailures() {
            final LoopMetrics.SystemMeters movement = metrics.system("player-movement");

            movement.skipped().increment(2);
            metrics.overBudget();
            metrics.overdue(3);
            metrics.overdue(0);
            metrics.triggerDepthExceeded();
            metrics.failure(PassPhase.INBOX);

            assertThat(registry.get("l2nx.engine.system.skipped")
                            .tags("loop", "world", "system", "player-movement")
                            .counter()
                            .count())
                    .isEqualTo(2.0);
            assertThat(registry.get("l2nx.engine.pass.over.budget").counter().count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.engine.timers.overdue").counter().count())
                    .isEqualTo(3.0);
            assertThat(registry.get("l2nx.engine.trigger.depth.exceeded")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(registry.get("l2nx.engine.failures")
                            .tag("phase", "inbox")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldRemoveEveryMeterOfTheLoop() {
            metrics.system("visibility");

            metrics.close();

            assertThat(registry.getMeters()).isEmpty();
        }
    }

    @Nested
    class Millis {
        @Test
        void millis_shouldFormatWithTwoDecimals() {
            assertThat(LoopMetrics.millis(1_234_567)).isEqualTo("1.23");
        }
    }
}
