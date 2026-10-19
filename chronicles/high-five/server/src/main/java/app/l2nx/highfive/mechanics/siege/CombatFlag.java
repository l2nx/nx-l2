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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import lombok.Getter;

public class CombatFlag {

    private Player player = null;

    @Getter
    private int playerObjectId = 0;

    private Item item = null;

    @Getter
    private Item combatFlagInstance;

    private final Location location;
    private final int itemId;

    @SuppressWarnings("unused")
    private final int fortId;

    public CombatFlag(int fortId, int x, int y, int z, int heading, int itemId) {
        this.fortId = fortId;
        location = new Location(x, y, z, heading);
        this.itemId = itemId;
    }

    public synchronized void spawnMe() {
        // Init the dropped ItemInstance and add it in the world as a visible object at the position where mob was last.
        combatFlagInstance = ItemManager.createItem(ItemProcessType.QUEST, itemId, 1, null, null);
        combatFlagInstance.dropMe(null, location.getX(), location.getY(), location.getZ());
    }

    public synchronized void unSpawnMe() {
        if (player != null) {
            dropIt();
        }

        if (combatFlagInstance != null) {
            combatFlagInstance.decayMe();
        }
    }

    public boolean activate(Player player, Item item) {
        if (player.isMounted()) {
            player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_THE_REQUIRED_CONDITION_TO_EQUIP_THAT_ITEM);
            return false;
        }

        // Player holding it data.
        this.player = player;
        playerObjectId = this.player.getObjectId();
        combatFlagInstance = null;

        // Equip with the weapon.
        this.item = item;
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
        player = null;
        playerObjectId = 0;
    }
}
