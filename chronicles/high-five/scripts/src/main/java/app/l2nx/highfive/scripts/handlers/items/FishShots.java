/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.scripts.handlers.items;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.MagicSkillUse;
import lombok.extern.slf4j.Slf4j;

/**
 * @author -Nemesiss-
 */
@Slf4j
public class FishShots implements IItemHandler {
    @Override
    public boolean onItemUse(Playable playable, Item item, boolean forceUse) {
        if (!playable.isPlayer()) {
            playable.sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_THIS_ITEM);
            return false;
        }

        final Player player = playable.asPlayer();
        final Item weaponInst = player.getActiveWeaponInstance();
        final Weapon weaponItem = player.getActiveWeaponItem();
        if ((weaponInst == null) || (weaponItem.getItemType() != WeaponType.FISHINGROD)) {
            return false;
        }

        if (player.isChargedShot(ShotType.FISH_SOULSHOTS)) {
            return false;
        }

        final SkillHolder[] skills = item.getTemplate().getSkills();
        if (skills == null) {
            log.warn("Is missing skills!");
            return false;
        }

        final boolean gradeCheck = item.isEtcItem()
                && (item.getEtcItem().getDefaultAction() == ActionType.FISHINGSHOT)
                && (weaponInst.getTemplate().getCrystalTypePlus()
                        == item.getTemplate().getCrystalTypePlus());
        if (!gradeCheck) {
            player.sendPacket(SystemMessageId.THAT_IS_THE_WRONG_GRADE_OF_SOULSHOT_FOR_THAT_FISHING_POLE);
            return false;
        }

        final long count = item.getCount();

        if (count < 1) {
            return false;
        }

        player.setChargedShot(ShotType.FISH_SOULSHOTS, true);
        player.destroyItem(ItemProcessType.NONE, item.getObjectId(), 1, null, false);
        final WorldObject oldTarget = player.getTarget();
        player.setTarget(player);

        player.broadcastSkillPacket(
                new MagicSkillUse(player, skills[0].getSkillId(), skills[0].getSkillLevel(), 0, 0), player);
        player.setTarget(oldTarget);
        return true;
    }
}
