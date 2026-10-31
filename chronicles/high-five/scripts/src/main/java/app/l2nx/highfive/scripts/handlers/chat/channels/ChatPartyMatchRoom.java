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
package app.l2nx.highfive.scripts.handlers.chat.channels;

import app.l2nx.highfive.chat.ChatRules;
import app.l2nx.highfive.chat.ChatService;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoom;
import app.l2nx.highfive.entity.groups.matching.PartyMatchRoomList;
import app.l2nx.highfive.handler.IChatHandler;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import java.util.ArrayList;
import java.util.List;

/**
 * Party Match Room chat handler.
 * @author Gnacik
 */
public class ChatPartyMatchRoom implements IChatHandler {
    private static final ChatType[] CHAT_TYPES = {
        ChatType.PARTYMATCH_ROOM,
    };

    @Override
    public void onChat(ChatType type, Player activeChar, String target, String text) {
        if (!activeChar.isInPartyMatchRoom()) {
            return;
        }

        final PartyMatchRoom room = PartyMatchRoomList.getInstance().getPlayerRoom(activeChar);
        if (room == null) {
            return;
        }

        if (activeChar.isChatBanned() && GeneralConfig.BAN_CHAT_CHANNELS.contains(type)) {
            activeChar.getPort().emit(MessageShown.of(MessageKey.CHAT_PROHIBITED_EXTENDED));
            return;
        }

        if (GeneralConfig.JAIL_DISABLE_CHAT && activeChar.isJailed() && !activeChar.isGM()) {
            activeChar.getPort().emit(MessageShown.of(MessageKey.CHAT_PROHIBITED));
            return;
        }

        final ChatMessage message = ChatService.message(activeChar, type, text);
        final List<Player> receivers = new ArrayList<>();
        for (Player member : room.getPartyMembers()) {
            if (ChatRules.sameFactionOrNone(activeChar, member)) {
                receivers.add(member);
            }
        }
        ChatService.getInstance().deliverAll(receivers, message);
    }

    @Override
    public ChatType[] getChatTypeList() {
        return CHAT_TYPES;
    }
}
