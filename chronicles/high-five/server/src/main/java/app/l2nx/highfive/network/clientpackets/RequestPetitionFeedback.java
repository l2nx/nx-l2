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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Player;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Plim
 */
@Slf4j
public class RequestPetitionFeedback extends ClientPacket {
    private static final String INSERT_FEEDBACK = "INSERT INTO petition_feedback VALUES (?,?,?,?,?)";

    // cdds
    // private int _unknown;
    private int rate; // 4=VeryGood, 3=Good, 2=Fair, 1=Poor, 0=VeryPoor
    private String message;

    @Override
    protected void readImpl() {
        readInt(); // unknown
        rate = readInt();
        message = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || (player.getLastPetitionGmName() == null)) {
            return;
        }

        if ((rate > 4) || (rate < 0)) {
            return;
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(INSERT_FEEDBACK)) {
            statement.setString(1, player.getName());
            statement.setString(2, player.getLastPetitionGmName());
            statement.setInt(3, rate);
            statement.setString(4, message);
            statement.setLong(5, System.currentTimeMillis());
            statement.execute();
        } catch (SQLException e) {
            log.warn("{}: Error while saving petition feedback: {}", getClass().getSimpleName(), e.getMessage());
        }
    }
}
