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
package app.l2nx.highfive.scripts.handlers.chat.channels;

import app.l2nx.highfive.chat.ChatService;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IChatHandler;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;

/**
 * Alliance Chat Handler.
 */
public class ChatAlliance implements IChatHandler {
    private static final ChatType[] CHAT_TYPES = {
        ChatType.ALLIANCE,
    };

    @Override
    public void onChat(ChatType type, Player activeChar, String target, String text) {
        if ((activeChar.getClan() == null)
                || ((activeChar.getClan() != null) && (activeChar.getClan().getAllyId() == 0))) {
            activeChar.sendMessage("You are not in an alliance.");
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

        activeChar.getClan().broadcastChatToOnlineAllyMembers(ChatService.message(activeChar, type, text), activeChar);
    }

    @Override
    public ChatType[] getChatTypeList() {
        return CHAT_TYPES;
    }
}
