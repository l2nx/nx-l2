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
package app.l2nx.highfive.mechanics.script.timers;

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * @author UnAfraid
 * @param <T>
 */
public class TimerHolder<T> implements Runnable {
    @Getter
    private final T event;

    @Getter
    private final StatSet params;

    private final long time;

    @Getter
    private final Npc npc;

    @Getter
    private final Player player;

    @Getter
    private final boolean repeating;

    private final IEventTimerEvent<T> eventScript;
    private final IEventTimerCancel<T> cancelScript;
    private final TimerExecutor<T> postExecutor;
    private final ScheduledFuture<?> task;

    public TimerHolder(
            T event,
            StatSet params,
            long time,
            Npc npc,
            Player player,
            boolean isRepeating,
            IEventTimerEvent<T> eventScript,
            IEventTimerCancel<T> cancelScript,
            TimerExecutor<T> postExecutor) {
        Objects.requireNonNull(event, getClass().getSimpleName() + ": \"event\" cannot be null!");
        Objects.requireNonNull(eventScript, getClass().getSimpleName() + ": \"script\" cannot be null!");
        Objects.requireNonNull(postExecutor, getClass().getSimpleName() + ": \"postExecutor\" cannot be null!");
        this.event = event;
        this.params = params;
        this.time = time;
        this.npc = npc;
        this.player = player;
        repeating = isRepeating;
        this.eventScript = eventScript;
        this.cancelScript = cancelScript;
        this.postExecutor = postExecutor;
        task = isRepeating
                ? ThreadPool.scheduleAtFixedRate(this, this.time, this.time)
                : ThreadPool.schedule(this, this.time);

        if (npc != null) {
            npc.addTimerHolder(this);
        }

        if (player != null) {
            player.addTimerHolder(this);
        }
    }

    /**
     * Cancels this timer.
     */
    public void cancelTimer() {
        if (npc != null) {
            npc.removeTimerHolder(this);
        }

        if (player != null) {
            player.removeTimerHolder(this);
        }

        if ((task == null) || task.isCancelled() || task.isDone()) {
            return;
        }

        task.cancel(false);
        cancelScript.onTimerCancel(this);
    }

    /**
     * Cancels task related to this quest timer.
     */
    public void cancelTask() {
        if ((task != null) && !task.isDone() && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    /**
     * @return the remaining time of the timer, or -1 in case it doesn't exist.
     */
    public long getRemainingTime() {
        if ((task == null) || task.isCancelled() || task.isDone()) {
            return -1;
        }

        return task.getDelay(TimeUnit.MILLISECONDS);
    }

    /**
     * @param event
     * @param npc
     * @param player
     * @return {@code true} if event, npc, player are equals to the ones stored in this TimerHolder, {@code false} otherwise
     */
    public boolean isEqual(T event, Npc npc, Player player) {
        return this.event.equals(event) && (this.npc == npc) && (this.player == player);
    }

    /**
     * @param timer the other timer to be compared with.
     * @return {@code true} of both of timers' npc, event and player match, {@code false} otherwise.
     */
    public boolean isEqual(TimerHolder<T> timer) {
        return event.equals(timer.event) && (npc == timer.npc) && (player == timer.player);
    }

    @Override
    public void run() {
        // Notify the post executor to remove this timer from the map.
        postExecutor.onTimerPostExecute(this);

        // Notify the script that the event has been fired.
        eventScript.onTimerEvent(this);
    }

    @Override
    public String toString() {
        return "event: " + event + " params: " + params + " time: " + time + " npc: " + npc + " player: " + player
                + " repeating: " + repeating + " script: "
                + eventScript.getClass().getSimpleName() + " postExecutor: "
                + postExecutor.getClass().getSimpleName();
    }
}
