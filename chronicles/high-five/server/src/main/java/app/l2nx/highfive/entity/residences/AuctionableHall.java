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
package app.l2nx.highfive.entity.residences;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.residence.model.ClanHallState;
import app.l2nx.highfive.util.StatSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class AuctionableHall extends ClanHall {
    private static final int CH_RATE = 604800000;

    protected long paidUntil;

    private final int grade;

    protected boolean paid;

    final int lease;

    // One pending fee per hall: a new owner replaces the previous owner's fee instead of adding a second one.
    private ScheduledFuture<?> feeTask;

    public AuctionableHall(StatSet set) {
        super(set);
        paidUntil = set.getLong("paidUntil");
        grade = set.getInt("grade");
        paid = set.getBoolean("paid");
        lease = set.getInt("lease");
        if (!free) {
            initialyzeTask(false);
            loadFunctions();
        }
    }

    @Override
    public long getPaidUntil() {
        WorldPartition.checkOwner();
        return paidUntil;
    }

    @Override
    public int getGrade() {
        WorldPartition.checkOwner();
        return grade;
    }

    @Override
    public int getLease() {
        WorldPartition.checkOwner();
        return lease;
    }

    public boolean getPaid() {
        WorldPartition.checkOwner();
        return paid;
    }

    @Override
    public void free() {
        WorldPartition.checkOwner();
        // Reset before the base persists the hall through updateDb.
        paidUntil = 0;
        paid = false;
        super.free();
    }

    @Override
    public void setOwner(Clan clan) {
        WorldPartition.checkOwner();
        super.setOwner(clan);
        paidUntil = ClockBridge.wallMillis();
        initialyzeTask(true);
    }

    private void initialyzeTask(boolean forced) {
        final long currentTime = ClockBridge.wallMillis();
        if (paidUntil > currentTime) {
            scheduleFee(paidUntil - currentTime);
        } else if (!paid && !forced) {
            if ((ClockBridge.wallMillis() + (3600000 * 24)) <= (paidUntil + CH_RATE)) {
                scheduleFee(3600000 * 24);
            } else {
                scheduleFee((paidUntil + CH_RATE) - ClockBridge.wallMillis());
            }
        } else {
            scheduleFee(0);
        }
    }

    private void scheduleFee(long delay) {
        if (feeTask != null) {
            feeTask.cancel(false);
        }

        feeTask = WorldPartition.loop().schedule(this::chargeFee, delay, TimeUnit.MILLISECONDS);
    }

    private void chargeFee() {
        feeTask = null;
        try {
            if (free) {
                return;
            }

            final long time = ClockBridge.wallMillis();
            if (paidUntil > time) {
                scheduleFee(paidUntil - time);
                return;
            }

            final int ownerId = getOwnerId();
            final Clan clan = ClanTable.getInstance().getClan(ownerId);
            if (clan.getWarehouse().getAdena() >= lease) {
                if (paidUntil != 0) {
                    while (paidUntil <= time) {
                        paidUntil += CH_RATE;
                    }
                } else {
                    paidUntil = time + CH_RATE;
                }

                clan.getWarehouse().destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, lease, null, null);
                scheduleFee(paidUntil - time);
                paid = true;
                updateDb();
            } else {
                paid = false;
                if (time > (paidUntil + CH_RATE)) {
                    if (ClanHallTable.getInstance().loaded()) {
                        ClanHallAuctionManager.getInstance().initNPC(id);
                        ClanHallTable.getInstance().setFree(id);
                        clan.broadcastToOnlineMembers(
                                new SystemMessage(
                                        SystemMessageId
                                                .THE_CLAN_HALL_FEE_IS_ONE_WEEK_OVERDUE_THEREFORE_THE_CLAN_HALL_OWNERSHIP_HAS_BEEN_REVOKED));
                    } else {
                        scheduleFee(3000);
                    }
                } else {
                    updateDb();
                    final SystemMessage sm = new SystemMessage(
                            SystemMessageId
                                    .PAYMENT_FOR_YOUR_CLAN_HALL_HAS_NOT_BEEN_MADE_PLEASE_MAKE_PAYMENT_TO_YOUR_CLAN_WAREHOUSE_BY_S1_TOMORROW);
                    sm.addInt(lease);
                    clan.broadcastToOnlineMembers(sm);
                    if ((time + (3600000 * 24)) <= (paidUntil + CH_RATE)) {
                        scheduleFee(3600000 * 24);
                    } else {
                        scheduleFee((paidUntil + CH_RATE) - time);
                    }
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void updateDb() {
        WorldPartition.checkOwner();
        ClanHallTable.getInstance()
                .saveState(ClanHallState.builder()
                        .clanHallId(id)
                        .ownerClanId(getOwnerId())
                        .paidUntil(instantOrNull(paidUntil))
                        .paid(paid)
                        .build());
    }
}
