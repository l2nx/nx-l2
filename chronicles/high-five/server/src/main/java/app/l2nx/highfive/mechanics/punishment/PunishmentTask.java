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
package app.l2nx.highfive.mechanics.punishment;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.handler.IPunishmentHandler;
import app.l2nx.highfive.handler.PunishmentHandler;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author UnAfraid
 */
@Slf4j
public class PunishmentTask implements Runnable {

    private long id;
    private final Object key;

    @Getter
    private final PunishmentAffect affect;

    @Getter
    private final PunishmentType type;

    @Getter
    private final long expirationTime;

    @Getter
    private final String reason;

    @Getter
    private final String punishedBy;

    @Getter
    private boolean stored;

    private ScheduledFuture<?> task = null;

    public PunishmentTask(
            Object key,
            PunishmentAffect affect,
            PunishmentType type,
            long expirationTime,
            String reason,
            String punishedBy) {
        this(0, key, affect, type, expirationTime, reason, punishedBy, false);
    }

    /**
     * @param key the key in any form {@link PunishmentManager#normalizeKey} accepts for the affect
     */
    public PunishmentTask(
            long id,
            Object key,
            PunishmentAffect affect,
            PunishmentType type,
            long expirationTime,
            String reason,
            String punishedBy,
            boolean isStored) {
        this.id = id;
        this.key = PunishmentManager.normalizeKey(key, affect);
        this.affect = affect;
        this.type = type;
        this.expirationTime = expirationTime;
        this.reason = reason;
        this.punishedBy = punishedBy;
        stored = isStored;
        startPunishment();
    }

    /**
     * @return the account id, character object id, address or hardware id, by affect
     */
    public Object getKey() {
        return key;
    }

    /**
     * @return {@code true} if current punishment task has expired, {@code false} otherwise.
     */
    public boolean isExpired() {
        return (expirationTime > 0) && (ClockBridge.wallMillis() > expirationTime);
    }

    /**
     * Activates the punishment task.
     */
    private void startPunishment() {
        if (isExpired()) {
            return;
        }

        onStart();
        if (expirationTime > 0) // Has expiration?
        {
            task = WorldPartition.loop()
                    .schedule(this, expirationTime - ClockBridge.wallMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the punishment task.
     */
    public void stopPunishment() {
        abortTask();
        onEnd();
    }

    /**
     * Aborts the scheduled task.
     */
    private void abortTask() {
        if (task == null) {
            return;
        }

        if (!task.isCancelled() && !task.isDone()) {
            task.cancel(false);
        }

        task = null;
    }

    /**
     * Store and activate punishment upon start.
     */
    private void onStart() {
        if (!stored) {
            try {
                id = PunishmentManager.getInstance().insert(this);
                stored = true;
            } catch (DataAccessException e) {
                log.warn("Couldn't store punishment task for: {} {}", affect, key, e);
            }
        }

        final IPunishmentHandler handler = PunishmentHandler.getInstance().getHandler(type);
        if (handler != null) {
            handler.onStart(this);
        }
    }

    /**
     * Remove and deactivate punishment when it ends.
     */
    private void onEnd() {
        if (stored) {
            try {
                PunishmentManager.getInstance().updateExpiration(id, ClockBridge.wallMillis());
            } catch (DataAccessException e) {
                log.warn("Couldn't update punishment task for: {} {} id: {}", affect, key, id, e);
            }
        }

        final IPunishmentHandler handler = PunishmentHandler.getInstance().getHandler(type);
        if (handler != null) {
            handler.onEnd(this);
        }
    }

    /**
     * Runs when punishment task ends in order to stop and remove it.
     */
    @Override
    public void run() {
        PunishmentManager.getInstance().stopPunishment(key, affect, type);
    }
}
