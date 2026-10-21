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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerPressTutorialMark;
import app.l2nx.highfive.mechanics.script.QuestState;

public class RequestTutorialQuestionMark extends ClientPacket {
    private int number = 0;

    @Override
    protected void readImpl() {
        number = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final QuestState qs = player.getQuestState("Q00255_Tutorial");
        if (qs != null) {
            qs.getQuest().notifyEvent("QM:" + number, null, player);
        }

        // Notify scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_PRESS_TUTORIAL_MARK, player)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnPlayerPressTutorialMark(player, number), player);
        }
    }
}
