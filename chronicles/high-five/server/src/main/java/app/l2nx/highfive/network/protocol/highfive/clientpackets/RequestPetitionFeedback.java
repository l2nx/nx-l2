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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.PetitionFeedbackManager;
import app.l2nx.highfive.repository.world.model.PetitionFeedback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author Plim
 */
@Slf4j
public class RequestPetitionFeedback extends ClientPacket {
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
        if ((player == null) || (player.getLastPetitionGmId() == 0)) {
            return;
        }

        if ((rate > 4) || (rate < 0)) {
            return;
        }

        try {
            PetitionFeedbackManager.getInstance()
                    .save(PetitionFeedback.builder()
                            .objectId(player.getObjectId())
                            .gmObjectId(player.getLastPetitionGmId())
                            .rating(rate)
                            .message(message)
                            .createdAt(instantOrNull(ClockBridge.wallMillis()))
                            .build());
        } catch (DataAccessException e) {
            log.warn("{}: Error while saving petition feedback: {}", getClass().getSimpleName(), e.getMessage());
        }
    }
}
