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
package app.l2nx.highfive.mechanics.petition;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.PetitionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PetitionVotePacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Petition
 * @author xban1x
 */
public class Petition {
    @Getter
    private final long submitTime = ClockBridge.wallMillis();

    @Getter
    private final int id;

    private final PetitionType type;

    @Getter
    @Setter
    private PetitionState state = PetitionState.PENDING;

    @Getter
    private final String content;

    @Getter
    private final Collection<CreatureSay> logMessages = ConcurrentHashMap.newKeySet();

    @Getter
    private final Player petitioner;

    @Getter
    private Player responder;

    public Petition(Player petitioner, String petitionText, int petitionType) {
        id = IdManager.getInstance().getNextId();
        type = PetitionType.values()[petitionType - 1];
        content = petitionText;
        this.petitioner = petitioner;
    }

    public boolean addLogMessage(CreatureSay cs) {
        return logMessages.add(cs);
    }

    public boolean endPetitionConsultation(PetitionState endState) {
        setState(endState);

        if ((responder != null) && responder.isOnline()) {
            if (endState == PetitionState.RESPONDER_REJECT) {
                petitioner.sendMessage("Your petition was rejected. Please try again later.");
            } else {
                // Ending petition consultation with <Player>.
                SystemMessage sm = new SystemMessage(SystemMessageId.ENDING_PETITION_CONSULTATION_WITH_C1);
                sm.addString(petitioner.getName());
                responder.sendPacket(sm);

                if (endState == PetitionState.PETITIONER_CANCEL) {
                    // Receipt No. <ID> petition cancelled.
                    sm = new SystemMessage(SystemMessageId.RECEIPT_NO_S1_PETITION_CANCELLED);
                    sm.addInt(id);
                    responder.sendPacket(sm);
                }
            }
        }

        // End petition consultation and inform them, if they are still online. And if petitioner is online, enable
        // Evaluation button.
        if ((petitioner != null) && petitioner.isOnline()) {
            petitioner.sendPacket(
                    SystemMessageId
                            .THIS_ENDS_THE_GM_PETITION_CONSULTATION_NPLEASE_GIVE_US_FEEDBACK_ON_THE_PETITION_SERVICE);
            petitioner.sendPacket(PetitionVotePacket.STATIC_PACKET);
        }

        PetitionManager.getInstance().getCompletedPetitions().put(getId(), this);
        return PetitionManager.getInstance().getPendingPetitions().remove(getId()) != null;
    }

    public String getTypeAsString() {
        return type.toString().replace('_', ' ');
    }

    public void sendPetitionerPacket(ServerPacket responsePacket) {
        if ((petitioner == null) || !petitioner.isOnline()) {
            // Allows petitioners to see the results of their petition when
            // they log back into the game.

            // endPetitionConsultation(PetitionState.Petitioner_Missing);
            return;
        }

        petitioner.sendPacket(responsePacket);
    }

    public void sendResponderPacket(ServerPacket responsePacket) {
        if ((responder == null) || !responder.isOnline()) {
            endPetitionConsultation(PetitionState.RESPONDER_MISSING);
            return;
        }

        responder.sendPacket(responsePacket);
    }

    public void setResponder(Player respondingAdmin) {
        if (responder != null) {
            return;
        }

        responder = respondingAdmin;
    }
}
