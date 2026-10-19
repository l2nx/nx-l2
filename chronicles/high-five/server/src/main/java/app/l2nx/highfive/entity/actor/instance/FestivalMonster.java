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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;

/**
 * This class manages all attackable festival NPCs, spawned during the Festival of Darkness.
 * @author Tempy
 */
public class FestivalMonster extends Monster {
    protected int bonusMultiplier = 1;

    /**
     * Creates a festival monster.
     * @param template the festival monster NPC template
     */
    public FestivalMonster(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.FestivalMonster);
    }

    public void setOfferingBonus(int bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
    }

    /**
     * Return True if the attacker is not another FestivalMonster.
     */
    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return !(attacker instanceof FestivalMonster);
    }

    /**
     * All mobs in the festival are aggressive, and have high aggro range.
     */
    @Override
    public boolean isAggressive() {
        return true;
    }

    /**
     * All mobs in the festival really don't need random animation.
     */
    @Override
    public boolean hasRandomAnimation() {
        return false;
    }

    /**
     * Actions:
     * <ul>
     * <li>Check if the killing object is a player, and then find the party they belong to.</li>
     * <li>Add a blood offering item to the leader of the party.</li>
     * <li>Update the party leader's inventory to show the new item addition.</li>
     * </ul>
     */
    @Override
    public void doItemDrop(Creature lastAttacker) {
        Player killingChar = null;
        if (!lastAttacker.isPlayer()) {
            return;
        }

        killingChar = lastAttacker.asPlayer();
        final Party associatedParty = killingChar.getParty();
        if (associatedParty == null) {
            return;
        }

        final Player partyLeader = associatedParty.getLeader();
        final Item addedOfferings = partyLeader
                .getInventory()
                .addItem(
                        ItemProcessType.REWARD,
                        SevenSignsFestival.FESTIVAL_OFFERING_ID,
                        bonusMultiplier,
                        partyLeader,
                        this);
        final InventoryUpdate iu = new InventoryUpdate();
        if (addedOfferings.getCount() != bonusMultiplier) {
            iu.addModifiedItem(addedOfferings);
        } else {
            iu.addNewItem(addedOfferings);
        }

        partyLeader.sendInventoryUpdate(iu);

        super.doItemDrop(lastAttacker); // Normal drop
    }
}
