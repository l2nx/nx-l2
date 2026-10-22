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
package app.l2nx.highfive.network.holders;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.clientpackets.ClientPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * This class manages requests (transactions) between two Player.
 * @author kriau
 */
public class RequestPartner {
    private static final int REQUEST_TIMEOUT = 15; // in secs

    protected Player player;

    @Getter
    protected Player partner;

    protected boolean requestor;
    protected boolean answerer;

    @Getter
    protected ClientPacket requestPacket;

    public RequestPartner(Player player) {
        this.player = player;
    }

    protected void clear() {
        partner = null;
        requestPacket = null;
        requestor = false;
        answerer = false;
    }

    /**
     * Set the Player member of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).
     * @param partner
     */
    private synchronized void setPartner(Player partner) {
        this.partner = partner;
    }

    /**
     * Set the packet that came from requester.
     * @param packet
     */
    private synchronized void setRequestPacket(ClientPacket packet) {
        requestPacket = packet;
    }

    /**
     * Checks if request can be made and in success case puts both PC on request state.
     * @param partner
     * @param packet
     * @return
     */
    public synchronized boolean setRequest(Player partner, ClientPacket packet) {
        if (partner == null) {
            player.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
            return false;
        }

        if (partner.getRequest().isProcessingRequest()) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_IS_ON_ANOTHER_TASK_PLEASE_TRY_AGAIN_LATER);
            sm.addString(partner.getName());
            player.sendPacket(sm);
            return false;
        }

        if (isProcessingRequest()) {
            player.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
            return false;
        }

        this.partner = partner;
        requestPacket = packet;
        setOnRequestTimer(true);
        this.partner.getRequest().setPartner(player);
        this.partner.getRequest().setRequestPacket(packet);
        this.partner.getRequest().setOnRequestTimer(false);
        return true;
    }

    private void setOnRequestTimer(boolean isRequestor) {
        requestor = isRequestor;
        answerer = !isRequestor;
        WorldPartition.loop().schedule(this::clear, REQUEST_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Clears PC request state. Should be called after answer packet receive.
     */
    public void onRequestResponse() {
        if (partner != null) {
            partner.getRequest().clear();
        }

        clear();
    }

    /**
     * @return {@code true} if a transaction is in progress.
     */
    public boolean isProcessingRequest() {
        return partner != null;
    }
}
