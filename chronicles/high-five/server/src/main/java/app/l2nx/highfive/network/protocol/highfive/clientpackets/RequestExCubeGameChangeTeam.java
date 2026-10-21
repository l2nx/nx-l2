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
 * Format: chdd d: Arena d: Team
 * @author mrTJO
 */
@Slf4j
public class RequestExCubeGameChangeTeam extends ClientPacket {
    private int arena;
    private int team;

    @Override
    protected void readImpl() {
        // client sends -1,0,1,2 for arena parameter
        arena = readInt() + 1;
        team = readInt();
    }

    @Override
    protected void runImpl() {
        // do not remove players after start
        if (HandysBlockCheckerManager.getInstance().arenaIsBeingUsed(arena)) {
            return;
        }

        final Player player = getPlayer();

        switch (this.team) {
            case 0:
            case 1: {
                // Change Player Team
                HandysBlockCheckerManager.getInstance().changePlayerToTeam(player, arena);
                break;
            }
            case -1:
                {
                    // Remove Player (me)
                }
                {
                    final int team = HandysBlockCheckerManager.getInstance()
                            .getHolder(arena)
                            .getPlayerTeam(player);

                    // client sends two times this packet if click on exit
                    // client did not send this packet on restart
                    if (team > -1) {
                        HandysBlockCheckerManager.getInstance().removePlayer(player, arena, team);
                    }
                    break;
                }
            default: {
                log.warn("Wrong Cube Game Team ID: {}", this.team);
                break;
            }
        }
    }
}
