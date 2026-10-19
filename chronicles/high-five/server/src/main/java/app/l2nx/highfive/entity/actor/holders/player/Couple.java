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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.IdManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author evill33t
 */
@Slf4j
public class Couple {

    @Getter
    private int id = 0;

    @Getter
    private int player1Id = 0;

    @Getter
    private int player2Id = 0;

    private boolean maried = false;

    @Getter
    private Calendar affiancedDate;

    @Getter
    private Calendar weddingDate;

    public Couple(int coupleId) {
        id = coupleId;

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM mods_wedding WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    player1Id = rs.getInt("player1Id");
                    player2Id = rs.getInt("player2Id");
                    maried = Boolean.parseBoolean(rs.getString("married"));
                    affiancedDate = Calendar.getInstance();
                    affiancedDate.setTimeInMillis(rs.getLong("affianceDate"));

                    weddingDate = Calendar.getInstance();
                    weddingDate.setTimeInMillis(rs.getLong("weddingDate"));
                }
            }
        } catch (Exception e) {
            log.error("Exception: Couple.load(): {}", e.getMessage(), e);
        }
    }

    public Couple(Player player1, Player player2) {
        final long currentTime = System.currentTimeMillis();
        player1Id = player1.getObjectId();
        player2Id = player2.getObjectId();

        affiancedDate = Calendar.getInstance();
        affiancedDate.setTimeInMillis(currentTime);

        weddingDate = Calendar.getInstance();
        weddingDate.setTimeInMillis(currentTime);

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO mods_wedding (id, player1Id, player2Id, married, affianceDate, weddingDate) VALUES (?, ?, ?, ?, ?, ?)")) {
            id = IdManager.getInstance().getNextId();
            ps.setInt(1, id);
            ps.setInt(2, player1Id);
            ps.setInt(3, player2Id);
            ps.setBoolean(4, false);
            ps.setLong(5, affiancedDate.getTimeInMillis());
            ps.setLong(6, weddingDate.getTimeInMillis());
            ps.execute();
        } catch (Exception e) {
            log.error("Could not create couple: {}", e.getMessage(), e);
        }
    }

    public void marry() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE mods_wedding set married = ?, weddingDate = ? where id = ?")) {
            ps.setBoolean(1, true);
            weddingDate = Calendar.getInstance();
            ps.setLong(2, weddingDate.getTimeInMillis());
            ps.setInt(3, id);
            ps.execute();
            maried = true;
        } catch (Exception e) {
            log.error("Could not marry: {}", e.getMessage(), e);
        }
    }

    public void divorce() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM mods_wedding WHERE id=?")) {
            ps.setInt(1, id);
            ps.execute();
        } catch (Exception e) {
            log.error("Exception: Couple.divorce(): {}", e.getMessage(), e);
        }
    }

    public boolean getMaried() {
        return maried;
    }
}
