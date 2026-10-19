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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid
 */
@Slf4j
public class AutoAnnouncement extends Announcement implements Runnable {
    private static final String INSERT_QUERY =
            "INSERT INTO announcements (`type`, `content`, `author`, `initial`, `delay`, `repeat`) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_QUERY =
            "UPDATE announcements SET `type` = ?, `content` = ?, `author` = ?, `initial` = ?, `delay` = ?, `repeat` = ? WHERE id = ?";

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

    public AutoAnnouncement(ResultSet rset) throws SQLException {
        super(rset);
        initial = rset.getLong("initial");
        delay = rset.getLong("delay");
        repeat = rset.getInt("repeat");
        restartMe();
    }

    @Override
    public boolean storeMe() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, getType().ordinal());
            ps.setString(2, getContent());
            ps.setString(3, getAuthor());
            ps.setLong(4, initial);
            ps.setLong(5, delay);
            ps.setInt(6, repeat);
            ps.execute();
            try (ResultSet rset = ps.getGeneratedKeys()) {
                if (rset.next()) {
                    id = rset.getInt(1);
                }
            }
        } catch (Exception e) {
            log.warn("Couldn't store announcement: ", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean updateMe() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(UPDATE_QUERY)) {
            ps.setInt(1, getType().ordinal());
            ps.setString(2, getContent());
            ps.setString(3, getAuthor());
            ps.setLong(4, initial);
            ps.setLong(5, delay);
            ps.setLong(6, repeat);
            ps.setLong(7, getId());
            ps.execute();
        } catch (Exception e) {
            log.warn("Couldn't update announcement: ", e);
            return false;
        }

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
        task = ThreadPool.schedule(this, initial);
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

        task = ThreadPool.schedule(this, delay);
    }
}
