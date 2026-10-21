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
package app.l2nx.highfive.scripts.handlers.items;

import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpiritShot implements IItemHandler {
    @Override
    public boolean onItemUse(Playable playable, Item item, boolean forceUse) {
        if (!playable.isPlayer()) {
            playable.sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_THIS_ITEM);
            return false;
        }

        final SkillHolder[] skills = item.getTemplate().getSkills();
        if (skills == null) {
            log.warn("Is missing skills!");
            return false;
        }

        final Player player = playable.asPlayer();
        final Item weaponInst = player.getActiveWeaponInstance();
        final Weapon weaponItem = player.getActiveWeaponItem();
        final int itemId = item.getId();

        // Check if Spirit shot can be used
        if ((weaponInst == null) || (weaponItem.getSpiritShotCount() == 0)) {
            if (!player.getAutoSoulShot().contains(itemId)) {
                player.sendPacket(SystemMessageId.YOU_MAY_NOT_USE_SPIRITSHOTS);
            }

            return false;
        }

        // Check if Spirit shot is already active
        if (player.isChargedShot(ShotType.SPIRITSHOTS)) {
            return false;
        }

        final boolean gradeCheck = item.isEtcItem()
                && (item.getEtcItem().getDefaultAction() == ActionType.SPIRITSHOT)
                && (weaponInst.getTemplate().getCrystalTypePlus()
                        == item.getTemplate().getCrystalTypePlus());
        if (!gradeCheck) {
            if (!player.getAutoSoulShot().contains(itemId)) {
                player.sendPacket(SystemMessageId.YOUR_SPIRITSHOT_DOES_NOT_MATCH_THE_WEAPON_S_GRADE);
            }

            return false;
        }

        // Consume Spirit shot if player has enough of them
        if (!player.destroyItem(
                ItemProcessType.NONE, item.getObjectId(), weaponItem.getSpiritShotCount(), null, false)) {
            if (!player.disableAutoShot(itemId)) {
                player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_SPIRITSHOT_FOR_THAT);
            }

            return false;
        }

        // Charge Spirit shot
        player.setChargedShot(ShotType.SPIRITSHOTS, true);

        // Send message to client.
        player.sendPacket(SystemMessageId.YOUR_SPIRITSHOT_HAS_BEEN_ENABLED);
        player.broadcastSkillPacket(
                new MagicSkillUse(player, player, skills[0].getSkillId(), skills[0].getSkillLevel(), 0, 0), player);
        return true;
    }
}
