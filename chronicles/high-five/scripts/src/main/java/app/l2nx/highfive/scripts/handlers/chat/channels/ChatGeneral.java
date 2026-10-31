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
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.BlockList;
import app.l2nx.highfive.handler.IChatHandler;
import app.l2nx.highfive.handler.IVoicedCommandHandler;
import app.l2nx.highfive.handler.VoicedCommandHandler;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * General Chat Handler.
 * @author durgus
 */
public class ChatGeneral implements IChatHandler {
    private static final ChatType[] CHAT_TYPES = {
        ChatType.GENERAL,
    };

    @Override
    public void onChat(ChatType type, Player activeChar, String paramsValue, String text) {
        boolean vcdUsed = false;
        if (text.startsWith(".")) {
            final StringTokenizer st = new StringTokenizer(text);
            final IVoicedCommandHandler vch;
            String command = "";
            String params = paramsValue;
            if (st.countTokens() > 1) {
                command = st.nextToken().substring(1);
                params = text.substring(command.length() + 2);
            } else {
                command = text.substring(1);
            }

            vch = VoicedCommandHandler.getInstance().getHandler(command);
            if (vch != null) {
                vch.onCommand(command, activeChar, params);
                vcdUsed = true;
            } else {
                vcdUsed = false;
            }
        }

        if (!vcdUsed) {
            if (activeChar.isChatBanned() && GeneralConfig.BAN_CHAT_CHANNELS.contains(type)) {
                activeChar.getPort().emit(MessageShown.of(MessageKey.CHAT_PROHIBITED_EXTENDED));
                return;
            }

            if ((activeChar.getLevel() < GeneralConfig.MINIMUM_CHAT_LEVEL) && !activeChar.isGM()) {
                activeChar.sendMessage(
                        "Players can use general chat after Lv. " + GeneralConfig.MINIMUM_CHAT_LEVEL + ".");
                return;
            }

            final ChatMessage message =
                    ChatService.message(activeChar, activeChar.getAppearance().getVisibleName(), type, text);
            final ChatMessage randomized =
                    message.toBuilder().text(ChatRandomizer.randomize(text)).build();
            final List<Player> plain = new ArrayList<>();
            final List<Player> opposing = new ArrayList<>();
            World.forEachVisibleObjectInRange(activeChar, Player.class, 1250, player -> {
                if ((player != null) && !BlockList.isBlocked(player, activeChar)) {
                    (ChatRules.opposingFactions(activeChar, player) ? opposing : plain).add(player);
                }
            });
            plain.add(activeChar);
            final ChatService chat = ChatService.getInstance();
            chat.deliverAll(plain, message);
            chat.deliverAll(opposing, randomized);
        }
    }

    @Override
    public ChatType[] getChatTypeList() {
        return CHAT_TYPES;
    }
}
