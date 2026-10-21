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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PetItemList;
import app.l2nx.highfive.util.StatSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Restoration effect implementation.
 * @author Zoey76, Mobius
 */
@Slf4j
public class Restoration extends AbstractEffect {
    private final int itemId;
    private final long itemCount;
    private final int itemEnchantmentLevel;

    public Restoration(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        itemId = params.getInt("itemId", 0);
        itemCount = params.getLong("itemCount", 0);
        itemEnchantmentLevel = params.getInt("itemEnchantmentLevel", 0);
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if ((effected == null) || !effected.isPlayable()) {
            return;
        }

        if ((itemId <= 0) || (itemCount <= 0)) {
            effected.sendPacket(SystemMessageId.THERE_WAS_NOTHING_FOUND_INSIDE);
            log.warn("Effect with wrong item Id/count: {}/{}!", itemId, itemCount);
            return;
        }

        if (effected.isPlayer()) {
            final Item newItem = effected.asPlayer().addItem(ItemProcessType.REWARD, itemId, itemCount, effector, true);
            if (itemEnchantmentLevel > 0) {
                newItem.setEnchantLevel(itemEnchantmentLevel);
            }
        } else if (effected.isPet()) {
            final Player target = effected.asPlayer();
            final Item newItem =
                    effected.getInventory().addItem(ItemProcessType.REWARD, itemId, itemCount, target, effector);
            if (itemEnchantmentLevel > 0) {
                newItem.setEnchantLevel(itemEnchantmentLevel);
            }

            target.sendPacket(new PetItemList(effected.getInventory().getItems()));
        }
    }
}
