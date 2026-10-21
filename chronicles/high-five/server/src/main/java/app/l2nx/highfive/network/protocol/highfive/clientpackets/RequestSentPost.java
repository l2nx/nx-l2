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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExReplySentPost;

/**
 * @author Migi, DS
 */
public class RequestSentPost extends ClientPacket {
    private int msgId;

    @Override
    protected void readImpl() {
        msgId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || !GeneralConfig.ALLOW_MAIL) {
            return;
        }

        final MailMessage msg = MailManager.getInstance().getMessage(msgId);
        if (msg == null) {
            return;
        }

        if (!player.isInsideZone(ZoneId.PEACE) && msg.hasAttachments()) {
            player.sendPacket(
                    SystemMessageId.YOU_CANNOT_RECEIVE_OR_SEND_MAIL_WITH_ATTACHED_ITEMS_IN_NON_PEACE_ZONE_REGIONS);
            return;
        }

        if (msg.getSenderId() != player.getObjectId()) {
            PunishmentManager.handleIllegalPlayerAction(
                    player, player + " tried to read not own post!", GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        if (msg.isDeletedBySender()) {
            return;
        }

        player.sendPacket(new ExReplySentPost(msg));
    }
}
