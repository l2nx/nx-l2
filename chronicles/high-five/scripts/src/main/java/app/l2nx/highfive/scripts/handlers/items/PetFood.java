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

import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import java.util.Set;

/**
 * @author Kerberos, Zoey76
 */
public class PetFood implements IItemHandler {
    @Override
    public boolean onItemUse(Playable playable, Item item, boolean forceUse) {
        if (playable.isPet() && !playable.asPet().canEatFoodId(item.getId())) {
            playable.sendPacket(SystemMessageId.THIS_PET_CANNOT_USE_THIS_ITEM);
            return false;
        }

        final SkillHolder[] skills = item.getTemplate().getSkills();
        if (skills != null) {
            for (SkillHolder sk : skills) {
                useFood(playable, sk.getSkillId(), sk.getSkillLevel(), item);
            }
        }

        return true;
    }

    private boolean useFood(Playable activeChar, int skillId, int skillLevel, Item item) {
        final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
        if (skill != null) {
            if (activeChar.isPet()) {
                final Pet pet = activeChar.asPet();
                if (pet.destroyItem(ItemProcessType.NONE, item.getObjectId(), 1, null, false)) {
                    pet.broadcastSkillPacket(new MagicSkillUse(pet, pet, skillId, skillLevel, 0, 0), pet);
                    pet.setCurrentFed(pet.getCurrentFed() + (skill.getFeed() * RatesConfig.PET_FOOD_RATE));
                    pet.broadcastStatusUpdate();
                    if (pet.isHungry()) {
                        pet.sendPacket(SystemMessageId.YOUR_PET_ATE_A_LITTLE_BUT_IS_STILL_HUNGRY);
                    }

                    return true;
                }
            } else if (activeChar.isPlayer()) {
                final Player player = activeChar.asPlayer();
                if (player.isMounted()) {
                    final Set<Integer> foodIds = PetDataTable.getInstance()
                            .getPetData(player.getMountNpcId())
                            .getFood();
                    if (foodIds.contains(item.getId())
                            && player.destroyItem(ItemProcessType.NONE, item.getObjectId(), 1, null, false)) {
                        player.broadcastSkillPacket(
                                new MagicSkillUse(player, player, skillId, skillLevel, 0, 0), player);
                        player.setCurrentFeed(player.getCurrentFeed() + skill.getFeed());
                        return true;
                    }
                }

                final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
                sm.addItemName(item);
                player.sendPacket(sm);
            }
        }

        return false;
    }
}
