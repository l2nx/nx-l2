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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Format: chddd d: Arena d: Answer
 * @author mrTJO
 */
@Slf4j
public class RequestExCubeGameReadyAnswer extends ClientPacket {
    private int arena;
    private int answer;

    @Override
    protected void readImpl() {
        // client sends -1,0,1,2 for arena parameter
        arena = readInt() + 1;

        // client sends 1 if clicked confirm on not clicked, 0 if clicked cancel
        answer = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        switch (answer) {
            case 0: {
                // Cancel - Answer No
                break;
            }
            case 1: {
                // OK or Time Over
                HandysBlockCheckerManager.getInstance().increaseArenaVotes(arena);
                break;
            }
            default: {
                log.warn("Unknown Cube Game Answer ID: {}", answer);
                break;
            }
        }
    }
}
