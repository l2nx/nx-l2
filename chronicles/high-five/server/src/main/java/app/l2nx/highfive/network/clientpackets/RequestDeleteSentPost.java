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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.network.serverpackets.ExChangePostState;

/**
 * @author Migi, DS
 */
public class RequestDeleteSentPost extends ClientPacket {
    private static final int BATCH_LENGTH = 4; // length of the one item

    int[] msgIds = null;

    @Override
    protected void readImpl() {
        final int count = readInt();
        if ((count <= 0) || (count > PlayerConfig.MAX_ITEM_IN_PACKET) || ((count * BATCH_LENGTH) != remaining())) {
            return;
        }

        msgIds = new int[count];
        for (int i = 0; i < count; i++) {
            msgIds[i] = readInt();
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || (msgIds == null) || !GeneralConfig.ALLOW_MAIL) {
            return;
        }

        if (!player.isInsideZone(ZoneId.PEACE)) {
            player.sendPacket(
                    SystemMessageId.YOU_CANNOT_RECEIVE_OR_SEND_MAIL_WITH_ATTACHED_ITEMS_IN_NON_PEACE_ZONE_REGIONS);
            return;
        }

        for (int msgId : msgIds) {
            final MailMessage msg = MailManager.getInstance().getMessage(msgId);
            if (msg == null) {
                continue;
            }

            if (msg.getSenderId() != player.getObjectId()) {
                PunishmentManager.handleIllegalPlayerAction(
                        player, player + " tried to delete not own post!", GeneralConfig.DEFAULT_PUNISH);
                return;
            }

            if (msg.hasAttachments() || msg.isDeletedBySender()) {
                return;
            }

            msg.setDeletedBySender();
        }

        player.sendPacket(new ExChangePostState(false, msgIds, MailMessage.DELETED));
    }
}
