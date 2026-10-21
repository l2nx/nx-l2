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

import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExAutoSoulShot;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;

/**
 * @version $Revision: 1.0.0.0 $ $Date: 2005/07/11 15:29:30 $
 */
public class RequestAutoSoulShot extends ClientPacket {
    // format cd
    private int itemId;
    private int type; // 1 = on : 0 = off;

    @Override
    protected void readImpl() {
        itemId = readInt();
        type = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!player.isInStoreMode() && (player.getActiveRequester() == null) && !player.isDead()) {
            final Item item = player.getInventory().getItemByItemId(itemId);
            if (item == null) {
                return;
            }

            // Disable Blessed SpiritShots in Olympiad.
            if (player.isInOlympiadMode()
                    && OlympiadConfig.OLYMPIAD_DISABLE_BLESSED_SPIRITSHOTS
                    && (item.getEtcItem().getDefaultAction() == ActionType.SPIRITSHOT)
                    && item.getEtcItem().isBlessed()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_USE_THAT_ITEM_IN_A_GRAND_OLYMPIAD_MATCH);
                return;
            }

            if (type == 1) {
                if (!player.getInventory().canManipulateWithItemId(item.getId())) {
                    player.sendMessage("Cannot use this item.");
                    return;
                }

                // Fishingshots are not automatic on retail
                if ((itemId < 6535) || (itemId > 6540)) {
                    // Attempt to charge first shot on activation
                    if ((itemId == 6645)
                            || (itemId == 6646)
                            || (itemId == 6647)
                            || (itemId == 20332)
                            || (itemId == 20333)
                            || (itemId == 20334)) {
                        if (player.hasSummon()) {
                            if (item.getEtcItem().getHandlerName().equals("BeastSoulShot")) {
                                if (player.getSummon().getSoulShotsPerHit() > item.getCount()) {
                                    player.sendPacket(
                                            SystemMessageId.YOU_DON_T_HAVE_ENOUGH_SOULSHOTS_NEEDED_FOR_A_PET_SERVITOR);
                                    return;
                                }
                            } else {
                                if (player.getSummon().getSpiritShotsPerHit() > item.getCount()) {
                                    player.sendPacket(
                                            SystemMessageId.YOU_DON_T_HAVE_ENOUGH_SOULSHOTS_NEEDED_FOR_A_PET_SERVITOR);
                                    return;
                                }
                            }

                            player.addAutoSoulShot(itemId);
                            player.sendPacket(new ExAutoSoulShot(itemId, type));

                            // start the auto soulshot use
                            final SystemMessage sm =
                                    new SystemMessage(SystemMessageId.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_ACTIVATED);
                            sm.addItemName(item);
                            player.sendPacket(sm);

                            player.rechargeShots(true, true);
                            player.getSummon().rechargeShots(true, true);
                        } else {
                            player.sendPacket(
                                    SystemMessageId
                                            .YOU_DO_NOT_HAVE_A_SERVITOR_OR_PET_AND_THEREFORE_CANNOT_USE_THE_AUTOMATIC_USE_FUNCTION);
                        }
                    } else {
                        if ((player.getActiveWeaponItem() != player.getFistsWeaponItem())
                                && (item.getTemplate().getCrystalType()
                                        == player.getActiveWeaponItem().getCrystalTypePlus())) {
                            player.addAutoSoulShot(itemId);
                            player.sendPacket(new ExAutoSoulShot(itemId, type));
                        } else {
                            if (((itemId >= 2509) && (itemId <= 2514))
                                    || ((itemId >= 3947) && (itemId <= 3952))
                                    || (itemId == 5790)
                                    || ((itemId >= 22072) && (itemId <= 22081))) {
                                player.sendPacket(SystemMessageId.YOUR_SPIRITSHOT_DOES_NOT_MATCH_THE_WEAPON_S_GRADE);
                            } else {
                                player.sendPacket(
                                        SystemMessageId
                                                .THE_SOULSHOT_YOU_ARE_ATTEMPTING_TO_USE_DOES_NOT_MATCH_THE_GRADE_OF_YOUR_EQUIPPED_WEAPON);
                            }

                            player.addAutoSoulShot(itemId);
                            player.sendPacket(new ExAutoSoulShot(itemId, type));
                        }

                        // start the auto soulshot use
                        final SystemMessage sm =
                                new SystemMessage(SystemMessageId.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_ACTIVATED);
                        sm.addItemName(item);
                        player.sendPacket(sm);

                        player.rechargeShots(true, true);
                    }
                }
            } else if (type == 0) {
                player.removeAutoSoulShot(itemId);
                player.sendPacket(new ExAutoSoulShot(itemId, type));

                // cancel the auto soulshot use
                final SystemMessage sm =
                        new SystemMessage(SystemMessageId.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_DEACTIVATED);
                sm.addItemName(item);
                player.sendPacket(sm);
            }
        }
    }
}
