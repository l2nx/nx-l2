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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.Skill;

/**
 * Task dedicated to qued magic use of character
 * @author xban1x
 */
public class QueuedMagicUseTask implements Runnable {
    private final Player currPlayer;
    private final Skill queuedSkill;
    private final boolean ctrlPressed;
    private final boolean shiftPressed;

    public QueuedMagicUseTask(Player currPlayer, Skill queuedSkill, boolean isCtrlPressed, boolean isShiftPressed) {
        this.currPlayer = currPlayer;
        this.queuedSkill = queuedSkill;
        ctrlPressed = isCtrlPressed;
        shiftPressed = isShiftPressed;
    }

    @Override
    public void run() {
        if (currPlayer != null) {
            currPlayer.useMagic(queuedSkill, ctrlPressed, shiftPressed);
        }
    }
}
