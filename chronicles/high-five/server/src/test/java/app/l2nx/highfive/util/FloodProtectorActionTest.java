/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.network.GameClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class FloodProtectorActionTest {
    private final FloodProtectorSettings settings = new FloodProtectorSettings("UseItemFloodProtector");

    @Nested
    class Constructor {
        @Test
        void constructor_shouldNotReadGameTime_offLoop() {
            ClockBridgeExtension.runBoundToLoops(() -> assertThatCode(
                            () -> new FloodProtectorAction(mock(GameClient.class), settings))
                    .doesNotThrowAnyException());
        }
    }

    @Nested
    class CanPerformAction {
        @Test
        void canPerformAction_shouldAllowTheFirstAction() {
            settings.setProtectionInterval(10);
            final FloodProtectorAction action = new FloodProtectorAction(mock(GameClient.class), settings);

            assertThat(action.canPerformAction()).isTrue();
        }

        @Test
        void canPerformAction_shouldRefuse_whenTheIntervalHasNotPassed() {
            settings.setProtectionInterval(10);
            final FloodProtectorAction action = new FloodProtectorAction(mock(GameClient.class), settings);
            action.canPerformAction();

            assertThat(action.canPerformAction()).isFalse();
        }
    }

    @Nested
    class PunishmentExpiration {
        @Test
        void punishmentExpiration_shouldAddTimeToNow_whenTimeIsPositive() {
            final long actual = FloodProtectorAction.punishmentExpiration(60_000);

            assertThat(actual).isEqualTo(ClockBridgeExtension.WALL_START.toEpochMilli() + 60_000);
        }

        @ParameterizedTest(name = "{0} ms")
        @ValueSource(longs = {0, -1})
        void punishmentExpiration_shouldBePermanent_whenTimeIsNotPositive(long punishmentTime) {
            final long actual = FloodProtectorAction.punishmentExpiration(punishmentTime);

            assertThat(actual).isZero();
        }
    }
}
