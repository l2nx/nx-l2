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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import java.util.Collection;

/**
 * @version $Revision: 1.8.2.3.2.7 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestUnEquipItem extends ClientPacket {
    private BodyPart slot;

    /**
     * Packet type id 0x16 format: cd
     */
    @Override
    protected void readImpl() {
        slot = BodyPart.fromPaperdollSlot(readInt());
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final Item item = player.getInventory().getPaperdollItemByBodyPart(slot);

        // Wear-items are not to be unequipped.
        if (item == null) {
            return;
        }

        // The English system message say weapon, but it's applied to any equipped item.
        if (player.isAttackingNow() || player.isCastingNow() || player.isCastingSimultaneouslyNow()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_CHANGE_WEAPONS_DURING_AN_ATTACK);
            return;
        }

        // Arrows and bolts.
        if ((slot == BodyPart.L_HAND) && (item.getTemplate() instanceof EtcItem)) {
            return;
        }

        // Prevent of unequipping a cursed weapon.
        if ((slot == BodyPart.LR_HAND) && (player.isCursedWeaponEquipped() || player.isCombatFlagEquipped())) {
            return;
        }

        // Prevent player from unequipping items in special conditions.
        if (player.isStunned() || player.isSleeping() || player.isParalyzed() || player.isAlikeDead()) {
            return;
        }

        if (!player.getInventory().canManipulateWithItemId(item.getId())) {
            player.sendPacket(SystemMessageId.THAT_ITEM_CANNOT_BE_TAKEN_OFF);
            return;
        }

        if (item.isWeapon() && item.getWeaponItem().isForceEquip() && !player.isGM()) {
            player.sendPacket(SystemMessageId.THAT_ITEM_CANNOT_BE_TAKEN_OFF);
            return;
        }

        final Collection<Item> unequipped = player.getInventory().unEquipItemInBodySlotAndRecord(slot);
        player.broadcastUserInfo();

        // This can be 0 if the user pressed the right mouse button twice very fast.
        if (!unequipped.isEmpty()) {
            SystemMessage sm = null;
            final Item unequippedItem = unequipped.iterator().next();
            if (unequippedItem.isEnchanted()) {
                sm = new SystemMessage(SystemMessageId.THE_EQUIPMENT_S1_S2_HAS_BEEN_REMOVED);
                sm.addInt(unequippedItem.getEnchantLevel());
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_DISARMED);
            }

            sm.addItemName(unequippedItem);
            player.sendPacket(sm);

            final InventoryUpdate iu = new InventoryUpdate();
            iu.addItems(unequipped);
            player.sendInventoryUpdate(iu);
        }
    }
}
