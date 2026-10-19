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
import app.l2nx.highfive.entity.actor.enums.player.PlayerState;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;

/**
 * The Class ConditionPlayerState.
 * @author mkizub
 */
public class ConditionPlayerState extends Condition {
    private final PlayerState check;
    private final boolean required;

    /**
     * Instantiates a new condition player state.
     * @param check the player state to be verified.
     * @param required the required value.
     */
    public ConditionPlayerState(PlayerState check, boolean required) {
        this.check = check;
        this.required = required;
    }

    @Override
    public boolean testImpl(Creature effector, Creature effected, Skill skill, ItemTemplate item) {
        final Player player = effector.asPlayer();
        switch (check) {
            case RESTING: {
                if (player != null) {
                    return (player.isSitting() == required);
                }

                return !required;
            }
            case MOVING: {
                return effector.isMoving() == required;
            }
            case RUNNING: {
                return (effector.isMoving() == required) && (effector.isRunning() == required);
            }
            case STANDING: {
                if (player != null) {
                    return (required != (player.isSitting() || player.isMoving()));
                }

                return (required != effector.isMoving());
            }
            case FLYING: {
                return (effector.isFlying() == required);
            }
            case BEHIND: {
                return (effector.isBehind(effected) == required);
            }
            case FRONT: {
                return (effector.isInFrontOf(effected) == required);
            }
            case CHAOTIC: {
                if (player != null) {
                    return ((player.getKarma() > 0) == required);
                }

                return !required;
            }
            case OLYMPIAD: {
                if (player != null) {
                    return (player.isInOlympiadMode() == required);
                }

                return !required;
            }
        }

        return !required;
    }
}
