/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.mechanics.undergroundColiseum;

import java.util.ArrayList;
import java.util.List;

public class UCRunningTask implements Runnable {
    private final UCArena arena;

    public UCRunningTask(UCArena arena) {
        this.arena = arena;
    }

    @Override
    public void run() {
        arena.generateWinner();
        arena.removeTeams();

        UCTeam winnerTeam = null;
        for (UCTeam team : arena.getTeams()) {
            if (team.getStatus() == UCTeam.WIN) {
                winnerTeam = team;
            } else if (team.getStatus() == UCTeam.FAIL) {
                team.cleanUp();
            }
        }

        for (UCPoint point : arena.getPoints()) {
            point.actionDoors(false);
            point.getPlayers().clear();
        }

        if (winnerTeam != null) {
            if (!arena.getWaitingList().isEmpty()) {
                final UCTeam other = winnerTeam.getOtherTeam();
                final UCWaiting otherWaiting = arena.getWaitingList().get(0);
                other.setParty(otherWaiting.getParty());
                other.setRegisterTime(otherWaiting.getRegisterMillis());
                arena.getWaitingList().remove(0);
                arena.prepareStart();
                return;
            }

            winnerTeam.cleanUp();
        }

        if (arena.getWaitingList().size() >= 2) {
            int i = 0;
            UCWaiting teamWaiting = null;
            final List<UCWaiting> removeList = new ArrayList<>();
            for (UCTeam team : arena.getTeams()) {
                teamWaiting = arena.getWaitingList().get(i);
                removeList.add(teamWaiting);
                team.setParty(teamWaiting.getParty());
                team.setRegisterTime(teamWaiting.getRegisterMillis());
                i++;
                if (i == 2) {
                    break;
                }
            }

            for (UCWaiting tm : removeList) {
                arena.getWaitingList().remove(tm);
            }

            removeList.clear();
            arena.prepareStart();
            return;
        }

        arena.setIsBattleNow(false);
        arena.runNewTask(false);
    }
}
