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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * @author UnAfraid
 */
@Slf4j
public class Announcement implements IAnnouncement {

    private static final String INSERT_QUERY = "INSERT INTO announcements (type, content, author) VALUES (?, ?, ?)";
    private static final String UPDATE_QUERY =
            "UPDATE announcements SET type = ?, content = ?, author = ? WHERE id = ?";
    private static final String DELETE_QUERY = "DELETE FROM announcements WHERE id = ?";

    @Getter
    protected int id;

    @Getter
    @Setter
    private AnnouncementType type;

    @Getter
    @Setter
    private String content;

    @Getter
    @Setter
    private String author;

    public Announcement(AnnouncementType type, String content, String author) {
        this.type = type;
        this.content = content;
        this.author = author;
    }

    public Announcement(ResultSet rset) throws SQLException {
        id = rset.getInt("id");
        type = AnnouncementType.findById(rset.getInt("type"));
        content = rset.getString("content");
        author = rset.getString("author");
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean storeMe() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_QUERY, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, type.ordinal());
            ps.setString(2, content);
            ps.setString(3, author);
            ps.execute();
            try (ResultSet rset = ps.getGeneratedKeys()) {
                if (rset.next()) {
                    id = rset.getInt(1);
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Couldn't store announcement: ", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean updateMe() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(UPDATE_QUERY)) {
            ps.setInt(1, type.ordinal());
            ps.setString(2, content);
            ps.setString(3, author);
            ps.setInt(4, id);
            ps.execute();
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Couldn't store announcement: ", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean deleteMe() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(DELETE_QUERY)) {
            ps.setInt(1, id);
            ps.execute();
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Couldn't remove announcement: ", e);
            return false;
        }

        return true;
    }
}
