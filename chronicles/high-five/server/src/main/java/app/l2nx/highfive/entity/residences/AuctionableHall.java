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
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuctionableHall extends ClanHall {
    private static final int CH_RATE = 604800000;

    @Getter
    protected long paidUntil;

    @Getter
    private final int grade;

    protected boolean paid;

    @Getter
    final int lease;

    public AuctionableHall(StatSet set) {
        super(set);
        paidUntil = set.getLong("paidUntil");
        grade = set.getInt("grade");
        paid = set.getBoolean("paid");
        lease = set.getInt("lease");
        if (!isFree()) {
            initialyzeTask(false);
            loadFunctions();
        }
    }

    /**
     * @return if clanHall is paid or not
     */
    public boolean getPaid() {
        return paid;
    }

    @Override
    public void free() {
        // Reset before the base persists the hall through updateDb.
        paidUntil = 0;
        paid = false;
        super.free();
    }

    @Override
    public void setOwner(Clan clan) {
        super.setOwner(clan);
        paidUntil = ClockBridge.wallMillis();
        initialyzeTask(true);
    }

    /**
     * Initialize Fee Task
     * @param forced
     */
    private void initialyzeTask(boolean forced) {
        final long currentTime = ClockBridge.wallMillis();
        if (paidUntil > currentTime) {
            WorldPartition.loop().schedule(new FeeTask(), paidUntil - currentTime, TimeUnit.MILLISECONDS);
        } else if (!paid && !forced) {
            if ((ClockBridge.wallMillis() + (3600000 * 24)) <= (paidUntil + CH_RATE)) {
                WorldPartition.loop().schedule(new FeeTask(), 3600000 * 24, TimeUnit.MILLISECONDS);
            } else {
                WorldPartition.loop()
                        .schedule(
                                new FeeTask(), (paidUntil + CH_RATE) - ClockBridge.wallMillis(), TimeUnit.MILLISECONDS);
            }
        } else {
            WorldPartition.loop().schedule(new FeeTask(), 0, TimeUnit.MILLISECONDS);
        }
    }

    /** Fee Task */
    protected class FeeTask implements Runnable {

        @Override
        public void run() {
            try {
                if (isFree()) {
                    return;
                }

                final long time = ClockBridge.wallMillis();
                if (paidUntil > time) {
                    WorldPartition.loop().schedule(new FeeTask(), paidUntil - time, TimeUnit.MILLISECONDS);
                    return;
                }

                final Clan clan = ClanTable.getInstance().getClan(getOwnerId());
                if (ClanTable.getInstance().getClan(getOwnerId()).getWarehouse().getAdena() >= getLease()) {
                    if (paidUntil != 0) {
                        while (paidUntil <= time) {
                            paidUntil += CH_RATE;
                        }
                    } else {
                        paidUntil = time + CH_RATE;
                    }

                    ClanTable.getInstance()
                            .getClan(getOwnerId())
                            .getWarehouse()
                            .destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, getLease(), null, null);
                    WorldPartition.loop().schedule(new FeeTask(), paidUntil - time, TimeUnit.MILLISECONDS);
                    paid = true;
                    updateDb();
                } else {
                    paid = false;
                    if (time > (paidUntil + CH_RATE)) {
                        if (ClanHallTable.getInstance().loaded()) {
                            ClanHallAuctionManager.getInstance().initNPC(getId());
                            ClanHallTable.getInstance().setFree(getId());
                            clan.broadcastToOnlineMembers(
                                    new SystemMessage(
                                            SystemMessageId
                                                    .THE_CLAN_HALL_FEE_IS_ONE_WEEK_OVERDUE_THEREFORE_THE_CLAN_HALL_OWNERSHIP_HAS_BEEN_REVOKED));
                        } else {
                            WorldPartition.loop().schedule(new FeeTask(), 3000, TimeUnit.MILLISECONDS);
                        }
                    } else {
                        updateDb();
                        final SystemMessage sm = new SystemMessage(
                                SystemMessageId
                                        .PAYMENT_FOR_YOUR_CLAN_HALL_HAS_NOT_BEEN_MADE_PLEASE_MAKE_PAYMENT_TO_YOUR_CLAN_WAREHOUSE_BY_S1_TOMORROW);
                        sm.addInt(lease);
                        clan.broadcastToOnlineMembers(sm);
                        if ((time + (3600000 * 24)) <= (paidUntil + CH_RATE)) {
                            WorldPartition.loop().schedule(new FeeTask(), 3600000 * 24, TimeUnit.MILLISECONDS);
                        } else {
                            WorldPartition.loop()
                                    .schedule(new FeeTask(), (paidUntil + CH_RATE) - time, TimeUnit.MILLISECONDS);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    @Override
    public void updateDb() {
        ClanHallTable.getInstance()
                .saveState(ClanHallState.builder()
                        .clanHallId(getId())
                        .ownerClanId(getOwnerId())
                        .paidUntil(instantOrNull(paidUntil))
                        .paid(paid)
                        .build());
    }
}
