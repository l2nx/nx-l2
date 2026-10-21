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
package app.l2nx.highfive.mechanics.conditions;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;

/**
 * Target My Party Except Me condition implementation.
 * @author Adry_85
 */
public class ConditionTargetMyPartyExceptMe extends Condition {
    private final boolean value;

    public ConditionTargetMyPartyExceptMe(boolean value) {
        this.value = value;
    }

    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        boolean isPartyMember = true;
        final Player player = effector.asPlayer();
        if ((player == null) || (effected == null) || !effected.isPlayer()) {
            isPartyMember = false;
        } else if (player == effected) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_USE_THIS_ON_YOURSELF);
            isPartyMember = false;
        } else if (!player.isInParty() || !player.getParty().equals(effected.getParty())) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
            sm.addSkillName(skill);
            player.sendPacket(sm);
            isPartyMember = false;
        }

        return value == isPartyMember;
    }
}
