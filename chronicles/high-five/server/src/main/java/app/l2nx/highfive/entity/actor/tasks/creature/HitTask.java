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
package app.l2nx.highfive.entity.actor.tasks.creature;

import app.l2nx.highfive.entity.actor.Creature;

/**
 * Task launching the function onHitTimer().<br>
 * <b><u>Actions</u>:</b>
 * <ul>
 * <li>If the attacker/target is dead or use fake death, notify the AI with CANCEL and send a Server->Client packet ActionFailed (if attacker is a Player)</li>
 * <li>If attack isn't aborted, send a message system (critical hit, missed...) to attacker/target if they are Player</li>
 * <li>If attack isn't aborted and hit isn't missed, reduce HP of the target and calculate reflection damage to reduce HP of attacker if necessary</li>
 * <li>if attack isn't aborted and hit isn't missed, manage attack or cast break of the target (calculating rate, sending message...)</li>
 * </ul>
 * @author xban1x
 */
public class HitTask implements Runnable {
    private final Creature creature;
    private final Creature hitTarget;
    private final int damage;
    private final boolean crit;
    private final boolean miss;
    private final byte shld;
    private final boolean soulshot;
    private final boolean rechargeShots;

    public HitTask(
            Creature creature,
            Creature target,
            int damage,
            boolean crit,
            boolean miss,
            byte shld,
            boolean soulshot,
            boolean rechargeShots) {
        this.creature = creature;
        hitTarget = target;
        this.damage = damage;
        this.crit = crit;
        this.shld = shld;
        this.miss = miss;
        this.soulshot = soulshot;
        this.rechargeShots = rechargeShots;
    }

    @Override
    public void run() {
        if (creature != null) {
            creature.onHitTimer(hitTarget, damage, crit, miss, shld, soulshot, rechargeShots);
        }
    }
}
