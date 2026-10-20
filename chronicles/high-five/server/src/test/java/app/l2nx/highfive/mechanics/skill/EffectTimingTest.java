/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.skill;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.GameClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EffectTimingTest {
    private static final long START = GameClock.ORIGIN_MILLIS + 5_000;

    @Nested
    class Remaining {
        @Test
        void remainingMillis_shouldCountDownFromTheAbnormalTime() {
            final EffectTiming timing = new EffectTiming(1200, START);

            assertThat(timing.remainingMillis(START + 2_500)).isEqualTo(1_197_500);
            assertThat(timing.remainingSeconds(START + 2_500)).isEqualTo(1198);
        }

        @Test
        void isExpired_shouldBeTrue_onlyAfterTheDeadline() {
            final EffectTiming timing = new EffectTiming(10, START);

            assertThat(timing.isExpired(START + 9_999)).isFalse();
            assertThat(timing.isExpired(START + 10_000)).isTrue();
        }

        @Test
        void isExpired_shouldBeFalse_whenUntimed() {
            final EffectTiming timing = new EffectTiming(-1, START);

            assertThat(timing.isExpired(START + 86_400_000)).isFalse();
        }
    }

    @Nested
    class Persistence {
        @Test
        void toStored_shouldKeepRemainingTimeAndTickPhase() {
            final EffectTiming timing = new EffectTiming(60, START);

            final StoredEffect actual = timing.toStored(START + 7_300);

            assertThat(actual)
                    .isEqualTo(StoredEffect.builder()
                            .remainingMillis(52_700L)
                            .tickPhaseMillis(7_300)
                            .build());
        }

        @Test
        void toStored_shouldBeNull_whenExpired() {
            final EffectTiming timing = new EffectTiming(5, START);

            assertThat(timing.toStored(START + 5_000)).isNull();
        }

        @Test
        void toStored_shouldHaveNoRemaining_whenUntimed() {
            final EffectTiming timing = new EffectTiming(-1, START);

            assertThat(timing.toStored(START + 4_000))
                    .isEqualTo(StoredEffect.builder().tickPhaseMillis(4_000).build());
        }

        @Test
        void restore_shouldContinueWithTheStoredRemainingTime_whenRestoredLater() {
            final StoredEffect stored = new EffectTiming(60, START).toStored(START + 7_300);
            final long restoredAt = GameClock.ORIGIN_MILLIS + 1_000;
            final EffectTiming restored = new EffectTiming(60, restoredAt);

            restored.restore(stored, restoredAt);

            assertThat(restored.remainingMillis(restoredAt)).isEqualTo(52_700);
            assertThat(restored.remainingSeconds(restoredAt)).isEqualTo(53);
            assertThat(restored.abnormalTime()).isEqualTo(53);
            assertThat(restored.isExpired(restoredAt + 52_699)).isFalse();
            assertThat(restored.isExpired(restoredAt + 52_700)).isTrue();
        }
    }

    @Nested
    class Ticks {
        @Test
        void firstTickDelay_shouldBeOnePeriod_whenFresh() {
            final EffectTiming timing = new EffectTiming(30, START);

            assertThat(timing.firstTickDelay(START, 3_000)).isEqualTo(3_000);
        }

        @Test
        void firstTickDelay_shouldKeepThePhase_whenRestored() {
            final long restoredAt = GameClock.ORIGIN_MILLIS;
            final EffectTiming restored = new EffectTiming(30, restoredAt);

            restored.restore(
                    StoredEffect.builder()
                            .remainingMillis(20_000L)
                            .tickPhaseMillis(7_300)
                            .build(),
                    restoredAt);

            assertThat(restored.firstTickDelay(restoredAt, 3_000)).isEqualTo(1_700);
            assertThat(restored.firstTickDelay(restoredAt, 5_000)).isEqualTo(2_700);
        }
    }
}
