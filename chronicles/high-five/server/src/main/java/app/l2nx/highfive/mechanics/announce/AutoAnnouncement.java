/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.mechanics.announce;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.world.model.AnnouncementRow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * @author UnAfraid
 */
public class AutoAnnouncement extends Announcement implements Runnable {
    @Getter
    @Setter
    private long initial;

    @Getter
    @Setter
    private long delay;

    @Getter
    @Setter
    private int repeat = -1;

    private int currentState;
    private ScheduledFuture<?> task;

    public AutoAnnouncement(
            AnnouncementType type, String content, String author, long initial, long delay, int repeat) {
        super(type, content, author);
        this.initial = initial;
        this.delay = delay;
        this.repeat = repeat;
        restartMe();
    }

    public AutoAnnouncement(AnnouncementRow row) {
        super(row);
        initial = row.initialDelayMs();
        delay = row.intervalMs();
        repeat = row.repeat();
        restartMe();
    }

    @Override
    protected AnnouncementRow toRow() {
        return super.toRow().toBuilder()
                .initialDelayMs(initial)
                .intervalMs(delay)
                .repeat(repeat)
                .build();
    }

    @Override
    protected boolean isStorable() {
        return true;
    }

    @Override
    public boolean deleteMe() {
        if ((task != null) && !task.isCancelled()) {
            task.cancel(false);
        }

        return super.deleteMe();
    }

    public void restartMe() {
        if ((task != null) && !task.isCancelled()) {
            task.cancel(false);
        }

        currentState = repeat;
        task = WorldPartition.loop().schedule(this, initial, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        if ((currentState != -1) && (currentState <= 0)) {
            return;
        }

        for (String content : getContent().split(System.lineSeparator())) {
            World.broadcastToAllOnlinePlayers(content, getType() == AnnouncementType.AUTO_CRITICAL);
        }

        if (currentState != -1) {
            currentState--;
        }

        task = WorldPartition.loop().schedule(this, delay, TimeUnit.MILLISECONDS);
    }
}
