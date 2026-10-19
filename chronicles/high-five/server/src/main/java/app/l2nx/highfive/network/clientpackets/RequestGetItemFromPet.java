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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.PacketLogger;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.EnchantResult;

/**
 * @version $Revision: 1.3.4.4 $ $Date: 2005/03/29 23:15:33 $
 */
public class RequestGetItemFromPet extends ClientPacket {
    private int _objectId;
    private long _amount;

    @SuppressWarnings("unused")
    private int _unknown;

    @Override
    protected void readImpl() {
        _objectId = readInt();
        _amount = readLong();
        _unknown = readInt(); // = 0 for most trades
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((_amount <= 0) || (player == null) || !player.hasPet()) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendMessage("You get items from pet too fast.");
            return;
        }

        if (player.getActiveEnchantItemId() != Player.ID_NONE) {
            player.setActiveEnchantItemId(Player.ID_NONE);
            player.sendPacket(SystemMessageId.YOU_HAVE_CANCELLED_THE_ENCHANTING_PROCESS);
            player.sendPacket(new EnchantResult(2, 0, 0));
            return;
        }

        final Pet pet = player.getSummon().asPet();
        final Item item = pet.getInventory().getItemByObjectId(_objectId);
        if (item == null) {
            return;
        }

        if (_amount > item.getCount()) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    getClass().getSimpleName() + ": Character " + player.getName() + " of account "
                            + player.getAccountName() + " tried to get item with oid " + _objectId
                            + " from pet but has invalid count " + _amount + " item count: " + item.getCount(),
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        if (pet.transferItem(ItemProcessType.TRANSFER, _objectId, _amount, player.getInventory(), player, pet)
                == null) {
            PacketLogger.warning("Invalid item transfer request: " + pet.getName() + "(pet) --> " + player.getName());
        }
    }
}
