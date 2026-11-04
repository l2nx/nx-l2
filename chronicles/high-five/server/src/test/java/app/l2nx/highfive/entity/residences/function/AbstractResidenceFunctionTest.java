/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class AbstractResidenceFunctionTest {
    private static final int TYPE = 3;
    private static final int LEASE = 1_000;
    private static final long RATE = 86_400_000L;

    private final ItemContainer warehouse = mock(ItemContainer.class);
    private final Item adena = mock(Item.class);

    static final class TestFunction extends AbstractResidenceFunction {
        private final boolean owned;
        private final ItemContainer warehouse;
        final List<ResidenceFunction> saved = new ArrayList<>();
        int removals;
        int failingSaves;
        int saveAttempts;
        boolean failingRemoval;

        TestFunction(boolean owned, ItemContainer warehouse, long endTime, boolean fromWarehouse) {
            super(TYPE, 1, LEASE, RATE, endTime);
            this.owned = owned;
            this.warehouse = warehouse;
            startFee(fromWarehouse);
        }

        @Override
        protected int residenceId() {
            return 9;
        }

        @Override
        protected boolean isOwned() {
            return owned;
        }

        @Override
        protected ItemContainer ownerWarehouse() {
            return warehouse;
        }

        @Override
        protected void save(ResidenceFunction function) {
            saveAttempts++;
            if (failingSaves > 0) {
                failingSaves--;
                throw new DataAccessResourceFailureException("save failed");
            }

            saved.add(function);
        }

        @Override
        protected void removeUnpaid() {
            removals++;
            if (failingRemoval) {
                throw new DataAccessResourceFailureException("delete failed");
            }
        }
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AbstractResidenceFunction> call) {
            final TestFunction function = new TestFunction(false, null, 0, false);

            assertThatThrownBy(() -> call.accept(function))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AbstractResidenceFunction>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getType (accessor)", AbstractResidenceFunction::getType),
                    Named.of("getLevel", AbstractResidenceFunction::getLevel),
                    Named.of("getLease", AbstractResidenceFunction::getLease),
                    Named.of("getRate", AbstractResidenceFunction::getRate),
                    Named.of("getEndTime", AbstractResidenceFunction::getEndTime),
                    Named.of("change", f -> f.change(2, 2)));
        }
    }

    @Nested
    class StartFee {
        @Test
        void startFee_shouldScheduleTheFee_whenTheResidenceHasAnOwner(Loop world) {
            final int actual = WorldLoopExtension.callInPass(() -> {
                final int base = world.timers(TimeBase.GAME).size();
                new TestFunction(true, warehouse, 0, true);
                return world.timers(TimeBase.GAME).size() - base;
            });

            assertThat(actual).isEqualTo(1);
        }

        @Test
        void startFee_shouldScheduleNoFee_whenTheResidenceHasNoOwner(Loop world) {
            final int actual = WorldLoopExtension.callInPass(() -> {
                final int base = world.timers(TimeBase.GAME).size();
                new TestFunction(false, warehouse, 0, true);
                return world.timers(TimeBase.GAME).size() - base;
            });

            assertThat(actual).isZero();
        }
    }

    @Nested
    class ChargeFee {
        @Test
        void chargeFee_shouldTakeTheLeaseRenewAndStore_whenTheWarehousePays() {
            whenTheWarehousePays();
            final TestFunction function =
                    WorldLoopExtension.callInPass(() -> new TestFunction(true, warehouse, 0, true));

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            WorldLoopExtension.passAfter(Duration.ofMillis(RATE));

            verify(warehouse, times(2)).destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null);
            assertThat(function.saved).hasSize(2);
            assertThat(function.saved.getFirst().endTime()).isAfter(ClockBridgeExtension.WALL_START);
            assertThat(function.removals).isZero();
        }

        @Test
        void chargeFee_shouldRenewForFree_whenAnUpgradeRenewsFirst() {
            final TestFunction function =
                    WorldLoopExtension.callInPass(() -> new TestFunction(true, warehouse, -1, true));

            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            assertThat(function.saved).hasSize(1);
            assertThat(function.removals).isZero();
            verify(warehouse, never()).destroyItemByItemId(any(), anyInt(), anyLong(), any(), any());
        }

        @Test
        void chargeFee_shouldKeepTheRenewalInMemoryWithoutRetrying_whenTheSaveFails() {
            whenTheWarehousePays();
            final TestFunction function = WorldLoopExtension.callInPass(() -> {
                final TestFunction created = new TestFunction(true, warehouse, 0, true);
                created.failingSaves = 1;
                return created;
            });

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            WorldLoopExtension.passAfter(Duration.ofMinutes(5));

            verify(warehouse, times(1)).destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null);
            assertThat(function.saveAttempts).isOne();
            assertThat(WorldLoopExtension.callInPass(function::getEndTime))
                    .isGreaterThan(ClockBridgeExtension.WALL_START.toEpochMilli());
        }

        @Test
        void chargeFee_shouldStoreTheNewerEndTimeOnTheNextCharge_whenTheSaveFailed() {
            whenTheWarehousePays();
            final TestFunction function = WorldLoopExtension.callInPass(() -> {
                final TestFunction created = new TestFunction(true, warehouse, 0, true);
                created.failingSaves = 1;
                return created;
            });
            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            WorldLoopExtension.passAfter(Duration.ofMillis(RATE));

            verify(warehouse, times(2)).destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null);
            assertThat(function.saved)
                    .singleElement()
                    .extracting(ResidenceFunction::endTime)
                    .isEqualTo(Instant.ofEpochMilli(WorldLoopExtension.callInPass(function::getEndTime)));
        }

        @Test
        void chargeFee_shouldRetryTheCharge_whenTakingTheFeeFailsWithTheAdenaStillHeld() {
            when(warehouse.getAdena()).thenReturn(5_000L);
            when(warehouse.destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null))
                    .thenThrow(new IllegalStateException("warehouse failed"))
                    .thenReturn(adena);
            final TestFunction function =
                    WorldLoopExtension.callInPass(() -> new TestFunction(true, warehouse, 0, true));

            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            assertThat(function.saved).isEmpty();

            WorldLoopExtension.passAfter(Duration.ofMinutes(1));

            verify(warehouse, times(2)).destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null);
            assertThat(function.saved).hasSize(1);
            assertThat(function.removals).isZero();
        }

        @Test
        void chargeFee_shouldRenewWithoutASecondFee_whenTakingTheFeeFailsAfterTheAdenaLeft() {
            when(warehouse.getAdena()).thenReturn(5_000L, 4_000L);
            when(warehouse.destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null))
                    .thenThrow(new IllegalStateException("item log failed"));
            final TestFunction function =
                    WorldLoopExtension.callInPass(() -> new TestFunction(true, warehouse, 0, true));

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            WorldLoopExtension.passAfter(Duration.ofMinutes(5));

            verify(warehouse, times(1)).destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null);
            assertThat(function.saved).hasSize(1);
            assertThat(function.removals).isZero();
        }

        @Test
        void chargeFee_shouldRemoveTheFunction_whenTheWarehouseRefusesTheLease() {
            final TestFunction function =
                    WorldLoopExtension.callInPass(() -> new TestFunction(true, warehouse, 0, true));

            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            assertThat(function.removals).isEqualTo(1);
            assertThat(function.saved).isEmpty();
        }

        @Test
        void chargeFee_shouldNotRetry_whenRemovingTheUnpaidFunctionFails() {
            final TestFunction function = WorldLoopExtension.callInPass(() -> {
                final TestFunction created = new TestFunction(true, warehouse, 0, true);
                created.failingRemoval = true;
                return created;
            });

            WorldLoopExtension.passAfter(Duration.ofMillis(1));
            WorldLoopExtension.passAfter(Duration.ofMinutes(1));
            WorldLoopExtension.passAfter(Duration.ofMillis(RATE));

            assertThat(function.removals).isEqualTo(1);
            assertThat(function.saved).isEmpty();
            verify(warehouse, times(1)).destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null);
        }

        @Test
        void chargeFee_shouldRenewWithoutCharging_whenThePlayerPaidTheFirstActivation() {
            final TestFunction function =
                    WorldLoopExtension.callInPass(() -> new TestFunction(true, warehouse, 0, false));

            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            assertThat(function.saved).hasSize(1);
            assertThat(function.removals).isZero();
            verify(warehouse, never()).destroyItemByItemId(any(), anyInt(), anyLong(), any(), any());
        }

        private void whenTheWarehousePays() {
            when(warehouse.destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, LEASE, null, null))
                    .thenReturn(adena);
        }
    }

    @Nested
    class Change {
        @Test
        void change_shouldStoreTheNewLevelAndLease() {
            final TestFunction function = WorldLoopExtension.callInPass(() -> {
                final TestFunction changed = new TestFunction(false, warehouse, 0, false);
                changed.change(4, 2_000);
                return changed;
            });

            assertThat(function.saved)
                    .extracting(ResidenceFunction::level, ResidenceFunction::lease)
                    .containsExactly(tuple(4, 2_000));
        }
    }
}
