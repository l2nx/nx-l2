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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.residences.function.AbstractResidenceFunction;
import app.l2nx.highfive.entity.residences.function.ResidenceFunctions;
import app.l2nx.highfive.entity.zone.type.ClanHallZone;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

@Slf4j
@SingleWriter
public abstract class ClanHall {

    final int id;

    // Registration order of the doors, which open and close in it.
    private final List<Door> doors = new ObjectArrayList<>();

    private final String name;

    private int ownerId;

    private final String desc;

    private final String location;

    private ClanHallZone zone;

    protected boolean free = true;

    private final ResidenceFunctions<ClanHallFunction> functions = new ResidenceFunctions<>();

    public static final int FUNC_TELEPORT = 1;

    public static final int FUNC_ITEM_CREATE = 2;
    public static final int FUNC_RESTORE_HP = 3;
    public static final int FUNC_RESTORE_MP = 4;
    public static final int FUNC_RESTORE_EXP = 5;
    public static final int FUNC_SUPPORT = 6;
    public static final int FUNC_DECO_FRONTPLATEFORM = 7; // Only Auctionable Halls
    public static final int FUNC_DECO_CURTAINS = 8; // Only Auctionable Halls

    @SingleWriter
    public class ClanHallFunction extends AbstractResidenceFunction {
        public ClanHallFunction(int type, int lvl, int lease, int tempLease, long rate, long time, boolean cwh) {
            super(type, lvl, lease, tempLease, rate, time);
            startFee(cwh);
        }

        @Override
        protected boolean isOwned() {
            return !free;
        }

        @Override
        protected ItemContainer ownerWarehouse() {
            return ClanTable.getInstance().getClan(ownerId).getWarehouse();
        }

        @Override
        protected void save(ResidenceFunction function) {
            ClanHallTable.getInstance().saveFunction(id, function);
        }

        @Override
        protected void removeUnpaid() {
            removeFunction(getType());
        }
    }

    public ClanHall(StatSet set) {
        id = set.getInt("id");
        name = set.getString("name");
        ownerId = set.getInt("ownerId");
        desc = set.getString("desc");
        location = set.getString("location");
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
        WorldPartition.checkOwner();
        if (!free || (ownerId <= 0)) {
            return;
        }

        log.warn("Clan hall {}: owner clan {} is not loaded, the hall is freed.", id, ownerId);
        for (ResidenceFunction stored : ClanHallTable.getInstance().findFunctions(id)) {
            removeFunction(stored.type());
        }

        free();
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public int getOwnerId() {
        WorldPartition.checkOwner();
        return ownerId;
    }

    public String getDesc() {
        WorldPartition.checkOwner();
        return desc;
    }

    public String getLocation() {
        WorldPartition.checkOwner();
        return location;
    }

    public ClanHallZone getZone() {
        WorldPartition.checkOwner();
        return zone;
    }

    public void setZone(ClanHallZone zone) {
        WorldPartition.checkOwner();
        this.zone = zone;
    }

    public boolean isFree() {
        WorldPartition.checkOwner();
        return free;
    }

    public List<Door> getDoors() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(doors));
    }

    public void addDoor(Door door) {
        WorldPartition.checkOwner();
        doors.add(door);
    }

    public Door getDoor(int doorId) {
        WorldPartition.checkOwner();
        if (doorId <= 0) {
            return null;
        }

        for (Door door : doors) {
            if (door.getId() == doorId) {
                return door;
            }
        }

        return null;
    }

    public ClanHallFunction getFunction(int type) {
        WorldPartition.checkOwner();
        return functions.get(type);
    }

    public boolean checkIfInZone(int x, int y, int z) {
        WorldPartition.checkOwner();
        return zone.isInsideZone(x, y, z);
    }

    public void free() {
        WorldPartition.checkOwner();
        ownerId = 0;
        free = true;
        functions.forEachType(this::removeFunction);

        updateDb();
    }

    public void setOwner(Clan clan) {
        WorldPartition.checkOwner();
        if ((ownerId > 0) || (clan == null)) {
            return;
        }

        ownerId = clan.getId();
        free = false;
        clan.setHideoutId(id);

        clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
        updateDb();
    }

    public void openCloseDoor(Player player, int doorId, boolean open) {
        WorldPartition.checkOwner();
        if ((player != null) && (player.getClanId() == ownerId)) {
            openCloseDoor(getDoor(doorId), open);
        }
    }

    public void openCloseDoor(int doorId, boolean open) {
        WorldPartition.checkOwner();
        openCloseDoor(getDoor(doorId), open);
    }

    public void openCloseDoor(Door door, boolean open) {
        WorldPartition.checkOwner();
        if (door != null) {
            if (open) {
                door.openMe();
            } else {
                door.closeMe();
            }
        }
    }

    public void openCloseDoors(Player player, boolean open) {
        WorldPartition.checkOwner();
        if ((player != null) && (player.getClanId() == ownerId)) {
            openCloseDoors(open);
        }
    }

    public void openCloseDoors(boolean open) {
        WorldPartition.checkOwner();
        try (Snapshot<Door> walk = ScratchBuffers.snapshot(doors)) {
            for (Door door : walk) {
                if (door != null) {
                    if (open) {
                        door.openMe();
                    } else {
                        door.closeMe();
                    }
                }
            }
        }
    }

    public void banishForeigners() {
        WorldPartition.checkOwner();
        if (zone != null) {
            zone.banishForeigners(ownerId);
        } else {
            LoggerFactory.getLogger(getClass()).warn("Zone is null for clan hall: {} {}", id, name);
        }
    }

    protected void loadFunctions() {
        for (ResidenceFunction stored : ClanHallTable.getInstance().findFunctions(id)) {
            functions.put(new ClanHallFunction(
                    stored.type(),
                    stored.level(),
                    stored.lease(),
                    0,
                    stored.rate(),
                    epochMillisOrZero(stored.endTime()),
                    true));
        }
    }

    public void removeFunction(int functionType) {
        WorldPartition.checkOwner();
        functions.remove(functionType);
        ClanHallTable.getInstance().deleteFunction(id, functionType);
    }

    public boolean updateFunctions(Player player, int type, int level, int lease, long rate, boolean addNew) {
        WorldPartition.checkOwner();
        if ((player == null)
                || ((lease > 0) && !player.destroyItemByItemId(null, Inventory.ADENA_ID, lease, null, true))) {
            return false;
        }

        if (addNew) {
            functions.put(new ClanHallFunction(type, level, lease, 0, rate, 0, false));
        } else if ((level == 0) && (lease == 0)) {
            removeFunction(type);
        } else if ((lease - functions.get(type).getLease()) > 0) {
            functions.put(new ClanHallFunction(type, level, lease, 0, rate, -1, false));
        } else {
            functions.get(type).change(level, lease);
        }

        return true;
    }

    public int getGrade() {
        WorldPartition.checkOwner();
        return 0;
    }

    public long getPaidUntil() {
        WorldPartition.checkOwner();
        return 0;
    }

    public int getLease() {
        WorldPartition.checkOwner();
        return 0;
    }

    public boolean isSiegableHall() {
        WorldPartition.checkOwner();
        return false;
    }

    public abstract void updateDb();
}
