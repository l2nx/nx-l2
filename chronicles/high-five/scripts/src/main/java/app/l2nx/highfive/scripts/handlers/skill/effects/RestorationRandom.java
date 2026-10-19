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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ExtractableProductItem;
import app.l2nx.highfive.entity.item.holders.RestorationItemHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Restoration Random effect implementation.<br>
 * This effect is present in item skills that "extract" new items upon usage.<br>
 * This effect has been unhardcoded in order to work on targets as well.
 * @author Zoey76, Mobius
 */
public class RestorationRandom extends AbstractEffect {
    private final List<ExtractableProductItem> products = new ArrayList<>();

    public RestorationRandom(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        for (StatSet group : params.getList("items", StatSet.class)) {
            final List<RestorationItemHolder> items = new ArrayList<>();
            for (StatSet item : group.getList(".", StatSet.class)) {
                items.add(RestorationItemHolder.builder()
                        .id(item.getInt(".id"))
                        .count(item.getLong(".count"))
                        .minEnchant(item.getInt(".minEnchant", 0))
                        .maxEnchant(item.getInt(".maxEnchant", 0))
                        .build());
            }

            products.add(new ExtractableProductItem(items, group.getFloat(".chance")));
        }
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final double rndNum = 100 * random.nextDouble();
        double chance = 0;
        double chanceFrom = 0;
        final List<RestorationItemHolder> creationList = new ArrayList<>();

        // Explanation for future changes:
        // You get one chance for the current skill, then you can fall into
        // one of the "areas" like in a roulette.
        // Example: for an item like Id1,A1,30;Id2,A2,50;Id3,A3,20;
        // #---#-----#--#
        // 0--30----80-100
        // If you get chance equal 45% you fall into the second zone 30-80.
        // Meaning you get the second production list.
        // Calculate extraction
        for (ExtractableProductItem expi : products) {
            chance = expi.getChance();
            if ((rndNum >= chanceFrom) && (rndNum <= (chance + chanceFrom))) {
                creationList.addAll(expi.getItems());
                break;
            }

            chanceFrom += chance;
        }

        final Player player = effected.asPlayer();
        if (creationList.isEmpty()) {
            player.sendPacket(SystemMessageId.THERE_WAS_NOTHING_FOUND_INSIDE);
            return;
        }

        final Map<Item, Long> extractedItems = new HashMap<>();
        for (RestorationItemHolder createdItem : creationList) {
            if ((createdItem.id() <= 0) || (createdItem.count() <= 0)) {
                continue;
            }

            final long itemCount = (long) (createdItem.count() * RatesConfig.RATE_EXTRACTABLE);
            final Item newItem = player.addItem(ItemProcessType.REWARD, createdItem.id(), itemCount, effector, false);

            if (createdItem.maxEnchant() > 0) {
                final int minEnchant = createdItem.minEnchant();
                final int maxEnchant = createdItem.maxEnchant();
                newItem.setEnchantLevel(
                        minEnchant < maxEnchant ? random.nextInt(minEnchant, maxEnchant + 1) : minEnchant);
            }

            extractedItems.merge(newItem, itemCount, Long::sum);
        }

        if (!extractedItems.isEmpty()) {
            final InventoryUpdate playerIU = new InventoryUpdate();
            for (Entry<Item, Long> entry : extractedItems.entrySet()) {
                if (entry.getKey().getTemplate().isStackable()) {
                    playerIU.addModifiedItem(entry.getKey());
                } else {
                    for (Item itemInstance : player.getInventory()
                            .getAllItemsByItemId(entry.getKey().getId())) {
                        playerIU.addModifiedItem(itemInstance);
                    }
                }

                sendMessage(player, entry.getKey(), entry.getValue().longValue());
            }

            player.sendInventoryUpdate(playerIU);
        }
    }

    private void sendMessage(Player player, Item item, long count) {
        final SystemMessage sm;
        if (count > 1) {
            sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_S2_S1);
            sm.addItemName(item);
            sm.addLong(count);
        } else if (item.isEnchanted()) {
            sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_A_S1_S2);
            sm.addInt(item.getEnchantLevel());
            sm.addItemName(item);
        } else {
            sm = new SystemMessage(SystemMessageId.YOU_HAVE_OBTAINED_S1);
            sm.addItemName(item);
        }

        player.sendPacket(sm);
    }
}
