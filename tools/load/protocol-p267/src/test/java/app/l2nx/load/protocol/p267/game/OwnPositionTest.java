/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.model.Position;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OwnPositionTest {
    private static final Position ORIGIN = Position.builder().x(0).y(0).z(-100).build();
    private static final Position TARGET =
            Position.builder().x(240).y(0).z(-100).build();
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    private final OwnPosition own = new OwnPosition(ORIGIN);

    @Nested
    class Current {
        @Test
        void current_shouldStayAtTheStart_whenNothingMoves() {
            assertThat(own.current(SECOND)).isEqualTo(ORIGIN);
        }

        @Test
        void current_shouldRunAlongTheLine_whileMoving() {
            own.moveTo(ORIGIN, TARGET, 0);

            assertThat(own.current(SECOND)).isEqualTo(ORIGIN.toBuilder().x(120).build());
        }

        @Test
        void current_shouldStopAtTheTarget_whenTheTimeOfTheRunPassed() {
            own.moveTo(ORIGIN, TARGET, 0);

            assertThat(own.current(10 * SECOND)).isEqualTo(TARGET);
            assertThat(own.current(20 * SECOND)).isEqualTo(TARGET);
        }

        @Test
        void current_shouldCarryOnFromAServerPosition_whenSyncedWhileMoving() {
            own.moveTo(ORIGIN, TARGET, 0);

            own.sync(ORIGIN.toBuilder().x(100).build(), SECOND);

            assertThat(own.current(SECOND + SECOND / 2))
                    .isEqualTo(ORIGIN.toBuilder().x(160).build());
        }

        @Test
        void current_shouldStayWhereTheServerStoppedIt() {
            own.moveTo(ORIGIN, TARGET, 0);
            final Position stopped = ORIGIN.toBuilder().x(50).build();

            own.stop(stopped);

            assertThat(own.current(10 * SECOND)).isEqualTo(stopped);
        }
    }
}
