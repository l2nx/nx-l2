/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerStatsTest {
    @Nested
    class Construct {
        @Test
        void serverStats_shouldKeepSortedReadOnlyCopies_whenTheSourcesChange() {
            final Map<String, LoopStats> loops = new HashMap<>(Map.of("world-1", loop(), "world-0", loop()));
            final Map<String, Double> packets = new HashMap<>(Map.of("Say2", 0.2, "MoveBackwardToLocation", 0.1));
            final Map<String, ProcessStats> processes =
                    new HashMap<>(Map.of("login", ProcessStats.builder().build()));

            final ServerStats actual = ServerStats.builder()
                    .loops(loops)
                    .legacyPacketMeanMillis(packets)
                    .processes(processes)
                    .build();
            loops.clear();
            packets.clear();
            processes.clear();

            assertThat(actual.loops().keySet()).containsExactly("world-0", "world-1");
            assertThat(actual.legacyPacketMeanMillis().keySet()).containsExactly("MoveBackwardToLocation", "Say2");
            assertThat(actual.processes()).containsOnlyKeys("login");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> actual.loops().remove("world-0"));
        }

        @Test
        void serverStats_shouldKeepAbsentMapsNull() {
            final ServerStats actual = ServerStats.builder().build();

            assertThat(actual.loops()).isNull();
            assertThat(actual.legacyPacketMeanMillis()).isNull();
            assertThat(actual.processes()).isNull();
        }

        @Test
        void loopStats_shouldKeepASortedReadOnlyCopyOfThePhases_whenTheSourceChanges() {
            final Map<String, Double> phases = new HashMap<>(Map.of("timers", 1.0, "inbox", 2.0));

            final LoopStats actual = LoopStats.builder().phaseMeanMillis(phases).build();
            phases.clear();

            assertThat(actual.phaseMeanMillis().keySet()).containsExactly("inbox", "timers");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> actual.phaseMeanMillis().clear());
        }
    }

    @Nested
    class Worst {
        @Test
        void timedLoops_shouldLeaveOutUntimedLoops() {
            final ServerStats actual = stats(Map.of("world-0", timed(10.0, 1, 100), "session-drain", untimed()));

            assertThat(actual.timedLoops()).containsOnlyKeys("world-0");
        }

        @Test
        void worst_shouldTakeTheHighestValuesOverTheTimedLoops() {
            final ServerStats actual = stats(
                    Map.of("world-0", timed(10.0, 4, 100), "world-1", timed(30.0, 1, 100), "session-drain", untimed()));

            assertThat(actual.worstPassP99Millis()).isEqualTo(30.0);
            assertThat(actual.worstOverBudgetShare()).isEqualTo(0.04);
        }

        @Test
        void worst_shouldBeNull_whenATimedLoopHasNoPass() {
            final ServerStats actual = stats(Map.of("world-0", timed(10.0, 1, 100), "world-1", timed(null, 0, 0)));

            assertThat(actual.worstPassP99Millis()).isNull();
            assertThat(actual.worstOverBudgetShare()).isNull();
        }

        @Test
        void worstPassP99Millis_shouldBeNull_whenATimedLoopHasNoP99() {
            final ServerStats actual = stats(Map.of("world-0", timed(10.0, 1, 100), "world-1", timed(null, 1, 100)));

            assertThat(actual.worstPassP99Millis()).isNull();
            assertThat(actual.worstOverBudgetShare()).isEqualTo(0.01);
        }

        @Test
        void worst_shouldBeNull_whenThereIsNoTimedLoop() {
            final ServerStats actual = stats(Map.of("session-drain", untimed()));

            assertThat(actual.timedLoops()).isEmpty();
            assertThat(actual.worstPassP99Millis()).isNull();
            assertThat(actual.worstOverBudgetShare()).isNull();
            assertThat(ServerStats.builder().build().timedLoops()).isEmpty();
        }

        private static ServerStats stats(Map<String, LoopStats> loops) {
            return ServerStats.builder().loops(loops).build();
        }

        private static LoopStats timed(Double passP99Millis, long overBudget, long passes) {
            return loop().toBuilder()
                    .passes(passes)
                    .overBudget(overBudget)
                    .passP99Millis(passP99Millis)
                    .build();
        }

        private static LoopStats untimed() {
            return loop().toBuilder().untimed(true).failures(3).build();
        }
    }

    private static LoopStats loop() {
        return LoopStats.builder().phaseMeanMillis(Map.of()).build();
    }
}
