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
package app.l2nx.highfive.entity.actor.holders.player;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CoupleManager;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.world.model.CoupleRow;
import java.util.Calendar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author evill33t
 */
@Slf4j
@SingleWriter
public class Couple {
    private final long id;
    private final int player1Id;
    private final int player2Id;
    private boolean maried;
    private final Calendar affiancedDate;

    /** Holds the engagement date until the couple marries. */
    private Calendar weddingDate;

    public Couple(CoupleRow row) {
        id = row.id();
        player1Id = row.firstObjectId();
        player2Id = row.secondObjectId();
        maried = row.marriedAt() != null;
        affiancedDate = ClockBridge.calendar();
        affiancedDate.setTimeInMillis(epochMillisOrZero(row.engagedAt()));
        weddingDate = ClockBridge.calendar();
        weddingDate.setTimeInMillis(epochMillisOrZero(maried ? row.marriedAt() : row.engagedAt()));
    }

    public Couple(Player player1, Player player2) {
        final long currentTime = ClockBridge.wallMillis();
        player1Id = player1.getObjectId();
        player2Id = player2.getObjectId();

        affiancedDate = ClockBridge.calendar();
        affiancedDate.setTimeInMillis(currentTime);

        weddingDate = ClockBridge.calendar();
        weddingDate.setTimeInMillis(currentTime);

        id = CoupleManager.getInstance().insert(player1Id, player2Id, currentTime);
    }

    public long getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public int getPlayer1Id() {
        WorldPartition.checkOwner();
        return player1Id;
    }

    public int getPlayer2Id() {
        WorldPartition.checkOwner();
        return player2Id;
    }

    /** @return a copy, the couple's own calendar stays unchanged */
    public Calendar getAffiancedDate() {
        WorldPartition.checkOwner();
        return (Calendar) affiancedDate.clone();
    }

    /** @return a copy, the couple's own calendar stays unchanged */
    public Calendar getWeddingDate() {
        WorldPartition.checkOwner();
        return (Calendar) weddingDate.clone();
    }

    public void marry() {
        WorldPartition.checkOwner();
        final Calendar marriedAt = ClockBridge.calendar();
        try {
            CoupleManager.getInstance().updateMarried(id, marriedAt.getTimeInMillis());
            weddingDate = marriedAt;
            maried = true;
        } catch (DataAccessException e) {
            log.error("Could not marry: {}", e.getMessage(), e);
        }
    }

    public void divorce() {
        WorldPartition.checkOwner();
        try {
            CoupleManager.getInstance().delete(id);
        } catch (DataAccessException e) {
            log.error("Exception: Couple.divorce(): {}", e.getMessage(), e);
        }
    }

    public boolean getMaried() {
        WorldPartition.checkOwner();
        return maried;
    }
}
