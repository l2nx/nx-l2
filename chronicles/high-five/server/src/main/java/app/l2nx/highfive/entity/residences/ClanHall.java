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

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.zone.type.ClanHallZone;
import app.l2nx.highfive.network.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

@Slf4j
public abstract class ClanHall {

    @Getter
    final int id;

    private List<Door> doors;

    @Getter
    private final String name;

    @Getter
    private int ownerId;

    @Getter
    private final String desc;

    @Getter
    private final String location;

    @Getter
    @Setter
    private ClanHallZone zone;

    @Getter
    protected boolean free = true;

    private final Map<Integer, ClanHallFunction> functions;

    /** Clan Hall Functions */
    public static final int FUNC_TELEPORT = 1;

    public static final int FUNC_ITEM_CREATE = 2;
    public static final int FUNC_RESTORE_HP = 3;
    public static final int FUNC_RESTORE_MP = 4;
    public static final int FUNC_RESTORE_EXP = 5;
    public static final int FUNC_SUPPORT = 6;
    public static final int FUNC_DECO_FRONTPLATEFORM = 7; // Only Auctionable Halls
    public static final int FUNC_DECO_CURTAINS = 8; // Only Auctionable Halls

    public class ClanHallFunction {
        @Getter
        final int type;

        @Getter
        @Setter
        private int level;

        @Getter
        @Setter
        protected int lease;

        protected int tempFee;

        @Getter
        final long rate;

        @Getter
        @Setter
        long endTime;

        protected boolean inDebt;
        public boolean cwh; // first activating clanhall function is payed from player inventory, any others from clan
        // warehouse

        public ClanHallFunction(int type, int lvl, int lease, int tempLease, long rate, long time, boolean cwh) {
            this.type = type;
            level = lvl;
            this.lease = lease;
            tempFee = tempLease;
            this.rate = rate;
            endTime = time;
            initializeTask(cwh);
        }

        private void initializeTask(boolean cwh) {
            if (free) {
                return;
            }

            final long currentTime = ClockBridge.wallMillis();
            if (endTime > currentTime) {
                ThreadPool.schedule(new FunctionTask(cwh), endTime - currentTime);
            } else {
                ThreadPool.schedule(new FunctionTask(cwh), 0);
            }
        }

        private class FunctionTask implements Runnable {
            public FunctionTask(boolean cwh) {
                ClanHall.ClanHallFunction.this.cwh = cwh;
            }

            @Override
            public void run() {
                try {
                    if (free) {
                        return;
                    }

                    if ((ClanTable.getInstance()
                                            .getClan(getOwnerId())
                                            .getWarehouse()
                                            .getAdena()
                                    >= lease)
                            || !cwh) {
                        final int fee = endTime == -1 ? tempFee : lease;
                        setEndTime(ClockBridge.wallMillis() + rate);
                        dbSave();
                        if (cwh) {
                            ClanTable.getInstance()
                                    .getClan(getOwnerId())
                                    .getWarehouse()
                                    .destroyItemByItemId(ItemProcessType.FEE, Inventory.ADENA_ID, fee, null, null);
                        }

                        ThreadPool.schedule(new FunctionTask(true), rate);
                    } else {
                        removeFunction(type);
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }

        public void dbSave() {
            ClanHallTable.getInstance()
                    .saveFunction(
                            id,
                            ResidenceFunction.builder()
                                    .type(type)
                                    .level(level)
                                    .lease(lease)
                                    .rate(rate)
                                    .endTime(instantOrNull(endTime))
                                    .build());
        }
    }

    public ClanHall(StatSet set) {
        id = set.getInt("id");
        name = set.getString("name");
        ownerId = set.getInt("ownerId");
        desc = set.getString("desc");
        location = set.getString("location");
        functions = new ConcurrentHashMap<>();
        if (ownerId <= 0) {
            return;
        }

        final Clan clan = ClanTable.getInstance().getClan(ownerId);
        if (clan != null) {
            clan.setHideoutId(id);
            free = false;
        }
    }

    /**
     * Frees a hall whose stored owner clan is not loaded. Called by the loader after construction, because freeing
     * persists through the subclass {@link #updateDb()}, which needs state the subclass constructor sets.
     */
    public final void releaseUnloadedOwner() {
        if (!free || (ownerId <= 0)) {
            return;
        }

        log.warn("Clan hall {}: owner clan {} is not loaded, the hall is freed.", id, ownerId);
        for (ResidenceFunction stored : ClanHallTable.getInstance().findFunctions(id)) {
            removeFunction(stored.type());
        }

        free();
    }

    /**
     * @return all Door
     */
    public List<Door> getDoors() {
        if (doors == null) {
            doors = new ArrayList<>();
        }

        return doors;
    }

    /**
     * @param doorId
     * @return Door
     */
    public Door getDoor(int doorId) {
        if (doorId <= 0) {
            return null;
        }

        for (Door door : getDoors()) {
            if (door.getId() == doorId) {
                return door;
            }
        }

        return null;
    }

    /**
     * @param type
     * @return function with id
     */
    public ClanHallFunction getFunction(int type) {
        return functions.get(type);
    }

    /**
     * @param x
     * @param y
     * @param z
     * @return true if object is inside the zone
     */
    public boolean checkIfInZone(int x, int y, int z) {
        return zone.isInsideZone(x, y, z);
    }

    /** Free this clan hall */
    public void free() {
        ownerId = 0;
        free = true;
        for (Integer fc : functions.keySet()) {
            removeFunction(fc);
        }

        functions.clear();
        updateDb();
    }

    /**
     * Set owner if clan hall is free
     * @param clan
     */
    public void setOwner(Clan clan) {
        // Verify that this ClanHall is Free and Clan isn't null.
        if ((ownerId > 0) || (clan == null)) {
            return;
        }

        ownerId = clan.getId();
        free = false;
        clan.setHideoutId(id);

        // Announce to Online member new ClanHall.
        clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
        updateDb();
    }

    /**
     * Open or Close Door
     * @param player
     * @param doorId
     * @param open
     */
    public void openCloseDoor(Player player, int doorId, boolean open) {
        if ((player != null) && (player.getClanId() == ownerId)) {
            openCloseDoor(doorId, open);
        }
    }

    public void openCloseDoor(int doorId, boolean open) {
        openCloseDoor(getDoor(doorId), open);
    }

    public void openCloseDoor(Door door, boolean open) {
        if (door != null) {
            if (open) {
                door.openMe();
            } else {
                door.closeMe();
            }
        }
    }

    public void openCloseDoors(Player player, boolean open) {
        if ((player != null) && (player.getClanId() == ownerId)) {
            openCloseDoors(open);
        }
    }

    public void openCloseDoors(boolean open) {
        for (Door door : getDoors()) {
            if (door != null) {
                if (open) {
                    door.openMe();
                } else {
                    door.closeMe();
                }
            }
        }
    }

    /** Banish Foreigner */
    public void banishForeigners() {
        if (zone != null) {
            zone.banishForeigners(ownerId);
        } else {
            LoggerFactory.getLogger(getClass()).warn("Zone is null for clan hall: {} {}", id, name);
        }
    }

    /** Load All Functions */
    protected void loadFunctions() {
        for (ResidenceFunction stored : ClanHallTable.getInstance().findFunctions(id)) {
            functions.put(
                    stored.type(),
                    new ClanHallFunction(
                            stored.type(),
                            stored.level(),
                            stored.lease(),
                            0,
                            stored.rate(),
                            epochMillisOrZero(stored.endTime()),
                            true));
        }
    }

    /**
     * Remove function In List and in DB
     * @param functionType
     */
    public void removeFunction(int functionType) {
        functions.remove(functionType);
        ClanHallTable.getInstance().deleteFunction(id, functionType);
    }

    public boolean updateFunctions(Player player, int type, int level, int lease, long rate, boolean addNew) {
        if ((player == null)
                || ((lease > 0) && !player.destroyItemByItemId(null, Inventory.ADENA_ID, lease, null, true))) {
            return false;
        }

        if (addNew) {
            functions.put(type, new ClanHallFunction(type, level, lease, 0, rate, 0, false));
        } else if ((level == 0) && (lease == 0)) {
            removeFunction(type);
        } else if ((lease - functions.get(type).getLease()) > 0) {
            functions.remove(type);
            functions.put(type, new ClanHallFunction(type, level, lease, 0, rate, -1, false));
        } else {
            functions.get(type).setLease(lease);
            functions.get(type).setLevel(level);
            functions.get(type).dbSave();
        }

        return true;
    }

    public int getGrade() {
        return 0;
    }

    public long getPaidUntil() {
        return 0;
    }

    public int getLease() {
        return 0;
    }

    public boolean isSiegableHall() {
        return false;
    }

    public abstract void updateDb();
}
