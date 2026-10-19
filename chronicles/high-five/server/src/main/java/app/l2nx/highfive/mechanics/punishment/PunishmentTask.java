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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.handler.IPunishmentHandler;
import app.l2nx.highfive.handler.PunishmentHandler;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid
 */
@Slf4j
public class PunishmentTask implements Runnable {

    private static final String INSERT_QUERY =
            "INSERT INTO punishments (`key`, `affect`, `type`, `expiration`, `reason`, `punishedBy`) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_QUERY = "UPDATE punishments SET expiration = ? WHERE id = ?";

    private int id;
    private final String key;

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

    public PunishmentTask(
            int id,
            Object key,
            PunishmentAffect affect,
            PunishmentType type,
            long expirationTime,
            String reason,
            String punishedBy,
            boolean isStored) {
        this.id = id;
        this.key = String.valueOf(key);
        this.affect = affect;
        this.type = type;
        this.expirationTime = expirationTime;
        this.reason = reason;
        this.punishedBy = punishedBy;
        stored = isStored;
        startPunishment();
    }

    /**
     * @return affection value charId, account, ip, etc..
     */
    public Object getKey() {
        return key;
    }

    /**
     * @return {@code true} if current punishment task has expired, {@code false} otherwise.
     */
    public boolean isExpired() {
        return (expirationTime > 0) && (System.currentTimeMillis() > expirationTime);
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
            task = ThreadPool.schedule(this, expirationTime - System.currentTimeMillis());
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
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, key);
                ps.setString(2, affect.name());
                ps.setString(3, type.name());
                ps.setLong(4, expirationTime);
                ps.setString(5, reason);
                ps.setString(6, punishedBy);
                ps.execute();
                try (ResultSet rset = ps.getGeneratedKeys()) {
                    if (rset.next()) {
                        id = rset.getInt(1);
                    }
                }

                stored = true;
            } catch (SQLException e) {
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
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(UPDATE_QUERY)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setLong(2, id);
                ps.execute();
            } catch (SQLException e) {
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
