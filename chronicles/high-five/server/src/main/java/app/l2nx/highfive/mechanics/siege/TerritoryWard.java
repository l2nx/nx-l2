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
package app.l2nx.highfive.mechanics.siege;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import lombok.Getter;
import lombok.Setter;

public class TerritoryWard {
    @Getter
    protected Player player = null;

    public int playerId = 0;
    private Item item = null;

    @Getter
    @Setter
    private Npc npc = null;

    private Location location;
    private Location oldLocation;

    private final int itemId;

    @Getter
    @Setter
    private int ownerCastleId;

    @Getter
    private final int territoryId;

    public TerritoryWard(int territoryId, int x, int y, int z, int heading, int itemId, int castleId, Npc npc) {
        this.territoryId = territoryId;
        location = new Location(x, y, z, heading);
        this.itemId = itemId;
        ownerCastleId = castleId;
        this.npc = npc;
    }

    public synchronized void spawnBack() {
        if (player != null) {
            dropIt();
        }

        // Init the dropped WardInstance and add it in the world as a visible object at the position where last Pc got
        // it.
        npc = TerritoryWarManager.getInstance().spawnNPC(36491 + territoryId, oldLocation);
    }

    public synchronized void spawnMe() {
        if (player != null) {
            dropIt();
        }

        // Init the dropped WardInstance and add it in the world as a visible object at the position where Pc was last.
        npc = TerritoryWarManager.getInstance().spawnNPC(36491 + territoryId, location);
    }

    public synchronized void unSpawnMe() {
        if (player != null) {
            dropIt();
        }

        if ((npc != null) && !npc.isDecayed()) {
            npc.deleteMe();
        }
    }

    public boolean activate(Player player, Item item) {
        if (player.isMounted()) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_THE_REQUIRED_CONDITION_TO_EQUIP_THAT_ITEM);
            player.destroyItem(ItemProcessType.DESTROY, item, null, true);
            spawnMe();
            return false;
        } else if (TerritoryWarManager.getInstance().getRegisteredTerritoryId(player) == 0) {
            player.sendMessage("Non participants can't pickup Territory Wards!");
            player.destroyItem(ItemProcessType.DESTROY, item, null, true);
            spawnMe();
            return false;
        }

        // Player holding it data.
        this.player = player;
        playerId = this.player.getObjectId();
        oldLocation = new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
        npc = null;

        // Equip with the weapon.
        if (item == null) {
            this.item = ItemManager.createItem(ItemProcessType.PICKUP, itemId, 1, null, null);
        } else {
            this.item = item;
        }

        this.player.getInventory().equipItem(this.item);
        final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EQUIPPED_YOUR_S1);
        sm.addItemName(this.item);
        this.player.sendPacket(sm);

        // Refresh inventory
        final InventoryUpdate iu = new InventoryUpdate();
        iu.addItem(this.item);
        this.player.sendInventoryUpdate(iu);

        // Refresh player stats
        this.player.broadcastUserInfo();
        this.player.setCombatFlagEquipped(true);
        this.player.sendPacket(SystemMessageId.YOU_VE_ACQUIRED_THE_WARD_MOVE_QUICKLY_TO_YOUR_FORCES_OUTPOST);
        TerritoryWarManager.getInstance().giveTWPoint(player, territoryId, 5);
        return true;
    }

    public void dropIt() {
        // Reset player stats
        player.setCombatFlagEquipped(false);
        final BodyPart bodyPart = BodyPart.fromItem(item);
        player.getInventory().unEquipItemInBodySlot(bodyPart);
        player.destroyItem(ItemProcessType.DESTROY, item, null, true);
        item = null;
        player.broadcastUserInfo();
        location = new Location(player.getX(), player.getY(), player.getZ(), player.getHeading());
        player = null;
        playerId = 0;
    }
}
