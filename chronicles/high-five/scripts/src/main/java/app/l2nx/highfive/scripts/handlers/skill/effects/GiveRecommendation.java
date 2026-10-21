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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExVoteSystemInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Give Recommendation effect implementation.
 * @author NosBit
 */
@Slf4j
public class GiveRecommendation extends AbstractEffect {
    private final int amount;

    public GiveRecommendation(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        amount = params.getInt("amount", 0);
        if (amount == 0) {
            log.warn("Amount parameter is missing or set to 0. id:{}", set.getInt("id", -1));
        }
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        final Player target = (effected != null) && effected.isPlayer() ? effected.asPlayer() : null;
        if (target != null) {
            int recommendationsGiven = amount;
            if ((target.getRecomHave() + amount) >= 255) {
                recommendationsGiven = 255 - target.getRecomHave();
            }

            if (recommendationsGiven > 0) {
                target.setRecomHave(target.getRecomHave() + recommendationsGiven);

                final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_OBTAINED_S1_RECOMMENDATION_S);
                sm.addInt(recommendationsGiven);
                target.sendPacket(sm);
                target.updateUserInfo();
                if (PlayerConfig.NEVIT_ENABLED) {
                    target.sendPacket(new ExVoteSystemInfo(target));
                }
            } else {
                final Player player = (effector != null) && effector.isPlayer() ? effector.asPlayer() : null;
                if (player != null) {
                    player.sendPacket(SystemMessageId.NOTHING_HAPPENED);
                }
            }
        }
    }
}
