/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.CrestTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.entity.clan.Crest;
import app.l2nx.highfive.entity.clan.enums.CrestType;
import app.l2nx.highfive.network.SystemMessageId;

/**
 * Client packet for setting/deleting clan crest.
 */
public class RequestSetPledgeCrest extends ClientPacket {
    private int length;
    private byte[] data = null;

    @Override
    protected void readImpl() {
        length = readInt();
        if (length > 256) {
            return;
        }

        data = readBytes(length);
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (length < 0) {
            player.sendPacket(
                    SystemMessageId.THE_SIZE_OF_THE_UPLOADED_CREST_OR_INSIGNIA_DOES_NOT_MEET_THE_STANDARD_REQUIREMENTS);
            return;
        }

        if (length > 256) {
            player.sendPacket(
                    SystemMessageId.THE_SIZE_OF_THE_IMAGE_FILE_IS_INAPPROPRIATE_PLEASE_ADJUST_TO_16X12_PIXELS);
            return;
        }

        final Clan clan = player.getClan();
        if (clan == null) {
            return;
        }

        if (clan.getDissolvingExpiryTime() > ClockBridge.wallMillis()) {
            player.sendPacket(
                    SystemMessageId
                            .AS_YOU_ARE_CURRENTLY_SCHEDULE_FOR_CLAN_DISSOLUTION_YOU_CANNOT_REGISTER_OR_DELETE_A_CLAN_CREST);
            return;
        }

        if (!player.hasAccess(ClanAccess.CHANGE_CREST)) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        if (length == 0) {
            if (clan.getCrestId() != 0) {
                clan.changeClanCrest(0);
                player.sendPacket(SystemMessageId.THE_CLAN_S_CREST_HAS_BEEN_DELETED);
            }
        } else {
            if (clan.getLevel() < 3) {
                player.sendPacket(
                        SystemMessageId.A_CLAN_CREST_CAN_ONLY_BE_REGISTERED_WHEN_THE_CLAN_S_SKILL_LEVEL_IS_3_OR_ABOVE);
                return;
            }

            final Crest crest = CrestTable.getInstance().createCrest(data, CrestType.PLEDGE);
            if (crest != null) {
                clan.changeClanCrest(crest.getId());
                player.sendPacket(SystemMessageId.THE_CREST_WAS_SUCCESSFULLY_REGISTERED);
            }
        }
    }
}
