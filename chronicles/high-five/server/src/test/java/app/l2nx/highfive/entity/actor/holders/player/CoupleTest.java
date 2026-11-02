/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.managers.CoupleManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.world.model.CoupleRow;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class CoupleTest {
    private static final Instant ENGAGED_AT = Instant.parse("2026-01-01T10:00:00Z");

    private static Couple engaged() {
        return new Couple(new CoupleRow(7L, 268_480_001, 268_480_002, ENGAGED_AT, null));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Couple> call) {
            final Couple couple = engaged();

            assertThatThrownBy(() -> call.accept(couple))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Couple>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId (accessor)", Couple::getId),
                    Named.of("getPlayer1Id (accessor)", Couple::getPlayer1Id),
                    Named.of("getPlayer2Id (accessor)", Couple::getPlayer2Id),
                    Named.of("getAffiancedDate (accessor)", Couple::getAffiancedDate),
                    Named.of("getWeddingDate (accessor)", Couple::getWeddingDate),
                    Named.of("getMaried", Couple::getMaried),
                    Named.of("marry", Couple::marry),
                    Named.of("divorce", Couple::divorce));
        }
    }

    @Nested
    class Marry {
        @Test
        void marry_shouldStoreTheWeddingAndMarkTheCouple() {
            final Couple couple = engaged();
            final CoupleManager manager = mock(CoupleManager.class);

            final boolean actual;
            try (MockedStatic<CoupleManager> couples = mockStatic(CoupleManager.class)) {
                couples.when(CoupleManager::getInstance).thenReturn(manager);
                actual = WorldLoopExtension.callInPass(() -> {
                    final boolean before = couple.getMaried();
                    couple.marry();
                    return !before && couple.getMaried();
                });
            }

            verify(manager).updateMarried(eq(7L), anyLong());
            assertThat(actual).isTrue();
        }
    }

    @Nested
    class Dates {
        @Test
        void getWeddingDate_shouldHandOutACopy() {
            final Couple couple = engaged();

            final List<Long> actual = WorldLoopExtension.callInPass(() -> {
                couple.getWeddingDate().add(Calendar.YEAR, 1);
                return List.of(
                        couple.getWeddingDate().getTimeInMillis(),
                        couple.getAffiancedDate().getTimeInMillis());
            });

            assertThat(actual).containsExactly(ENGAGED_AT.toEpochMilli(), ENGAGED_AT.toEpochMilli());
        }
    }
}
