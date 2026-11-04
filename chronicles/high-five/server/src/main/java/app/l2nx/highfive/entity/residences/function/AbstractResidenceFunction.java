/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.residences.function;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * The first activation is paid from the player's inventory, every renewal from the owner's clan warehouse; a renewal
 * the warehouse cannot pay removes the function. The first renewal after an upgrade (end time -1) is free.
 */
@Slf4j
@SingleWriter
public abstract class AbstractResidenceFunction {
    private static final long RETRY_DELAY = TimeUnit.MINUTES.toMillis(1);

    private final int type;

    private int level;

    private int lease;

    private final long rate;

    private long endTime;

    private boolean cwh;

    // The pending fee, cancelled when the function is removed or replaced so that it charges no more.
    private ScheduledFuture<?> feeTask;

    protected AbstractResidenceFunction(int type, int level, int lease, long rate, long endTime) {
        this.type = type;
        this.level = level;
        this.lease = lease;
        this.rate = rate;
        this.endTime = endTime;
    }

    /**
     * Schedules the first fee, at the end time or now when it has passed. The subclass calls it at the end of its
     * constructor, since the owner check reads the residence.
     */
    protected final void startFee(boolean fromWarehouse) {
        if (!isOwned()) {
            return;
        }

        final long currentTime = ClockBridge.wallMillis();
        scheduleFee(fromWarehouse, endTime > currentTime ? endTime - currentTime : 0);
    }

    protected abstract int residenceId();

    protected abstract boolean isOwned();

    protected abstract ItemContainer ownerWarehouse();

    protected abstract void save(ResidenceFunction function);

    /** Removes the function from its residence, from memory and the database. */
    protected abstract void removeUnpaid();

    private void scheduleFee(boolean fromWarehouse, long delay) {
        cwh = fromWarehouse;
        feeTask = WorldPartition.loop().schedule(this::chargeFee, delay, TimeUnit.MILLISECONDS);
    }

    final void cancelFee() {
        if (feeTask != null) {
            feeTask.cancel(false);
            feeTask = null;
        }
    }

    private void chargeFee() {
        feeTask = null;
        final boolean paid;
        try {
            if (!isOwned()) {
                return;
            }

            paid = takeFee();
        } catch (Exception e) {
            log.error("Could not charge function {} of residence {}, retrying", type, residenceId(), e);
            scheduleFee(cwh, RETRY_DELAY);
            return;
        }

        if (!paid) {
            removeUnpaidOnce();
            return;
        }

        endTime = ClockBridge.wallMillis() + rate;
        storeRenewal();
        scheduleFee(true, rate);
    }

    /**
     * @throws RuntimeException only when the warehouse still holds the adena, so that the charge may run again
     */
    private boolean takeFee() {
        final int fee = endTime == -1 ? 0 : lease;
        if (!cwh || (fee == 0)) {
            return true;
        }

        final ItemContainer warehouse = ownerWarehouse();
        final long before = warehouse.getAdena();
        try {
            return warehouse.destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, fee, null, null) != null;
        } catch (RuntimeException e) {
            if (warehouse.getAdena() == before) {
                throw e;
            }

            log.error(
                    "Taking the fee of function {} of residence {} failed after the adena left",
                    type,
                    residenceId(),
                    e);
            return true;
        }
    }

    private void removeUnpaidOnce() {
        try {
            removeUnpaid();
        } catch (Exception e) {
            // Not retried: the function may already be gone from memory, and its type may hold a newer function.
            log.error("Could not remove unpaid function {} of residence {}", type, residenceId(), e);
        }
    }

    // Not retried: a second charge would take a second fee; the next save writes the renewed end time.
    private void storeRenewal() {
        try {
            persist();
        } catch (Exception e) {
            log.error(
                    "Could not store the renewal of function {} of residence {}, the next save stores it",
                    type,
                    residenceId(),
                    e);
        }
    }

    private void persist() {
        save(ResidenceFunction.builder()
                .type(type)
                .level(level)
                .lease(lease)
                .rate(rate)
                .endTime(instantOrNull(endTime))
                .build());
    }

    public int getType() {
        WorldPartition.checkOwner();
        return type;
    }

    public int getLevel() {
        WorldPartition.checkOwner();
        return level;
    }

    public int getLease() {
        WorldPartition.checkOwner();
        return lease;
    }

    public long getRate() {
        WorldPartition.checkOwner();
        return rate;
    }

    public long getEndTime() {
        WorldPartition.checkOwner();
        return endTime;
    }

    public void change(int level, int lease) {
        WorldPartition.checkOwner();
        this.level = level;
        this.lease = lease;
        persist();
    }
}
