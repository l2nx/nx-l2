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
package app.l2nx.highfive.mechanics.script;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;

public class QuestTimer {
    protected final String name;

    @Getter
    protected final Quest quest;

    @Getter
    protected final Npc npc;

    @Getter
    protected final Player player;

    @Getter
    protected final boolean repeating;

    protected ScheduledFuture<?> scheduler;

    public QuestTimer(Quest quest, String name, long time, Npc npc, Player player, boolean repeating) {
        this.quest = quest;
        this.name = name;
        this.npc = npc;
        this.player = player;
        this.repeating = repeating;

        if (repeating) {
            scheduler = ThreadPool.scheduleAtFixedRate(new ScheduleTimerTask(), time, time); // Prepare auto end task.
        } else {
            scheduler = ThreadPool.schedule(new ScheduleTimerTask(), time); // Prepare auto end task.
        }

        if (npc != null) {
            npc.addQuestTimer(this);
        }

        if (player != null) {
            player.addQuestTimer(this);
        }
    }

    public void cancel() {
        cancelTask();

        if (npc != null) {
            npc.removeQuestTimer(this);
        }

        if (player != null) {
            player.removeQuestTimer(this);
        }
    }

    public void cancelTask() {
        if ((scheduler != null) && !scheduler.isDone() && !scheduler.isCancelled()) {
            scheduler.cancel(false);
            scheduler = null;
        }

        quest.removeQuestTimer(this);
    }

    /**
     * public method to compare if this timer matches with the key attributes passed.
     * @param quest : Quest instance to which the timer is attached
     * @param name : Name of the timer
     * @param npc : Npc instance attached to the desired timer (null if no npc attached)
     * @param player : Player instance attached to the desired timer (null if no player attached)
     * @return boolean
     */
    public boolean equals(Quest quest, String name, Npc npc, Player player) {
        if ((quest == null) || (quest != this.quest)) {
            return false;
        }

        if ((name == null) || !name.equals(this.name)) {
            return false;
        }

        return (npc == this.npc) && (player == this.player);
    }

    public boolean isActive() {
        return (scheduler != null) && !scheduler.isCancelled() && !scheduler.isDone();
    }

    @Override
    public String toString() {
        return name;
    }

    public class ScheduleTimerTask implements Runnable {
        @Override
        public void run() {
            if (scheduler == null) {
                return;
            }

            if (!repeating) {
                cancel();
            }

            quest.notifyEvent(name, npc, player);
        }
    }
}
